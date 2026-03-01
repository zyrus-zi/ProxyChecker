package com.example.proxychecker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("isDarkTheme", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)

        if (!prefs.getBoolean("isFirstRun", true)) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_welcome)

        // СКРЫТИЕ СТАТУС БАРА
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())

        // НАСТРОЙКА ФОНА (Исправлено)
        val starField = findViewById<StarFieldView>(R.id.starField)
        val isStarsEnabled = prefs.getBoolean("isStarsEnabled", true)
        starField.visibility = if (isStarsEnabled) View.VISIBLE else View.GONE
        if (isStarsEnabled) starField.updateColors()

        val btnNotify = findViewById<Button>(R.id.btnNotifyPerm)
        val btnBattery = findViewById<Button>(R.id.btnBatteryPerm)
        val btnContinue = findViewById<Button>(R.id.btnContinue)

        btnNotify.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Разрешение уже предоставлено", Toast.LENGTH_SHORT).show()
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                }
            } else {
                Toast.makeText(this, "На вашей версии Android это разрешено по умолчанию", Toast.LENGTH_SHORT).show()
            }
        }

        btnBattery.setOnClickListener {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Разрешение уже предоставлено", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Откройте настройки вручную", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnContinue.setOnClickListener {
            checkPermissionsAndProceed()
        }
    }

    private fun checkPermissionsAndProceed() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        val notifyOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!batteryOk || !notifyOk) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Разрешения не выданы")
                .setMessage("Без разрешений на уведомления и работу в фоне приложение может закрываться системой. Продолжить?")
                .setPositiveButton("ОК") { _, _ -> completeSetup() }
                .setNegativeButton("Отмена", null)
                .show()
        } else {
            completeSetup()
        }
    }

    private fun completeSetup() {
        getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().putBoolean("isFirstRun", false).apply()
        goToMain()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}