package com.yepanywhere.android.core.usecase

private const val HTTP_PREFIX = "http://"
private const val HTTPS_PREFIX = "https://"
private const val WS_PREFIX = "ws://"
private const val WSS_PREFIX = "wss://"

fun resolveRelayWebSocketUrl(rawUrl: String): String {
    val normalized = rawUrl.trim()
    require(normalized.isNotEmpty()) { "relay_url_missing" }

    return when {
        normalized.startsWith(WS_PREFIX, ignoreCase = true) -> normalized.trimEnd('/')
        normalized.startsWith(WSS_PREFIX, ignoreCase = true) -> normalized.trimEnd('/')
        normalized.startsWith(HTTP_PREFIX, ignoreCase = true) -> {
            "$WS_PREFIX${normalized.removePrefix(HTTP_PREFIX).removePrefix(HTTP_PREFIX.uppercase())}".trimEnd('/')
        }

        normalized.startsWith(HTTPS_PREFIX, ignoreCase = true) -> {
            "$WSS_PREFIX${normalized.removePrefix(HTTPS_PREFIX).removePrefix(HTTPS_PREFIX.uppercase())}".trimEnd('/')
        }

        normalized.contains("://") -> throw IllegalArgumentException("relay_url_scheme_unsupported")
        else -> "$WSS_PREFIX${normalized.trimEnd('/')}"
    }
}
