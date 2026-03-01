package com.example.proxychecker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
        // ТЕМА: ИСПРАВЛЕНО НА false
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("isDarkTheme", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_telegram_export)

        // СКРЫТИЕ СТАТУС БАРА
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())

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
            Toast.makeText(this, "Нет доступных протоколов для отправки", Toast.LENGTH_SHORT).show()
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
                val selectedProtocols = mutableListOf<ProxyProtocol>()
                for (i in checkedItems.indices) {
                    if (checkedItems[i]) {
                        selectedProtocols.add(availableProtocols[i])
                    }
                }
                sendToTelegramSavedMessages(selectedProtocols)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun sendToTelegramSavedMessages(protocols: List<ProxyProtocol>) {
        val filteredList = selectedProxies.filter { protocols.contains(it.protocol) }

        if (filteredList.isEmpty()) {
            Toast.makeText(this, "Ничего не выбрано", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        val includeCountry = cbIncludeCountry.isChecked
        val includePing = cbIncludePing.isChecked
        val groupByProtocol = cbGroupByProtocol.isChecked
        val isCompact = cbCompactView.isChecked

        if (groupByProtocol) {
            val grouped = filteredList.groupBy {
                if (it.protocol == ProxyProtocol.SOCKS4) ProxyProtocol.SOCKS5 else it.protocol
            }
            for ((protocol, list) in grouped) {
                sb.append("\n------- ${protocol.name} -------\n\n")
                appendProxyList(sb, list, includeCountry, includePing, isCompact)
            }
        } else {
            appendProxyList(sb, filteredList, includeCountry, includePing, isCompact)
        }

        val finalMessage = sb.toString().trim()

        try {
            val uri = Uri.parse("tg://msg?text=${Uri.encode(finalMessage)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, finalMessage)
                setPackage("org.telegram.messenger")
            }
            try {
                startActivity(shareIntent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Не удалось открыть Telegram.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateResultText() {
        val includeCountry = cbIncludeCountry.isChecked
        val includePing = cbIncludePing.isChecked
        val groupByProtocol = cbGroupByProtocol.isChecked
        val isCompact = cbCompactView.isChecked

        val sb = StringBuilder()

        if (groupByProtocol) {
            val grouped = selectedProxies.groupBy {
                if (it.protocol == ProxyProtocol.SOCKS4) ProxyProtocol.SOCKS5 else it.protocol
            }
            for ((protocol, list) in grouped) {
                sb.append("\n------- ${protocol.name} -------\n\n")
                appendProxyList(sb, list, includeCountry, includePing, isCompact)
            }
        } else {
            appendProxyList(sb, selectedProxies, includeCountry, includePing, isCompact)
        }

        etResult.setText(sb.toString().trim())
    }

    private fun appendProxyList(
        sb: StringBuilder,
        list: List<ProxyItem>,
        includeCountry: Boolean,
        includePing: Boolean,
        isCompact: Boolean
    ) {
        for (proxy in list) {
            if (includeCountry || includePing) {
                val headerParts = mutableListOf<String>()
                if (includeCountry) headerParts.add(proxy.country)
                if (includePing) headerParts.add("⚡ ${proxy.pingMs}ms")

                sb.append(headerParts.joinToString(" | ")).append("\n")
            }

            sb.append(proxy.toTgLink()).append("\n")

            if (!isCompact) {
                sb.append("\n")
            }
        }
    }
}