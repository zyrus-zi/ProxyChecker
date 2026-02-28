package com.example.proxychecker

import java.util.regex.Pattern

object ProxyParser {

    // Форматы: IP:PORT:USER:PASS
    private val IP_PORT_USER_PASS_REGEX = Pattern.compile("\\b((?:[0-9]{1,3}\\.){3}[0-9]{1,3}):([0-9]{1,5}):([^:\\s]+):([^:\\s]+)\\b")

    // Форматы: USER:PASS@IP:PORT
    private val USER_PASS_IP_PORT_REGEX = Pattern.compile("\\b([^:@\\s]+):([^:@\\s]+)@((?:[0-9]{1,3}\\.){3}[0-9]{1,3}):([0-9]{1,5})\\b")

    // Обычный IP:PORT
    private val IP_PORT_REGEX = Pattern.compile("\\b((?:[0-9]{1,3}\\.){3}[0-9]{1,3}):([0-9]{1,5})\\b")

    // Телеграм ссылки
    private val TG_MTPROTO_REGEX = Pattern.compile("t\\.me/proxy\\?server=([^&]+)&port=(\\d+)&secret=([a-zA-Z0-9]+)")
    private val TG_SOCKS_REGEX = Pattern.compile("t\\.me/socks\\?server=([^&]+)&port=(\\d+)&user=([^&]+)&pass=([^&\\s]+)")

    fun parseText(text: String): List<ProxyItem> {
        val proxies = mutableListOf<ProxyItem>()

        // 1. MTProto ссылки Телеграм
        val mtMatcher = TG_MTPROTO_REGEX.matcher(text)
        while (mtMatcher.find()) {
            proxies.add(ProxyItem(host = mtMatcher.group(1)!!, port = mtMatcher.group(2)!!.toInt(), secret = mtMatcher.group(3), protocol = ProxyProtocol.MTPROTO))
        }

        // 2. Socks5 ссылки Телеграм
        val socksMatcher = TG_SOCKS_REGEX.matcher(text)
        while (socksMatcher.find()) {
            proxies.add(ProxyItem(host = socksMatcher.group(1)!!, port = socksMatcher.group(2)!!.toInt(), user = socksMatcher.group(3), pass = socksMatcher.group(4), protocol = ProxyProtocol.SOCKS5))
        }

        // 3. Формат MTProto текстом (Server: ... Port: ... Secret: ...)
        val mtProtoBlocks = text.split(Regex("(?=Server:)"))
        for (block in mtProtoBlocks) {
            val serverMatch = Regex("Server:\\s*([\\w.-]+)").find(block)
            val portMatch = Regex("Port:\\s*(\\d+)").find(block)
            val secretMatch = Regex("Secret:\\s*([\\w]+)").find(block)
            if (serverMatch != null && portMatch != null && secretMatch != null) {
                proxies.add(ProxyItem(host = serverMatch.groupValues[1], port = portMatch.groupValues[1].toInt(), secret = secretMatch.groupValues[1], protocol = ProxyProtocol.MTPROTO))
            }
        }

        // 4. IP:PORT:USER:PASS
        val ipUpMatcher = IP_PORT_USER_PASS_REGEX.matcher(text)
        while (ipUpMatcher.find()) {
            proxies.add(ProxyItem(host = ipUpMatcher.group(1)!!, port = ipUpMatcher.group(2)!!.toInt(), user = ipUpMatcher.group(3), pass = ipUpMatcher.group(4)))
        }

        // 5. USER:PASS@IP:PORT
        val upIpMatcher = USER_PASS_IP_PORT_REGEX.matcher(text)
        while (upIpMatcher.find()) {
            proxies.add(ProxyItem(host = upIpMatcher.group(3)!!, port = upIpMatcher.group(4)!!.toInt(), user = upIpMatcher.group(1), pass = upIpMatcher.group(2)))
        }

        // 6. Простой IP:PORT (игнорируем те, что уже спарсились с юзером/паролем)
        val ipMatcher = IP_PORT_REGEX.matcher(text)
        while (ipMatcher.find()) {
            val host = ipMatcher.group(1)!!
            val port = ipMatcher.group(2)!!.toInt()
            if (!proxies.any { it.host == host && it.port == port }) {
                proxies.add(ProxyItem(host = host, port = port))
            }
        }

        return proxies.distinctBy { "${it.host}:${it.port}" }
    }
}