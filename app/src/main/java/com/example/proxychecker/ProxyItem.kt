package com.example.proxychecker

enum class ProxyProtocol { HTTP, HTTPS, SOCKS4, SOCKS5, MTPROTO, UNKNOWN }

data class ProxyItem(
    val host: String,
    val port: Int,
    val user: String? = null,
    val pass: String? = null,
    val secret: String? = null,

    var protocol: ProxyProtocol = ProxyProtocol.UNKNOWN,
    var isAlive: Boolean = false,
    var pingMs: Long = -1,
    var speedKbps: Double = 0.0,
    var country: String = "Unknown",
    var isSelected: Boolean = false
) {
    // Генерация ссылки для Телеграма (Telegram поддерживает только MTProto и SOCKS5)
    fun toTgLink(): String {
        return when {
            protocol == ProxyProtocol.MTPROTO && secret != null ->
                "https://t.me/proxy?server=$host&port=$port&secret=$secret"

            protocol == ProxyProtocol.SOCKS5 -> {
                if (user != null && pass != null) {
                    "https://t.me/socks?server=$host&port=$port&user=$user&pass=$pass"
                } else {
                    "https://t.me/socks?server=$host&port=$port"
                }
            }
            else -> "" // HTTP, HTTPS, SOCKS4 не поддерживаются Telegram клиентами
        }
    }

    override fun toString(): String {
        return if (user != null && pass != null) {
            "$host:$port:$user:$pass"
        } else {
            "$host:$port"
        }
    }
}