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
        // Тема здесь применится, но основную работу делает ProxyApplication
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("isDarkTheme", false) // По умолчанию false (Светлая)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)

        val isFirstRun = prefs.getBoolean("isFirstRun", true)
        if (!isFirstRun) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_welcome)

        // Скрытие статус бара
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())

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
            if (isBatteryOptimizationIgnored()) {
                Toast.makeText(this, "Разрешение уже предоставлено", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Не удалось открыть настройки батареи", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnContinue.setOnClickListener {
            checkPermissionsAndProceed()
        }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun checkPermissionsAndProceed() {
        var missingPermissions = false

        // Проверка уведомлений (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions = true
            }
        }

        // Проверка батареи
        if (!isBatteryOptimizationIgnored()) {
            missingPermissions = true
        }

        if (missingPermissions) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Не все разрешения выданы")
                .setMessage("Без разрешений на уведомления и работу в фоне приложение может останавливаться при сворачивании. Вы уверены, что хотите продолжить? В будущем вам придется выдать их вручную через настройки.")
                .setPositiveButton("ОК (Продолжить)") { dialog, _ ->
                    dialog.dismiss()
                    completeSetup()
                }
                .setNegativeButton("Отмена", null)
                .show()
        } else {
            completeSetup()
        }
    }

    private fun completeSetup() {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("isFirstRun", false).apply()
        goToMain()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}