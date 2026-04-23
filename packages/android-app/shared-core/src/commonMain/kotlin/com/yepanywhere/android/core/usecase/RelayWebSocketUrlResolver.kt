package com.yepanywhere.android.core.usecase

private const val HTTP_PREFIX = "http://"
private const val HTTPS_PREFIX = "https://"
private const val WS_PREFIX = "ws://"
private const val WSS_PREFIX = "wss://"

fun resolveRelayWebSocketUrl(rawUrl: String): String {
    val normalized = rawUrl.trim()
    require(normalized.isNotEmpty()) { "relay_url_missing" }

    val wsUrl = when {
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

    return ensureDefaultWebSocketPath(wsUrl)
}

private fun ensureDefaultWebSocketPath(wsUrl: String): String {
    val schemeSeparator = wsUrl.indexOf("://")
    if (schemeSeparator < 0) {
        return wsUrl
    }
    val authorityStart = schemeSeparator + 3
    val pathStart = wsUrl.indexOf('/', authorityStart)
    val queryStart = wsUrl.indexOf('?', authorityStart).takeIf { it >= 0 }
    val fragmentStart = wsUrl.indexOf('#', authorityStart).takeIf { it >= 0 }
    val suffixStart = sequenceOf(queryStart, fragmentStart).filterNotNull().minOrNull()

    return when {
        pathStart >= 0 && (suffixStart == null || pathStart < suffixStart) -> wsUrl
        suffixStart != null -> "${wsUrl.substring(0, suffixStart)}/ws${wsUrl.substring(suffixStart)}"
        else -> "$wsUrl/ws"
    }
}
