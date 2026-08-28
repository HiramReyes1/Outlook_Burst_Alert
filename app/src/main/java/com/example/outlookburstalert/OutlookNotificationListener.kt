package com.example.outlookburstalert

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Servicio especial del sistema: una vez que el usuario da el permiso de
 * "acceso a notificaciones" en Ajustes, Android llama a
 * onNotificationPosted() cada vez que llega CUALQUIER notificacion al
 * telefono, de cualquier app.
 *
 * Aqui filtramos dos veces:
 * 1. Que la notificacion venga del paquete de Outlook.
 * 2. Que NO sea una notificacion de "resumen de grupo" (Outlook publica
 *    un resumen interno ademas de cada correo individual, aunque solo se
 *    vea uno en pantalla — sin este filtro se contarian correos de mas).
 */
class OutlookNotificationListener : NotificationListenerService() {

    private lateinit var statsRepository: StatsRepository
    private lateinit var settingsRepository: SettingsRepository
    private var burstDetector: BurstDetector? = null
    private var cachedThreshold = -1
    private var cachedWindowMillis = -1L
    private var wasMonitoringActive = false

    override fun onCreate() {
        super.onCreate()
        statsRepository = StatsRepository(applicationContext)
        settingsRepository = SettingsRepository(applicationContext)
        Log.d(TAG, "Listener creado")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        if (!settingsRepository.isMonitoringActive()) {
            wasMonitoringActive = false
            return
        }

        if (!wasMonitoringActive) {
            Log.d(TAG, "Monitoreo reactivado, arrancando detector desde cero")
            burstDetector = null
        }
        wasMonitoringActive = true

        if (sbn.packageName != OUTLOOK_PACKAGE_NAME) {
            return
        }

        val isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (isGroupSummary) {
            Log.d(TAG, "Resumen de grupo ignorado (key=${sbn.key})")
            return
        }

        Log.d(TAG, "Correo de Outlook detectado (key=${sbn.key})")
        logNotificationExtrasForDebugging(sbn)
        statsRepository.incrementNotificationCount()

        val senderTitle = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

        if (isTrustedSender(senderTitle)) {
            val detector = getOrCreateBurstDetector()
            val alertTriggered = detector.registerEvent()
            if (alertTriggered) {
                Log.i(TAG, "🔔 Umbral de rafaga superado (remitente confiable: $senderTitle), disparando alarma")
                AlertTrigger.fire(applicationContext)
            } else {
                Log.d(TAG, "Correos vigentes en la ventana: ${detector.currentCount()}")
            }
        } else {
            Log.i(TAG, "⚠️ Remitente NO confiable ($senderTitle), disparando alarma de inmediato")
            AlertTrigger.fire(applicationContext)
        }
    }

    /**
     * TEMPORAL - Fase A de deteccion de remitente. Imprime todos los campos
     * relevantes del extras de la notificacion para ver que trae Outlook
     * realmente (nombre, correo completo, o ambos) segun el remitente y el
     * tipo de notificacion. Se quita una vez que sepamos que campo usar.
     */
    private fun logNotificationExtrasForDebugging(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras

        Log.i(TAG, "===== DEBUG EXTRAS (key=${sbn.key}) =====")
        Log.i(TAG, "EXTRA_TITLE: ${extras.getCharSequence(Notification.EXTRA_TITLE)}")
        Log.i(TAG, "EXTRA_TITLE_BIG: ${extras.getCharSequence(Notification.EXTRA_TITLE_BIG)}")
        Log.i(TAG, "EXTRA_TEXT: ${extras.getCharSequence(Notification.EXTRA_TEXT)}")
        Log.i(TAG, "EXTRA_SUB_TEXT: ${extras.getCharSequence(Notification.EXTRA_SUB_TEXT)}")
        Log.i(TAG, "EXTRA_SUMMARY_TEXT: ${extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)}")
        Log.i(TAG, "EXTRA_BIG_TEXT: ${extras.getCharSequence(Notification.EXTRA_BIG_TEXT)}")
        Log.i(TAG, "EXTRA_INFO_TEXT: ${extras.getCharSequence(Notification.EXTRA_INFO_TEXT)}")

        // EXTRA_PEOPLE_LIST suele traer el remitente en notificaciones tipo
        // "mensaje" (MessagingStyle) -- Outlook a veces lo usa, a veces no.
        val people = extras.getParcelableArrayList<android.app.Person>(Notification.EXTRA_PEOPLE_LIST)
        Log.i(TAG, "EXTRA_PEOPLE_LIST: ${people?.map { "${it.name} <${it.uri ?: "sin uri"}>" }}")

        // Imprime TODAS las claves del bundle, por si hay algo especifico de
        // Outlook que no está en las constantes estandar de Notification.
        Log.i(TAG, "Todas las claves del bundle: ${extras.keySet().joinToString()}")
        Log.i(TAG, "===== FIN DEBUG EXTRAS =====")
    }

    /**
     * Devuelve el BurstDetector actual, o construye uno nuevo si es la
     * primera vez o si el usuario cambio el umbral/ventana desde la UI.
     */
    private fun getOrCreateBurstDetector(): BurstDetector {
        val threshold = settingsRepository.getThreshold()
        val windowMillis = settingsRepository.getWindowSeconds() * 1000L

        val existing = burstDetector
        if (existing != null && threshold == cachedThreshold && windowMillis == cachedWindowMillis) {
            return existing
        }

        Log.d(TAG, "Config cambio (umbral=$threshold, ventana=${windowMillis}ms), reconstruyendo detector")
        cachedThreshold = threshold
        cachedWindowMillis = windowMillis
        return BurstDetector(threshold, windowMillis).also { burstDetector = it }
    }

    /**
     * Decide si el remitente (tomado de EXTRA_TITLE) es una cuenta de
     * confianza que debe respetar el umbral de rafaga configurado. Cualquier
     * remitente que no entre aqui dispara la alarma de inmediato con un solo
     * correo.
     */
    private fun isTrustedSender(senderTitle: String?): Boolean {
        val title = senderTitle?.trim() ?: return false
        return title.startsWith(CATALYST_TITLE_PREFIX, ignoreCase = true) ||
                title.equals(ZABBIX_TITLE, ignoreCase = true)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener conectado al sistema (permiso activo)")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener desconectado del sistema")
    }

    companion object {
        private const val TAG = "OutlookNotifListener"
        private const val OUTLOOK_PACKAGE_NAME = "com.microsoft.office.outlook"
        private const val CATALYST_TITLE_PREFIX = "PUIG_CATALYST_CENTER"
        private const val ZABBIX_TITLE = "zabbix@puig.es"
    }
}