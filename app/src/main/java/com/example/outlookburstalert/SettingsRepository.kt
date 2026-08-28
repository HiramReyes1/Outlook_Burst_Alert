package com.example.outlookburstalert

import android.content.Context

/**
 * Guarda y lee la configuracion del monitoreo: si esta activo, el umbral
 * de correos y la ventana de tiempo (en segundos). Usa SharedPreferences,
 * igual que StatsRepository, para no meter una dependencia nueva
 * (DataStore) solo por 3 valores simples.
 *
 * OutlookNotificationListener lee estos valores cada vez que llega una
 * notificacion, para reaccionar a cambios sin tener que reiniciar el
 * servicio. MainActivity los lee/escribe desde la UI.
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isMonitoringActive(): Boolean = prefs.getBoolean(KEY_MONITORING_ACTIVE, false)

    fun setMonitoringActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_MONITORING_ACTIVE, active).apply()
    }

    fun getThreshold(): Int = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD)

    fun setThreshold(value: Int) {
        prefs.edit().putInt(KEY_THRESHOLD, value).apply()
    }

    /** Ventana de tiempo en SEGUNDOS (mas simple de mostrar en la UI que milisegundos). */
    fun getWindowSeconds(): Int = prefs.getInt(KEY_WINDOW_SECONDS, DEFAULT_WINDOW_SECONDS)

    fun setWindowSeconds(value: Int) {
        prefs.edit().putInt(KEY_WINDOW_SECONDS, value).apply()
    }

    companion object {
        private const val PREFS_NAME = "outlook_burst_alert_settings"
        private const val KEY_MONITORING_ACTIVE = "monitoring_active"
        private const val KEY_THRESHOLD = "threshold_count"
        private const val KEY_WINDOW_SECONDS = "window_seconds"

        const val DEFAULT_THRESHOLD = 5
        const val DEFAULT_WINDOW_SECONDS = 120
    }
}