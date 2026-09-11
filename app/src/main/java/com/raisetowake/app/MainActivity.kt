package com.raisetowake.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.raisetowake.app.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toggleButton = findViewById<Button>(R.id.btnToggleService)
        val seekBar = findViewById<SeekBar>(R.id.seekBarSensitivity)
        val txtSensitivity = findViewById<TextView>(R.id.txtSensitivity)

        val currentSensitivity = Prefs.getSensitivity(this)
        seekBar.progress = (currentSensitivity * 10).toInt()
        txtSensitivity.text = "Sensitivity: $currentSensitivity"

        updateButtonState(toggleButton)

        toggleButton.setOnClickListener {
            val isEnabled = Prefs.isServiceEnabled(this)
            Prefs.setServiceEnabled(this, !isEnabled)

            val intent = Intent(this, WakeService::class.java)
            if (!isEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } else {
                stopService(intent)
            }

            updateButtonState(toggleButton)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val valFloat = progress / 10f
                txtSensitivity.text = "Sensitivity: $valFloat"
                Prefs.setSensitivity(this@MainActivity, valFloat)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun updateButtonState(button: Button) {
        if (Prefs.isServiceEnabled(this)) {
            button.text = "Disable Service"
        } else {
            button.text = "Enable Service"
        }
    }
}
