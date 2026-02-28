package com.example.proxychecker

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class ProxyChecker {

    companion object {
        private val socksAuthMap = ConcurrentHashMap<String, PasswordAuthentication>()
        private val baseClient: OkHttpClient

        init {
            java.net.Authenticator.setDefault(object : java.net.Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? {
                    val key = "${requestingHost}:${requestingPort}"
                    return socksAuthMap[key]
                }
            })

            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
            })
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())

            baseClient = OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectionPool(ConnectionPool(100, 5, TimeUnit.MINUTES)) // Оптимизировано
                .connectTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }

    private fun getFlagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return "🏳️"
        val firstLetter = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }

    suspend fun checkProxyList(
        proxies: List<ProxyItem>,
        alreadyCheckedCount: Int,
        totalOriginalCount: Int,
        threadCount: Int,
        timeoutMs: Int,
        forceProtocol: ProxyProtocol = ProxyProtocol.UNKNOWN,
        pauseHandler: suspend () -> Unit,
        onProgress: (Int, Int) -> Unit,
        onResult: (ProxyItem) -> Unit
    ) = coroutineScope {

        proxies.filter { it.user != null && it.pass != null }.forEach {
            socksAuthMap["${it.host}:${it.port}"] = PasswordAuthentication(it.user, it.pass!!.toCharArray())
        }

        val semaphore = Semaphore(threadCount)
        var checkedCount = alreadyCheckedCount

        val jobs = proxies.map { proxy ->
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    pauseHandler()

                    // Увеличили общий лимит ожидания, чтобы DNS успел разрешиться
                    val result = withTimeoutOrNull(timeoutMs.toLong() + 3000L) {
                        testSingleProxy(proxy, forceProtocol, timeoutMs)
                    } ?: proxy.copy(isAlive = false)

                    checkedCount++
                    withContext(Dispatchers.Main) {
                        onProgress(checkedCount, totalOriginalCount)
                        onResult(result)
                    }
                }
            }
        }
        jobs.joinAll()
    }

    private suspend fun testSingleProxy(proxy: ProxyItem, protocol: ProxyProtocol, timeout: Int): ProxyItem {
        // --- 1. Проверка MTPROTO ---
        if (protocol == ProxyProtocol.MTPROTO || proxy.secret != null) {
            val startTime = System.currentTimeMillis()
            return try {
                val socket = Socket()
                socket.connect(InetSocketAddress(proxy.host, proxy.port), timeout)
                val ping = System.currentTimeMillis() - startTime
                socket.close()

                // Сначала резолвим IP, потом спрашиваем страну
                val countryInfo = fetchCountryForHost(proxy.host)

                proxy.copy(
                    isAlive = true,
                    pingMs = ping,
                    protocol = ProxyProtocol.MTPROTO,
                    country = countryInfo
                )
            } catch (e: Exception) {
                proxy.copy(isAlive = false, protocol = ProxyProtocol.MTPROTO)
            }
        }

        // --- 2. Проверка HTTP / SOCKS ---
        return try {
            val actualProtocol = if (protocol == ProxyProtocol.UNKNOWN) ProxyProtocol.SOCKS5 else protocol
            testWithOkHttp(proxy, actualProtocol, timeout)
        } catch (e: Exception) {
            if (protocol == ProxyProtocol.UNKNOWN) {
                try {
                    return testWithOkHttp(proxy, ProxyProtocol.SOCKS4, timeout)
                } catch (e2: Exception) {
                    try {
                        return testWithOkHttp(proxy, ProxyProtocol.HTTP, timeout)
                    } catch (e3: Exception) {
                        return proxy.copy(isAlive = false)
                    }
                }
            }
            proxy.copy(isAlive = false)
        }
    }

    private fun testWithOkHttp(proxy: ProxyItem, protocol: ProxyProtocol, timeout: Int): ProxyItem {
        val startTime = System.currentTimeMillis()
        val javaProxyType = when (protocol) {
            ProxyProtocol.SOCKS4, ProxyProtocol.SOCKS5 -> Proxy.Type.SOCKS
            else -> Proxy.Type.HTTP
        }
        val javaProxy = Proxy(javaProxyType, InetSocketAddress(proxy.host, proxy.port))

        val client = baseClient.newBuilder()
            .proxy(javaProxy)
            .connectTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
            .apply {
                if (proxy.user != null && proxy.pass != null && javaProxyType == Proxy.Type.HTTP) {
                    proxyAuthenticator { _, response ->
                        val credential = Credentials.basic(proxy.user, proxy.pass)
                        response.request.newBuilder().header("Proxy-Authorization", credential).build()
                    }
                }
            }
            .build()

        val request = Request.Builder().url("https://ipwho.is/").build()

        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val regex = Regex("\"country\":\"([^\"]+)\".*?\"country_code\":\"([^\"]+)\"")
                val match = regex.find(body)
                val countryName = match?.groupValues?.get(1) ?: "Unknown"
                val countryCode = match?.groupValues?.get(2) ?: ""

                val flag = if (countryCode.isNotEmpty()) getFlagEmoji(countryCode) else "🏳️"
                val finalCountry = if (countryName != "Unknown") "$flag $countryName" else "Unknown"

                proxy.copy(
                    isAlive = true,
                    pingMs = System.currentTimeMillis() - startTime,
                    country = finalCountry,
                    protocol = protocol
                )
            } else {
                throw Exception("Fail")
            }
        }
    }

    /**
     * Превращает домен в IP и узнает страну
     */
    private fun fetchCountryForHost(host: String): String {
        return try {
            // DNS Resolve: minecraftiro.com -> 1.2.3.4
            val ipAddress = InetAddress.getByName(host).hostAddress ?: host

            val request = Request.Builder().url("https://ipwho.is/$ipAddress").build()
            baseClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val regex = Regex("\"country\":\"([^\"]+)\".*?\"country_code\":\"([^\"]+)\"")
                    val match = regex.find(body)
                    val countryName = match?.groupValues?.get(1) ?: "Unknown"
                    val countryCode = match?.groupValues?.get(2) ?: ""

                    val flag = if (countryCode.isNotEmpty()) getFlagEmoji(countryCode) else "🏳️"
                    if (countryName != "Unknown") "$flag $countryName" else "Unknown"
                } else "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
}