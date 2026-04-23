package com.yepanywhere.android

import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.core.usecase.SecureRelayAuthHandshakeResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidRelayAuthHandshakeExecutorTest {
    @Test
    fun demoModeReturnsDeterministicHandshakeResult() = runTest {
        val executor = AndroidRelayAuthHandshakeExecutor(
            settings = AndroidRelayAuthSettings(
                mode = AndroidRelayAuthMode.DEMO,
                relayUrl = "wss://relay.yepanywhere.local",
                relayUsername = null,
            ),
        )

        val result = executor.execute(
            username = "demo@yepanywhere",
            password = "demo",
            relayUrl = "https://relay.yepanywhere.local",
            storedSession = null,
        )

        assertEquals(
            SecureRelayAuthHandshakeResult(
                session = RelaySession(
                    username = "demo@yepanywhere",
                    relayUrl = "wss://relay.yepanywhere.local/ws",
                    sessionId = "relay-session-demo",
                ),
                persistedSession = StoredRelaySession(
                    wsUrl = "wss://relay.yepanywhere.local/ws",
                    username = "demo@yepanywhere",
                    sessionId = "relay-session-demo",
                    sessionKey = "demo-session-key",
                ),
                clearedStoredSession = false,
                transportNonce = null,
                resumed = false,
            ),
            result,
        )
    }

    @Test
    fun relayModeDelegatesToRunnerWithResolvedUrl() = runTest {
        var capturedRelayUrl: String? = null
        var capturedRelayUsername: String? = null
        var capturedIdentity: String? = null
        var capturedPassword: String? = null
        var capturedStoredSession: StoredRelaySession? = null

        val executor = AndroidRelayAuthHandshakeExecutor(
            settings = AndroidRelayAuthSettings(
                mode = AndroidRelayAuthMode.RELAY,
                relayUrl = "wss://relay.yepanywhere.local",
                relayUsername = "relay-user",
            ),
            relayAuthRunner = AndroidRelayAuthRunner { relayUrl, relayUsername, identity, password, storedSession ->
                capturedRelayUrl = relayUrl
                capturedRelayUsername = relayUsername
                capturedIdentity = identity
                capturedPassword = password
                capturedStoredSession = storedSession
                SecureRelayAuthHandshakeResult(
                    session = RelaySession(
                        username = identity,
                        relayUrl = relayUrl,
                        sessionId = "session-real",
                    ),
                    persistedSession = null,
                    clearedStoredSession = false,
                    transportNonce = "nonce",
                    resumed = true,
                )
            },
        )

        val stored = StoredRelaySession(
            wsUrl = "wss://relay.yepanywhere.local",
            username = "demo@yepanywhere",
            sessionId = "session-old",
            sessionKey = "stored-key",
        )

        val result = executor.execute(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "https://relay.yepanywhere.local",
            storedSession = stored,
        )

        assertEquals("wss://relay.yepanywhere.local/ws", capturedRelayUrl)
        assertEquals("relay-user", capturedRelayUsername)
        assertEquals("demo@yepanywhere", capturedIdentity)
        assertEquals("secret", capturedPassword)
        assertEquals(stored, capturedStoredSession)
        assertEquals("session-real", result.session.sessionId)
        assertTrue(result.resumed)
    }

    @Test
    fun relayModeUsesIdentityAsRelayUsernameFallback() = runTest {
        var capturedRelayUsername: String? = null

        val executor = AndroidRelayAuthHandshakeExecutor(
            settings = AndroidRelayAuthSettings(
                mode = AndroidRelayAuthMode.RELAY,
                relayUrl = "wss://relay.yepanywhere.local",
                relayUsername = null,
            ),
            relayAuthRunner = AndroidRelayAuthRunner { relayUrl, relayUsername, identity, _, _ ->
                capturedRelayUsername = relayUsername
                SecureRelayAuthHandshakeResult(
                    session = RelaySession(
                        username = identity,
                        relayUrl = relayUrl,
                        sessionId = "session-real",
                    ),
                    persistedSession = null,
                    clearedStoredSession = false,
                    transportNonce = null,
                    resumed = false,
                )
            },
        )

        executor.execute(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
            storedSession = null,
        )

        assertEquals("demo@yepanywhere", capturedRelayUsername)
    }

    @Test
    fun relayModeWithoutRunnerFails() = runTest {
        val executor = AndroidRelayAuthHandshakeExecutor(
            settings = AndroidRelayAuthSettings(
                mode = AndroidRelayAuthMode.RELAY,
                relayUrl = "wss://relay.yepanywhere.local",
                relayUsername = "relay-user",
            ),
            relayAuthRunner = null,
        )

        val error = assertFailsWith<IllegalStateException> {
            executor.execute(
                username = "demo@yepanywhere",
                password = "secret",
                relayUrl = "",
                storedSession = null,
            )
        }

        assertEquals("relay_auth_runner_missing", error.message)
    }
}
