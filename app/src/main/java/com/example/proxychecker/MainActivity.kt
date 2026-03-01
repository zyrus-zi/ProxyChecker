package com.example.proxychecker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.proxychecker.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ProxyAdapter

    private var sortPingAsc = true
    private var sortProtoAsc = true
    private var sortCountryAsc = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. ЖЕСТКОЕ ПРИМЕНЕНИЕ ТЕМЫ
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        // ИСПРАВЛЕНО: false (Светлая по умолчанию), чтобы совпадало с ProxyApplication
        val isDarkMode = prefs.getBoolean("isDarkTheme", false)

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. СКРЫТИЕ СТРОКИ СОСТОЯНИЯ
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())

        // Кнопка смены темы
        binding.btnThemeToggle.setOnClickListener {
            val currentNightMode = prefs.getBoolean("isDarkTheme", false) // Здесь тоже исправлено на false
            val newMode = !currentNightMode
            prefs.edit().putBoolean("isDarkTheme", newMode).apply()

            if (newMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        val protocols = resources.getStringArray(R.array.protocols_array)
        val arrayAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, protocols)
        binding.spinnerProtocol.setAdapter(arrayAdapter)
        binding.spinnerProtocol.setOnClickListener { binding.spinnerProtocol.showDropDown() }

        adapter = ProxyAdapter(ProxyManager.proxyList)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        observeManager()

        binding.btnStart.setOnClickListener {
            val input = binding.etInput.text.toString()
            val threads = binding.etThreads.text.toString().toIntOrNull() ?: 50
            val timeout = binding.etTimeout.text.toString().toIntOrNull() ?: 5000

            val selectedProtocolText = binding.spinnerProtocol.text.toString()
            val forceProtocol = when (selectedProtocolText) {
                "HTTP" -> ProxyProtocol.HTTP
                "HTTPS" -> ProxyProtocol.HTTPS
                "SOCKS4" -> ProxyProtocol.SOCKS4
                "SOCKS5" -> ProxyProtocol.SOCKS5
                "MTPROTO" -> ProxyProtocol.MTPROTO
                else -> ProxyProtocol.UNKNOWN
            }
            val isAutoDetect = forceProtocol == ProxyProtocol.UNKNOWN

            binding.cbSelectAll.isChecked = false
            ProxyManager.proxyList.forEach { it.isSelected = false }

            if (input.isNotEmpty()) {
                ProxyManager.proxyList.clear()
                adapter.notifyDataSetChanged()
            } else {
                ProxyManager.proxyList.forEach {
                    it.isAlive = false
                    it.pingMs = -1
                    it.country = "Unknown"
                }
                adapter.notifyDataSetChanged()
            }

            ProxyManager.startChecking(
                context = this,
                inputText = input,
                threads = threads,
                timeoutMs = timeout,
                autoDetect = isAutoDetect,
                forceProtocol = forceProtocol
            )
        }

        binding.btnPause.setOnClickListener { ProxyManager.togglePause() }
        binding.btnStop.setOnClickListener { ProxyManager.stopChecking(this) }

        binding.cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            ProxyManager.proxyList.forEach {
                if (it.isAlive) it.isSelected = isChecked else it.isSelected = false
            }
            adapter.notifyDataSetChanged()
        }

        binding.headerPing.setOnClickListener {
            if (sortPingAsc) {
                ProxyManager.proxyList.sortBy { if (it.isAlive) it.pingMs else Long.MAX_VALUE }
                binding.headerPing.text = "Пинг ▲"
            } else {
                ProxyManager.proxyList.sortByDescending { if (it.isAlive) it.pingMs else -1L }
                binding.headerPing.text = "Пинг ▼"
            }
            sortPingAsc = !sortPingAsc
            binding.headerProtocol.text = "Тип ▼"
            binding.headerCountry.text = "Страна ▼"
            adapter.notifyDataSetChanged()
        }

        binding.headerProtocol.setOnClickListener {
            if (sortProtoAsc) {
                ProxyManager.proxyList.sortBy { it.protocol.name }
                binding.headerProtocol.text = "Тип ▲"
            } else {
                ProxyManager.proxyList.sortByDescending { it.protocol.name }
                binding.headerProtocol.text = "Тип ▼"
            }
            sortProtoAsc = !sortProtoAsc
            binding.headerPing.text = "Пинг ▼"
            binding.headerCountry.text = "Страна ▼"
            adapter.notifyDataSetChanged()
        }

        binding.headerCountry.setOnClickListener {
            if (sortCountryAsc) {
                ProxyManager.proxyList.sortBy { it.country }
                binding.headerCountry.text = "Страна ▲"
            } else {
                ProxyManager.proxyList.sortByDescending { it.country }
                binding.headerCountry.text = "Страна ▼"
            }
            sortCountryAsc = !sortCountryAsc
            binding.headerPing.text = "Пинг ▼"
            binding.headerProtocol.text = "Тип ▼"
            adapter.notifyDataSetChanged()
        }

        binding.btnExportTxt.setOnClickListener {
            val aliveSelected = ProxyManager.proxyList.filter { it.isSelected && it.isAlive }
            if (aliveSelected.isEmpty()) {
                Toast.makeText(this, "Нет выбранных живых прокси!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sb = java.lang.StringBuilder()
            val grouped = aliveSelected.groupBy { it.protocol }
            for ((protocol, list) in grouped) {
                sb.append("------- ${protocol.name} -------\n")
                for (proxy in list) {
                    sb.append(proxy.toString()).append("\n")
                }
                sb.append("\n")
            }
            val txt = sb.toString().trim()

            val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.bottom_sheet_export, null)
            bottomSheetDialog.setContentView(view)

            view.findViewById<android.widget.Button>(R.id.btnCopyTxt).setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Proxies", txt))
                Toast.makeText(this, "Скопировано в буфер обмена!", Toast.LENGTH_SHORT).show()
                bottomSheetDialog.dismiss()
            }

            view.findViewById<android.widget.Button>(R.id.btnShareTxt).setOnClickListener {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, txt)
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(sendIntent, "Поделиться прокси"))
                bottomSheetDialog.dismiss()
            }

            view.findViewById<android.widget.Button>(R.id.btnDownloadTxt).setOnClickListener {
                saveTextToDownloads(txt)
                bottomSheetDialog.dismiss()
            }

            bottomSheetDialog.show()
        }

        binding.btnExportTg.setOnClickListener {
            val hasValidTgProxies = ProxyManager.proxyList.any {
                it.isSelected && it.isAlive && it.toTgLink().isNotEmpty()
            }

            if (hasValidTgProxies) {
                val intent = Intent(this, TelegramExportActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Выберите живые SOCKS5 с паролем или MTProto!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveTextToDownloads(text: String) {
        try {
            val fileName = "Proxies_${System.currentTimeMillis()}.txt"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                    Toast.makeText(this, "Файл сохранен в Загрузки", Toast.LENGTH_LONG).show()
                }
            } else {
                val path = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(path, fileName)
                file.writeText(text)
                Toast.makeText(this, "Файл сохранен в Загрузки", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка сохранения файла: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private var planeAnimator: android.animation.AnimatorSet? = null

    private fun startPlaneAnimation() {
        if (planeAnimator != null) return
        val plane = binding.ivPlane
        val translationY = android.animation.ObjectAnimator.ofFloat(plane, "translationY", 0f, -10f, 0f).apply {
            duration = 2000
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        val rotation = android.animation.ObjectAnimator.ofFloat(plane, "rotation", 0f, -15f, 0f).apply {
            duration = 1800
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
        }
        planeAnimator = android.animation.AnimatorSet().apply {
            playTogether(translationY, rotation)
            start()
        }
    }

    private fun stopPlaneAnimation() {
        planeAnimator?.cancel()
        planeAnimator = null
        binding.ivPlane.rotation = 0f
        binding.ivPlane.translationY = 0f
    }

    private fun observeManager() {
        lifecycleScope.launch {
            ProxyManager.stateFlow.collect { state ->
                when (state) {
                    CheckerState.IDLE -> {
                        binding.btnStart.isEnabled = true
                        binding.btnPause.isEnabled = false
                        binding.btnStop.isEnabled = false
                        binding.btnPause.setIconResource(R.drawable.ic_pause)
                        stopPlaneAnimation()
                    }
                    CheckerState.RUNNING -> {
                        binding.btnStart.isEnabled = false
                        binding.btnPause.isEnabled = true
                        binding.btnStop.isEnabled = true
                        binding.btnPause.setIconResource(R.drawable.ic_pause)
                        startPlaneAnimation()
                    }
                    CheckerState.PAUSED -> {
                        binding.btnStart.isEnabled = false
                        binding.btnPause.isEnabled = true
                        binding.btnStop.isEnabled = true
                        binding.btnPause.setIconResource(R.drawable.ic_play)
                    }
                }
            }
        }

        lifecycleScope.launch {
            ProxyManager.progressFlow.collect { (current, total) ->
                binding.tvProgress.text = "Готово: $current / $total"
                if (total > 0) {
                    val percent = current.toFloat() / total.toFloat()
                    val params = binding.progressGuideline.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                    params.guidePercent = percent
                    binding.progressGuideline.layoutParams = params
                } else {
                    val params = binding.progressGuideline.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                    params.guidePercent = 0f
                    binding.progressGuideline.layoutParams = params
                }
            }
        }

        lifecycleScope.launch {
            ProxyManager.listUpdateFlow.collect {
                adapter.notifyDataSetChanged()
                binding.etInput.setText("")
                val params = binding.progressGuideline.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                params.guidePercent = 0f
                binding.progressGuideline.layoutParams = params
            }
        }

        lifecycleScope.launch {
            ProxyManager.itemUpdateFlow.collect { index ->
                adapter.notifyItemChanged(index)
            }
        }

        lifecycleScope.launch {
            ProxyManager.finishedFlow.collect {
                Toast.makeText(this@MainActivity, "Проверка завершена/остановлена", Toast.LENGTH_SHORT).show()
                stopPlaneAnimation()
            }
        }
    }
}