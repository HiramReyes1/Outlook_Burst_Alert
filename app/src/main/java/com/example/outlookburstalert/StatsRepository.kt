package com.example.outlookburstalert

import android.content.Context

/**
 * Guarda los contadores de "correos detectados" y "veces que sono la
 * alarma" en SharedPreferences, para que sobrevivan aunque se cierre la
 * app o se reinicie el telefono.
 *
 * - OutlookNotificationListener llama a incrementNotificationCount() cada
 *   vez que detecta un correo nuevo de Outlook.
 * - En la Fase 3, AlertTrigger llamara a incrementAlarmCount() cada vez
 *   que se dispare la alarma.
 * - MainActivity lee ambos contadores cada vez que el usuario abre la app
 *   (en onResume), para que se vean actualizados aunque hayan llegado
 *   correos mientras la app estaba cerrada.
 *
 * Los contadores son acumulados: no se reinician solos. El usuario decide
 * cuando reiniciarlos con el boton "Reiniciar contadores" en la UI.
 */
class StatsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun incrementNotificationCount() {
        val current = prefs.getInt(KEY_NOTIFICATION_COUNT, 0)
        prefs.edit().putInt(KEY_NOTIFICATION_COUNT, current + 1).apply()
    }

    fun incrementAlarmCount() {
        val current = prefs.getInt(KEY_ALARM_COUNT, 0)
        prefs.edit().putInt(KEY_ALARM_COUNT, current + 1).apply()
    }

    fun getNotificationCount(): Int = prefs.getInt(KEY_NOTIFICATION_COUNT, 0)

    fun getAlarmCount(): Int = prefs.getInt(KEY_ALARM_COUNT, 0)

    fun resetCounters() {
        prefs.edit()
            .putInt(KEY_NOTIFICATION_COUNT, 0)
            .putInt(KEY_ALARM_COUNT, 0)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "outlook_burst_alert_stats"
        private const val KEY_NOTIFICATION_COUNT = "notification_count"
        private const val KEY_ALARM_COUNT = "alarm_count"
    }
}