package com.example.outlookburstalert

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.outlookburstalert.databinding.ActivityAlarmBinding

/**
 * Pantalla que se muestra al dispararse la alarma. Se abre incluso con
 * el telefono bloqueado, similar a una llamada entrante o una alarma de
 * reloj.
 *
 * El unico boton es "Detener alarma", que llama a AlertTrigger.stop() y
 * cierra la pantalla. El boton "Atras" del sistema hace lo mismo, para
 * que el usuario nunca se quede con la alarma sonando sin forma de
 * pararla.
 */
class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockScreenFlags()

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonStopAlarm.setOnClickListener {
            stopAlarmAndClose()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        stopAlarmAndClose()
    }

    private fun stopAlarmAndClose() {
        AlertTrigger.stop(applicationContext)
        finish()
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}