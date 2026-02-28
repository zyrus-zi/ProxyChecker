package com.example.proxychecker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ProxyAdapter(ProxyManager.proxyList)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        observeManager()

        binding.btnStart.setOnClickListener {
            val input = binding.etInput.text.toString()
            val threads = binding.etThreads.text.toString().toIntOrNull() ?: 50
            val timeout = binding.etTimeout.text.toString().toIntOrNull() ?: 5000

            val isAutoDetect = binding.spinnerProtocol.selectedItemPosition == 0
            val forceProtocol = when (binding.spinnerProtocol.selectedItemPosition) {
                1 -> ProxyProtocol.HTTP
                2 -> ProxyProtocol.HTTPS
                3 -> ProxyProtocol.SOCKS4
                4 -> ProxyProtocol.SOCKS5
                5 -> ProxyProtocol.MTPROTO
                else -> ProxyProtocol.UNKNOWN
            }

            // СБРОС ВЫДЕЛЕНИЯ ПРИ СТАРТЕ
            binding.cbSelectAll.isChecked = false
            ProxyManager.proxyList.forEach { it.isSelected = false }
            adapter.notifyDataSetChanged()

            if (input.isNotEmpty()) {
                ProxyManager.proxyList.clear()
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

        binding.btnPause.setOnClickListener {
            ProxyManager.togglePause()
        }

        binding.btnStop.setOnClickListener {
            ProxyManager.stopChecking(this)
        }

        binding.cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            ProxyManager.proxyList.forEach {
                if (it.isAlive) {
                    it.isSelected = isChecked
                } else {
                    it.isSelected = false
                }
            }
            adapter.notifyDataSetChanged()
        }

        binding.headerPing.setOnClickListener {
            if (sortPingAsc) ProxyManager.proxyList.sortBy { if (it.isAlive) it.pingMs else Long.MAX_VALUE }
            else ProxyManager.proxyList.sortByDescending { if (it.isAlive) it.pingMs else -1L }
            sortPingAsc = !sortPingAsc
            adapter.notifyDataSetChanged()
        }
        binding.headerProtocol.setOnClickListener {
            if (sortProtoAsc) ProxyManager.proxyList.sortBy { it.protocol.name }
            else ProxyManager.proxyList.sortByDescending { it.protocol.name }
            sortProtoAsc = !sortProtoAsc
            adapter.notifyDataSetChanged()
        }
        binding.headerCountry.setOnClickListener {
            if (sortCountryAsc) ProxyManager.proxyList.sortBy { it.country }
            else ProxyManager.proxyList.sortByDescending { it.country }
            sortCountryAsc = !sortCountryAsc
            adapter.notifyDataSetChanged()
        }

        binding.btnExportTxt.setOnClickListener {
            val aliveSelected = ProxyManager.proxyList.filter { it.isSelected && it.isAlive }

            if (aliveSelected.isEmpty()) {
                Toast.makeText(this, "Нет выбранных живых прокси!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sb = StringBuilder()
            val grouped = aliveSelected.groupBy { it.protocol }
            for ((protocol, list) in grouped) {
                sb.append("------- ${protocol.name} -------\n")
                for (proxy in list) {
                    sb.append(proxy.toString()).append("\n")
                }
                sb.append("\n")
            }

            val txt = sb.toString().trim()

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Proxies", txt))
            Toast.makeText(this, "Скопировано в буфер обмена!", Toast.LENGTH_SHORT).show()

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, txt)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, "Сохранить/Отправить прокси"))
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

    private fun observeManager() {
        lifecycleScope.launch {
            ProxyManager.stateFlow.collect { state ->
                when (state) {
                    CheckerState.IDLE -> {
                        binding.btnStart.isEnabled = true
                        binding.btnPause.isEnabled = false
                        binding.btnStop.isEnabled = false
                        binding.btnPause.text = "Пауза"
                    }
                    CheckerState.RUNNING -> {
                        binding.btnStart.isEnabled = false
                        binding.btnPause.isEnabled = true
                        binding.btnStop.isEnabled = true
                        binding.btnPause.text = "Пауза"
                    }
                    CheckerState.PAUSED -> {
                        binding.btnStart.isEnabled = false
                        binding.btnPause.isEnabled = true
                        binding.btnStop.isEnabled = true
                        binding.btnPause.text = "Продолжить"
                    }
                }
            }
        }

        lifecycleScope.launch {
            ProxyManager.progressFlow.collect { (current, total) ->
                binding.progressBar.max = total
                binding.progressBar.progress = current
                binding.tvProgress.text = "Готово: $current / $total"
            }
        }

        lifecycleScope.launch {
            ProxyManager.listUpdateFlow.collect {
                adapter.notifyDataSetChanged()
                binding.etInput.setText("")
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
            }
        }
    }
}