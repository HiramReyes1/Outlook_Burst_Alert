package com.example.outlookburstalert

import android.content.Context
import androidx.core.app.NotificationManagerCompat

object NotificationAccessUtils {

    /**
     * Revisa si el usuario ya activo el permiso especial de "acceso a
     * notificaciones" para esta app. No es un permiso runtime normal, asi
     * que se consulta con esta API de androidx en vez de
     * ActivityCompat.checkSelfPermission().
     */
    fun isNotificationAccessGranted(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }
}