package com.example.proxychecker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TelegramExportActivity : AppCompatActivity() {

    private lateinit var etResult: EditText
    private lateinit var cbIncludeCountry: CheckBox
    private lateinit var cbIncludePing: CheckBox
    private lateinit var cbGroupByProtocol: CheckBox
    private lateinit var cbCompactView: CheckBox

    private val selectedProxies = ProxyManager.proxyList.filter {
        it.isSelected && it.isAlive && it.toTgLink().isNotEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_telegram_export)

        // ВКЛЮЧАЕМ КНОПКУ НАЗАД
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Экспорт в Telegram"

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

    // ОБРАБОТКА НАЖАТИЯ КНОПКИ НАЗАД
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