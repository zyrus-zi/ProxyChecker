package com.example.proxychecker

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class ProxyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // ПРИНУДИТЕЛЬНОЕ ПРИМЕНЕНИЕ ТЕМЫ ПРИ ЗАПУСКЕ ПРИЛОЖЕНИЯ
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        // false по умолчанию = Светлая тема (как вы просили)
        val isDarkMode = prefs.getBoolean("isDarkTheme", false)

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}