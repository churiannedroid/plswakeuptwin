package com.raisetowake.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.raisetowake.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissionStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupServiceSwitch()
        setupSensitivitySlider()
        setupPermissionButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun setupServiceSwitch() {
        binding.switchService.isChecked = Prefs.isServiceEnabled(this)
        updateSwitchLabel(binding.switchService.isChecked)

        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setServiceEnabled(this, isChecked)
            updateSwitchLabel(isChecked)

            val serviceIntent = Intent(this, WakeService::class.java)
            if (isChecked) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                stopService(serviceIntent)
            }
        }
    }

    private fun updateSwitchLabel(enabled: Boolean) {
        binding.txtServiceStatus.text = if (enabled) {
            getString(R.string.status_active)
        } else {
            getString(R.string.status_inactive)
        }
    }

    private fun setupSensitivitySlider() {
        val current = Prefs.getSensitivity(this)
        binding.seekBarSensitivity.max = 90
        binding.seekBarSensitivity.progress = ((current - Prefs.MIN_SENSITIVITY) * 10).toInt()
        binding.txtSensitivityValue.text = getString(R.string.sensitivity_value, current)

        binding.seekBarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = Prefs.MIN_SENSITIVITY + (progress / 10f)
                binding.txtSensitivityValue.text = getString(R.string.sensitivity_value, value)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}

            override fun onStopTrackingTouch(sb: SeekBar?) {
                val value = Prefs.MIN_SENSITIVITY + (sb!!.progress / 10f)
                Prefs.setSensitivity(this@MainActivity, value)

                if (Prefs.isServiceEnabled(this@MainActivity)) {
                    val serviceIntent = Intent(this@MainActivity, WakeService::class.java)
                    ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                }
            }
        })
    }

    private fun setupPermissionButtons() {
        binding.btnGrantNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.btnGrantBattery.setOnClickListener {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    private fun refreshPermissionStatus() {
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        binding.txtNotificationStatus.text = if (notificationGranted) {
            getString(R.string.status_granted)
        } else {
            getString(R.string.status_not_granted)
        }
        binding.btnGrantNotification.isEnabled = !notificationGranted

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val batteryExempt = powerManager.isIgnoringBatteryOptimizations(packageName)

        binding.txtBatteryStatus.text = if (batteryExempt) {
            getString(R.string.status_granted)
        } else {
            getString(R.string.status_not_granted)
        }
        binding.btnGrantBattery.isEnabled = !batteryExempt
    }
}
