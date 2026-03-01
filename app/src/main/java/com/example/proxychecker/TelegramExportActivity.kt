package com.example.proxychecker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch

class TelegramExportActivity : AppCompatActivity() {

    private lateinit var etResult: EditText

    // ИСПРАВЛЕНИЕ ЗДЕСЬ: CheckBox заменен на MaterialSwitch
    private lateinit var cbIncludeCountry: MaterialSwitch
    private lateinit var cbIncludePing: MaterialSwitch
    private lateinit var cbGroupByProtocol: MaterialSwitch
    private lateinit var cbCompactView: MaterialSwitch

    private val selectedProxies = ProxyManager.proxyList.filter {
        it.isSelected && it.isAlive && it.toTgLink().isNotEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_telegram_export)

        // Инициализация красивого тулбара с кнопкой "Назад"
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
    }

    // ОБРАБОТКА НАЖАТИЯ КНОПКИ НАЗАД В ТУЛБАРЕ
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun updateResultText() {
        val includeCountry = cbIncludeCountry.isChecked
        val includePing = cbIncludePing.isChecked
        val groupByProtocol = cbGroupByProtocol.isChecked
        val isCompact = cbCompactView.isChecked

        val sb = StringBuilder()

        if (groupByProtocol) {
            val grouped = selectedProxies.groupBy { it.protocol }
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