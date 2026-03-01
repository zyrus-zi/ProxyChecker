package com.example.proxychecker

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

enum class CheckerState { IDLE, RUNNING, PAUSED }

object ProxyManager {
    val proxyList = mutableListOf<ProxyItem>()
    private val checker = ProxyChecker()

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var checkingJob: Job? = null

    val stateFlow = MutableStateFlow(CheckerState.IDLE)
    val progressFlow = MutableStateFlow(Pair(0, 0))
    val listUpdateFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val itemUpdateFlow = MutableSharedFlow<Int>(extraBufferCapacity = 100)
    val finishedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    var isPaused = false
        private set

    fun togglePause() {
        if (stateFlow.value == CheckerState.RUNNING) {
            stateFlow.value = CheckerState.PAUSED
            isPaused = true
        } else if (stateFlow.value == CheckerState.PAUSED) {
            stateFlow.value = CheckerState.RUNNING
            isPaused = false
        }
    }

    fun stopChecking(context: Context) {
        checkingJob?.cancel()
        checkingJob = null
        stateFlow.value = CheckerState.IDLE
        isPaused = false
        context.stopService(Intent(context, ProxyService::class.java))
    }

    fun startChecking(
        context: Context,
        inputText: String,
        threads: Int,
        timeoutMs: Int,
        autoDetect: Boolean,
        forceProtocol: ProxyProtocol
    ) {
        if (stateFlow.value == CheckerState.RUNNING) return

        checkingJob = appScope.launch {
            stateFlow.value = CheckerState.RUNNING
            isPaused = false

            val needsParsing = proxyList.isEmpty() || inputText.isNotEmpty()

            if (needsParsing) {
                val allParsedProxies = mutableListOf<ProxyItem>()
                val urls = extractUrls(inputText)
                for (url in urls) {
                    if (url.contains("t.me/")) {
                        allParsedProxies.addAll(fetchFromTelegram(url))
                    } else if (url.contains("githubusercontent") || url.startsWith("http")) {
                        allParsedProxies.addAll(fetchFromWeb(url))
                    }
                }
                allParsedProxies.addAll(ProxyParser.parseText(inputText))

                proxyList.clear()
                proxyList.addAll(allParsedProxies.distinctBy { "${it.host}:${it.port}" })
                listUpdateFlow.tryEmit(Unit)
            }

            if (proxyList.isEmpty()) {
                stateFlow.value = CheckerState.IDLE
                finishedFlow.tryEmit(Unit)
                return@launch
            }

            val serviceIntent = Intent(context, ProxyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            val proxiesToCheck = proxyList.filter { it.pingMs == -1L && !it.isAlive }
            val alreadyCheckedCount = proxyList.size - proxiesToCheck.size
            val totalOriginalCount = proxyList.size
            val protocolToUse = if (autoDetect) ProxyProtocol.UNKNOWN else forceProtocol

            checker.checkProxyList(
                proxies = proxiesToCheck,
                alreadyCheckedCount = alreadyCheckedCount,
                totalOriginalCount = totalOriginalCount,
                threadCount = threads,
                timeoutMs = timeoutMs,
                forceProtocol = protocolToUse,
                pauseHandler = {
                    while (stateFlow.value == CheckerState.PAUSED) { delay(200) }
                },
                onProgress = { current, total ->
                    progressFlow.value = Pair(current, total)
                },
                onResult = { resultProxy ->
                    val index = proxyList.indexOfFirst { it.host == resultProxy.host && it.port == resultProxy.port }
                    if (index != -1) {
                        proxyList[index] = resultProxy
                        itemUpdateFlow.tryEmit(index)
                    }
                }
            )

            if (isActive) {
                stateFlow.value = CheckerState.IDLE
                finishedFlow.tryEmit(Unit)
                // Останавливаем сервис прогресса
                context.stopService(serviceIntent)
                // Небольшая задержка, чтобы сервис успел удалиться и убрать свое уведомление
                delay(200)
                // Показываем уведомление о завершении
                ProxyService.showCompletionNotification(context)
            }
        }
    }

    private fun extractUrls(text: String): List<String> {
        return Regex("(https?://\\S+)").findAll(text).map { it.value }.toList()
    }

    private suspend fun fetchFromWeb(url: String): List<ProxyItem> = withContext(Dispatchers.IO) {
        try {
            val response = OkHttpClient().newCall(Request.Builder().url(url).build()).execute()
            val body = response.body?.string() ?: ""
            response.close()
            ProxyParser.parseText(body)
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun fetchFromTelegram(originalUrl: String): List<ProxyItem> = withContext(Dispatchers.IO) {
        val proxies = mutableListOf<ProxyItem>()
        try {
            var cleanUrl = originalUrl.trim()
            if (cleanUrl.endsWith("/")) cleanUrl = cleanUrl.dropLast(1)

            val path = cleanUrl
                .replace("https://", "")
                .replace("http://", "")
                .replace("t.me/s/", "")
                .replace("t.me/", "")

            val parts = path.split("/").filter { it.isNotEmpty() }
            val isMessage = parts.isNotEmpty() && parts.last().all { it.isDigit() }

            if (isMessage) {
                val channelName = parts[0]
                val msgId = parts.last()

                if (parts.size >= 3) {
                    val embedUrl = "https://t.me/$channelName/$msgId?embed=1&mode=tme"
                    val doc = Jsoup.connect(embedUrl).userAgent("Mozilla/5.0").get()
                    val contentElements = doc.select("div.tgme_widget_message_text")
                    if (contentElements.isNotEmpty()) {
                        proxies.addAll(ProxyParser.parseText(contentElements.text() + " " + contentElements.select("a").joinToString(" ") { it.attr("href") }))
                    }
                } else {
                    val scrapeUrl = "https://t.me/s/$channelName/$msgId"
                    val dataPostId = "$channelName/$msgId"
                    val doc = Jsoup.connect(scrapeUrl).userAgent("Mozilla/5.0").get()
                    val specificMessageElement = doc.selectFirst("div.tgme_widget_message[data-post='$dataPostId']")
                    if (specificMessageElement != null) {
                        proxies.addAll(ProxyParser.parseText(specificMessageElement.text() + " " + specificMessageElement.select("a").joinToString(" ") { it.attr("href") }))
                    }
                }
            } else {
                var currentUrl = "https://t.me/s/${parts[0]}"
                val oneMonthAgoMillis = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                var pageCount = 0
                val maxPages = 20

                while (currentUrl.isNotEmpty() && pageCount < maxPages) {
                    pageCount++
                    val doc = Jsoup.connect(currentUrl).get()
                    val text = doc.text()
                    val links = doc.select("a").joinToString(" ") { it.attr("href") }
                    proxies.addAll(ProxyParser.parseText("$text $links"))

                    var oldestOnPage = System.currentTimeMillis()
                    val timeElements = doc.select("time")
                    for (timeElem in timeElements) {
                        val datetime = timeElem.attr("datetime")
                        try {
                            val dateStr = datetime.substringBefore("T")
                            val date = sdf.parse(dateStr)
                            if (date != null && date.time < oldestOnPage) oldestOnPage = date.time
                        } catch (_: Exception) {}
                    }
                    if (oldestOnPage < oneMonthAgoMillis) break
                    val moreLink = doc.select("a.tme_messages_more").attr("href")
                    if (moreLink.isNotEmpty()) currentUrl = "https://t.me$moreLink" else break
                }
            }
        } catch (_: Exception) {}
        return@withContext proxies.distinctBy { "${it.host}:${it.port}" }
    }
}