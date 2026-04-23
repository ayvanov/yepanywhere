package com.yepanywhere.android

import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.core.usecase.SecureRelayAuthHandshakeResult
import com.yepanywhere.android.core.usecase.resolveRelayWebSocketUrl

fun interface AndroidRelayAuthRunner {
    suspend fun run(
        relayUrl: String,
        relayUsername: String?,
        identity: String,
        password: String?,
        storedSession: StoredRelaySession?,
    ): SecureRelayAuthHandshakeResult
}

class AndroidRelayAuthHandshakeExecutor(
    private val settings: AndroidRelayAuthSettings,
    private val relayAuthRunner: AndroidRelayAuthRunner? = null,
) {
    suspend fun execute(
        username: String,
        password: String?,
        relayUrl: String,
        storedSession: StoredRelaySession?,
    ): SecureRelayAuthHandshakeResult {
        return when (settings.mode) {
            AndroidRelayAuthMode.DEMO -> {
                val sessionId = "relay-session-demo"
                val resolvedRelayUrl = resolveRelayWebSocketUrl(relayUrl)
                SecureRelayAuthHandshakeResult(
                    session = RelaySession(
                        username = username,
                        relayUrl = resolvedRelayUrl,
                        sessionId = sessionId,
                    ),
                    persistedSession = StoredRelaySession(
                        wsUrl = resolvedRelayUrl,
                        username = username,
                        sessionId = sessionId,
                        sessionKey = "demo-session-key",
                    ),
                    clearedStoredSession = false,
                    transportNonce = null,
                    resumed = false,
                )
            }

            AndroidRelayAuthMode.RELAY -> {
                val runner = relayAuthRunner ?: error("relay_auth_runner_missing")
                val targetRelayUrl = resolveRelayWebSocketUrl(
                    if (relayUrl.isBlank()) settings.relayUrl else relayUrl,
                )
                runner.run(
                    relayUrl = targetRelayUrl,
                    relayUsername = settings.relayUsername ?: username,
                    identity = username,
                    password = password,
                    storedSession = storedSession,
                )
            }
        }
    }
}
