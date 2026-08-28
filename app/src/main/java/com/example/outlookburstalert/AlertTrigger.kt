package com.example.outlookburstalert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Arma y dispara la alarma cuando se detecta una rafaga de correos:
 * sonido en loop por el canal de alarma del telefono, vibracion, y una
 * notificacion de pantalla completa que abre AlarmActivity (incluso con
 * el telefono bloqueado).
 *
 * Es un singleton (object) para poder compartir la misma instancia de
 * MediaPlayer entre quien la dispara (OutlookNotificationListener) y
 * quien la detiene (AlarmActivity).
 */
object AlertTrigger {

    private const val TAG = "AlertTrigger"
    private const val CHANNEL_ID = "alarm_channel"
    private const val NOTIFICATION_ID = 1001

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isAlarmSounding = false

    fun fire(context: Context) {
        if (isAlarmSounding) {
            Log.d(TAG, "Alarma ya sonando, se ignora el nuevo disparo")
            return
        }
        isAlarmSounding = true

        Log.i(TAG, "Disparando alarma")

        StatsRepository(context).incrementAlarmCount()

        ensureChannel(context)
        startSoundAndVibration(context)
        showFullScreenNotification(context)
    }

    /** Detiene el sonido y la vibracion, y quita la notificacion. Se
     * llama desde el boton "Detener alarma" en AlarmActivity. */
    fun stop(context: Context) {
        Log.i(TAG, "Deteniendo alarma")
        isAlarmSounding = false

        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null

        vibrator?.cancel()

        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return

        // Canal SIN sonido ni vibracion propios: eso ya lo maneja
        // MediaPlayer/Vibrator mas abajo (loop indefinido hasta "Detener").
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alarm_channel_description)
            setSound(null, null)
            enableVibration(false)
        }

        notificationManager.createNotificationChannel(channel)
    }

    private fun startSoundAndVibration(context: Context) {
        try {
            val soundUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, soundUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo reproducir el sonido de alarma", e)
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // espera 0ms, vibra 800ms, pausa 400ms -- y se repite desde el
        // indice 0 indefinidamente hasta que se llame a stop()
        val pattern = longArrayOf(0, 800, 400)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun showFullScreenNotification(context: Context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(TAG, "Notificaciones desactivadas por el usuario: la alarma no se puede mostrar")
            return
        }

        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.alarm_notification_title))
            .setContentText(context.getString(R.string.alarm_notification_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSilent(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}