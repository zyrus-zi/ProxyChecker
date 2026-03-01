package com.example.proxychecker

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayoutMediator
import com.example.proxychecker.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("isDarkTheme", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка фона со звездами
        val isStarsEnabled = prefs.getBoolean("isStarsEnabled", true)
        binding.starField.visibility = if (isStarsEnabled) View.VISIBLE else View.GONE
        if (isStarsEnabled) binding.starField.updateColors()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Справка и помощь"
        binding.toolbar.setNavigationOnClickListener { finish() }

        val sections = listOf("🚀 Возможности", "📖 Инструкция", "🔗 Ссылки", "🛡️ Протоколы")

        binding.viewPager.adapter = object : RecyclerView.Adapter<AboutViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AboutViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_about_page, parent, false)
                return AboutViewHolder(view)
            }

            override fun onBindViewHolder(holder: AboutViewHolder, position: Int) {
                holder.tvContent.text = when(position) {
                    0 -> """
                        🌟 Proxy Checker — это инструмент для массовой проверки прокси-серверов.
                        
                        ✅ Основные функции:
                        • Проверка скорости (пинг) и доступности.
                        • Геолокация: определение страны и вывод флага.
                        • Многопоточность: одновременная проверка до 500 прокси.
                        • Умный парсинг: извлечение прокси из любого текста, файлов и Telegram.
                        • Фоновый режим: продолжайте работу, пока приложение проверяет список.
                        • Экспорт: сохранение результатов в удобном формате.
                    """.trimIndent()

                    1 -> """
                        🛠 Пошаговая работа с приложением:
                        
                        Шаг 1: Добавление прокси
                        Вставьте данные в поле ввода. Это может быть один IP:PORT или огромный список. Вы также можете вставить ссылку на канал в Telegram.
                        
                        Шаг 2: Настройка
                        Выберите количество потоков. Для обычных телефонов рекомендуется 50–100. Установите таймаут (время ожидания ответа). Если прокси медленные, ставьте 5000–10000 мс.
                        
                        Шаг 3: Проверка
                        Нажмите 'СТАРТ'. Приложение начнет работу. Вы можете нажать 'Пауза', чтобы временно остановить процесс, или 'Стоп', чтобы полностью отменить. 
                        Если свернуть приложение, в панели уведомлений будет виден прогресс.
                        
                        Шаг 4: Управление результатами
                        Нажмите на заголовок таблицы (Пинг, Тип или Страна), чтобы отсортировать список.
                        
                        Шаг 5: Экспорт
                        Отметьте нужные прокси галочками (или используйте 'Выбрать все живые') и нажмите 'Сохранить TXT' или 'Ссылки ТГ'.
                    """.trimIndent()

                    2 -> """
                        🔗 Поддерживаемые типы ссылок и как их получить:
                        
                        1. Telegram Каналы (Скрапинг за 1 месяц):
                        Формат: https://t.me/name_channel
                        Как получить: Нажмите на название канала -> Ссылка (Share link).
                        
                        2. Ссылка на конкретный пост:
                        Формат: https://t.me/name/1234
                        Как получить: Нажмите правой кнопкой на сообщение (или долгий тап) -> Копировать ссылку (Copy Post Link).
                        
                        3. Ссылка на пост в Топике (Группы-форумы):
                        Формат: https://t.me/group/100/500
                        Приложение автоматически найдет нужное сообщение внутри темы.
                        
                        4. Внешние списки (GitHub/Web):
                        Формат: https://raw.githubusercontent.com/.../proxy.txt
                        Просто вставьте прямую ссылку на текстовый файл.
                        
                        5. Форматы текста:
                        • IP:PORT
                        • IP:PORT:USER:PASS
                        • USER:PASS@IP:PORT
                        • t.me/proxy?server=... (MTProto)
                        • t.me/socks?server=... (SOCKS5)
                    """.trimIndent()

                    else -> """
                        🛡 Подробно о протоколах:
                        
                        • HTTP: Самый распространенный тип. Работает только с веб-трафиком.
                        • HTTPS: Защищенный HTTP прокси (SSL).
                        • SOCKS4/5: Универсальные протоколы. Через них можно работать в Telegram, играх и любых приложениях. SOCKS5 поддерживает авторизацию (логин/пароль).
                        • MTProto: Эксклюзивный протокол Telegram. Самый стабильный для обхода блокировок мессенджера. Требует секретный ключ (Secret).
                        
                        ⚠️ Если вы не знаете тип прокси — выбирайте 'Автоопределение'. Приложение сначала проверит SOCKS5, затем SOCKS4, и в конце HTTP.
                    """.trimIndent()
                }
            }
            override fun getItemCount() = sections.size
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = sections[position]
        }.attach()
    }

    class AboutViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvContent: TextView = view.findViewById(R.id.tvContent)
    }
}