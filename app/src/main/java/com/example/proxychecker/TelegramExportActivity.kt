package com.example.proxychecker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class TelegramExportActivity : AppCompatActivity() {

    private lateinit var etResult: EditText
    private lateinit var cbIncludeCountry: MaterialSwitch
    private lateinit var cbIncludePing: MaterialSwitch
    private lateinit var cbGroupByProtocol: MaterialSwitch
    private lateinit var cbCompactView: MaterialSwitch

    private val selectedProxies = ProxyManager.proxyList.filter {
        it.isSelected && it.isAlive && it.toTgLink().isNotEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. ТЕМА
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("isDarkTheme", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_telegram_export)

        // 2. СКРЫТИЕ СТАТУС БАРА
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())

        // 3. ЛОГИКА ЗВЕЗД (ИСПРАВЛЕНО: Теперь звезды отключаются глобально)
        val starField = findViewById<StarFieldView>(R.id.starField)
        val isStarsEnabled = prefs.getBoolean("isStarsEnabled", true)
        starField.visibility = if (isStarsEnabled) View.VISIBLE else View.GONE
        if (isStarsEnabled) starField.updateColors()

        // Остальная инициализация
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        etResult = findViewById(R.id.etResult)
        cbIncludeCountry = findViewById(R.id.cbIncludeCountry)
        cbIncludePing = findViewById(R.id.cbIncludePing)
        cbGroupByProtocol = findViewById(R.id.cbGroupByProtocol)
        cbCompactView = findViewById(R.id.cbCompactView)

        val btnCopy = findViewById<Button>(R.id.btnCopy)
        val btnShare = findViewById<Button>(R.id.btnShare)
        val btnToSaved = findViewById<Button>(R.id.btnToSaved)

        val changeListener = { _: Any?, _: Any? -> updateResultText() }
        cbIncludeCountry.setOnCheckedChangeListener(changeListener)
        cbIncludePing.setOnCheckedChangeListener(changeListener)
        cbGroupByProtocol.setOnCheckedChangeListener(changeListener)
        cbCompactView.setOnCheckedChangeListener(changeListener)

        updateResultText()

        btnCopy.setOnClickListener {
            val text = etResult.text.toString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Telegram Proxies", text))
            Toast.makeText(this, "Скопировано в буфер обмена!", Toast.LENGTH_SHORT).show()
        }

        btnShare.setOnClickListener {
            val text = etResult.text.toString()
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, "Отправить прокси Telegram"))
        }

        btnToSaved.setOnClickListener {
            showProtocolSelectionDialog()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showProtocolSelectionDialog() {
        val availableProtocols = selectedProxies.map { it.protocol }.distinct().sortedBy { it.name }
        if (availableProtocols.isEmpty()) {
            Toast.makeText(this, "Нет доступных протоколов", Toast.LENGTH_SHORT).show()
            return
        }
        val protocolNames = availableProtocols.map { it.name }.toTypedArray()
        val checkedItems = BooleanArray(availableProtocols.size) { true }

        MaterialAlertDialogBuilder(this)
            .setTitle("Выберите протоколы")
            .setMultiChoiceItems(protocolNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Отправить") { _, _ ->
                val selected = mutableListOf<ProxyProtocol>()
                for (i in checkedItems.indices) if (checkedItems[i]) selected.add(availableProtocols[i])
                sendToTelegramSavedMessages(selected)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun sendToTelegramSavedMessages(protocols: List<ProxyProtocol>) {
        val filteredList = selectedProxies.filter { protocols.contains(it.protocol) }
        if (filteredList.isEmpty()) return

        val sb = StringBuilder()
        if (cbGroupByProtocol.isChecked) {
            val grouped = filteredList.groupBy {
                if (it.protocol == ProxyProtocol.SOCKS4) ProxyProtocol.SOCKS5 else it.protocol
            }
            for ((protocol, list) in grouped) {
                sb.append("\n------- ${protocol.name} -------\n\n")
                appendProxyList(sb, list, cbIncludeCountry.isChecked, cbIncludePing.isChecked, cbCompactView.isChecked)
            }
        } else {
            appendProxyList(sb, filteredList, cbIncludeCountry.isChecked, cbIncludePing.isChecked, cbCompactView.isChecked)
        }

        val finalMessage = sb.toString().trim()
        try {
            val uri = Uri.parse("tg://msg?text=${Uri.encode(finalMessage)}")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, finalMessage)
                setPackage("org.telegram.messenger")
            }
            try { startActivity(shareIntent) } catch (ex: Exception) {
                Toast.makeText(this, "Telegram не найден", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateResultText() {
        val sb = StringBuilder()
        if (cbGroupByProtocol.isChecked) {
            val grouped = selectedProxies.groupBy {
                if (it.protocol == ProxyProtocol.SOCKS4) ProxyProtocol.SOCKS5 else it.protocol
            }
            for ((protocol, list) in grouped) {
                sb.append("\n------- ${protocol.name} -------\n\n")
                appendProxyList(sb, list, cbIncludeCountry.isChecked, cbIncludePing.isChecked, cbCompactView.isChecked)
            }
        } else {
            appendProxyList(sb, selectedProxies, cbIncludeCountry.isChecked, cbIncludePing.isChecked, cbCompactView.isChecked)
        }
        etResult.setText(sb.toString().trim())
    }

    private fun appendProxyList(sb: StringBuilder, list: List<ProxyItem>, country: Boolean, ping: Boolean, compact: Boolean) {
        for (proxy in list) {
            if (country || ping) {
                val p = mutableListOf<String>()
                if (country) p.add(proxy.country)
                if (ping) p.add("⚡ ${proxy.pingMs}ms")
                sb.append(p.joinToString(" | ")).append("\n")
            }
            sb.append(proxy.toTgLink()).append("\n")
            if (!compact) sb.append("\n")
        }
    }
}