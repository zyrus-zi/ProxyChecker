package com.example.proxychecker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Проверяем, заходил ли уже пользователь
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("isFirstRun", true)

        if (!isFirstRun) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_welcome)

        val btnNotify = findViewById<Button>(R.id.btnNotifyPerm)
        val btnBattery = findViewById<Button>(R.id.btnBatteryPerm)
        val btnContinue = findViewById<Button>(R.id.btnContinue)

        btnNotify.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                } else {
                    Toast.makeText(this, "Уже разрешено", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "На вашей версии Android это разрешено по умолчанию", Toast.LENGTH_SHORT).show()
            }
        }

        btnBattery.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Не удалось открыть настройки батареи", Toast.LENGTH_SHORT).show()
            }
        }

        btnContinue.setOnClickListener {
            prefs.edit().putBoolean("isFirstRun", false).apply()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}