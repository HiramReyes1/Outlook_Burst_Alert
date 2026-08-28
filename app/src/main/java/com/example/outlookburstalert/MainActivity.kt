package com.example.outlookburstalert

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.outlookburstalert.databinding.ActivityMainBinding
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var statsRepository: StatsRepository
    private lateinit var settingsRepository: SettingsRepository
    private var isRefreshingUi = false

    // Lanzador para pedir el permiso POST_NOTIFICATIONS (Android 13+).
    // Es informativo nada mas: si el usuario lo niega, la app sigue
    // funcionando para detectar correos, pero no podra avisar con una
    // notificacion visible mas adelante (Fase 3).
    private val requestPostNotificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        statsRepository = StatsRepository(applicationContext)

        settingsRepository = SettingsRepository(applicationContext)

        binding.buttonGrantAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.buttonResetCounters.setOnClickListener {
            statsRepository.resetCounters()
            refreshUi()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPostNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.buttonTestAlarm.setOnClickListener {
            AlertTrigger.fire(applicationContext)
        }

        binding.switchMonitoring.setOnCheckedChangeListener { _, isChecked ->
            if (isRefreshingUi) return@setOnCheckedChangeListener
            onMonitoringToggled(isChecked)
        }

        binding.buttonSaveSettings.setOnClickListener {
            saveSettingsFromInputs()
        }
    }

    override fun onResume() {
        super.onResume()
        // Se llama cada vez que el usuario vuelve a la app: por ejemplo,
        // justo despues de dar el permiso en Ajustes, o simplemente al
        // reabrir la app despues de que llegaron correos mientras estaba
        // cerrada. Asi los contadores siempre se ven al dia.
        refreshUi()
    }

    private fun onMonitoringToggled(wantsActive: Boolean) {
        if (wantsActive && !NotificationAccessUtils.isNotificationAccessGranted(this)) {
            Toast.makeText(this, R.string.error_no_notification_access, Toast.LENGTH_LONG).show()
            binding.switchMonitoring.isChecked = false
            return
        }

        settingsRepository.setMonitoringActive(wantsActive)

        if (wantsActive) {
            MonitorForegroundService.start(applicationContext)
        } else {
            MonitorForegroundService.stop(applicationContext)
        }
    }

    private fun saveSettingsFromInputs() {
        val threshold = binding.editThreshold.text.toString().toIntOrNull()
        val windowSeconds = binding.editWindowSeconds.text.toString().toIntOrNull()

        if (threshold == null || threshold < 2) {
            Toast.makeText(this, R.string.error_invalid_threshold, Toast.LENGTH_SHORT).show()
            return
        }
        if (windowSeconds == null || windowSeconds < 10) {
            Toast.makeText(this, R.string.error_invalid_window, Toast.LENGTH_SHORT).show()
            return
        }

        settingsRepository.setThreshold(threshold)
        settingsRepository.setWindowSeconds(windowSeconds)
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }

    private fun refreshUi() {
        val hasAccess = NotificationAccessUtils.isNotificationAccessGranted(this)

        binding.textPermissionStatus.text = if (hasAccess) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_not_granted)
        }
        binding.buttonGrantAccess.isEnabled = !hasAccess

        binding.textEmailCount.text = getString(
            R.string.email_count_format,
            statsRepository.getNotificationCount()
        )
        binding.textAlarmCount.text = getString(
            R.string.alarm_count_format,
            statsRepository.getAlarmCount()
        )

        isRefreshingUi = true
        binding.switchMonitoring.isChecked = settingsRepository.isMonitoringActive()
        isRefreshingUi = false

        if (!binding.editThreshold.isFocused) {
            binding.editThreshold.setText(settingsRepository.getThreshold().toString())
        }
        if (!binding.editWindowSeconds.isFocused) {
            binding.editWindowSeconds.setText(settingsRepository.getWindowSeconds().toString())
        }
    }
}