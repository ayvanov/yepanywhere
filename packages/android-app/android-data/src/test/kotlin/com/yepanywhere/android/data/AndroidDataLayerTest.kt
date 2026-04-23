package com.yepanywhere.android.data

import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.core.usecase.SecureRelayAuthHandshakeResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidDataLayerTest {
    @Test
    fun loginPersistsCredentialsAndReconnectsWithStoredSession() = runTest(UnconfinedTestDispatcher()) {
        var callCount = 0
        var reconnectStoredSession: StoredRelaySession? = null
        val stateStore = InMemoryRelayAuthStateStore()
        val createDataLayer = {
            AndroidDataLayer(
                relayAuthStateStore = stateStore,
                relayAuthHandshake = { username, password, relayUrl, storedSession ->
                    callCount += 1
                    if (callCount == 2) {
                        reconnectStoredSession = storedSession
                    }
                    val sessionId = if (callCount == 1) "session-initial" else "session-resumed"
                    val sessionKey = if (callCount == 1) "persisted-session-key-1" else "persisted-session-key-2"
                    SecureRelayAuthHandshakeResult(
                        session = RelaySession(
                            username = username,
                            relayUrl = relayUrl,
                            sessionId = sessionId,
                        ),
                        persistedSession = StoredRelaySession(
                            wsUrl = relayUrl,
                            username = username,
                            sessionId = sessionId,
                            sessionKey = sessionKey,
                        ),
                        clearedStoredSession = false,
                        transportNonce = null,
                        resumed = callCount > 1,
                    )
                },
            )
        }
        val firstLaunchDataLayer = createDataLayer()

        firstLaunchDataLayer.login(
            RelayCredentials(
                relayUrl = "wss://relay.yepanywhere.local",
                username = "demo@yepanywhere",
                password = "secret",
            ),
        )

        assertEquals(
            RelayCredentials(
                relayUrl = "wss://relay.yepanywhere.local",
                username = "demo@yepanywhere",
                password = "secret",
            ),
            firstLaunchDataLayer.restorePersistedCredentials(),
        )

        val secondLaunchDataLayer = createDataLayer()
        val reconnected = secondLaunchDataLayer.reconnectPersistedSession()

        assertTrue(reconnected)
        assertEquals("session-initial", reconnectStoredSession?.sessionId)
        assertEquals(
            RelayCredentials(
                relayUrl = "wss://relay.yepanywhere.local",
                username = "demo@yepanywhere",
                password = "secret",
            ),
            secondLaunchDataLayer.restorePersistedCredentials(),
        )
    }

    @Test
    fun loginUsesInjectedRelayAuthHandshake() = runTest(UnconfinedTestDispatcher()) {
        var capturedUsername: String? = null
        var capturedPassword: String? = null
        var capturedRelayUrl: String? = null
        val dataLayer = AndroidDataLayer(
            relayAuthHandshake = { username, password, relayUrl, _ ->
                capturedUsername = username
                capturedPassword = password
                capturedRelayUrl = relayUrl
                SecureRelayAuthHandshakeResult(
                    session = RelaySession(
                        username = username,
                        relayUrl = relayUrl,
                        sessionId = "session-demo",
                    ),
                    persistedSession = null,
                    clearedStoredSession = false,
                    transportNonce = null,
                    resumed = false,
                )
            },
        )

        dataLayer.login(
            RelayCredentials(
                relayUrl = "wss://relay.yepanywhere.local",
                username = "demo@yepanywhere",
                password = "demo",
            ),
        )

        assertEquals("demo@yepanywhere", capturedUsername)
        assertEquals("demo", capturedPassword)
        assertEquals("wss://relay.yepanywhere.local", capturedRelayUrl)
    }

    @Test
    fun appliesAndClearsPendingInputNotifications() = runTest(UnconfinedTestDispatcher()) {
        val runtime = InMemorySupervisorRuntime(scope = backgroundScope)
        val dataLayer = AndroidDataLayer(runtime)

        dataLayer.applyPendingInputNotification(
            sessionId = "session-new",
            projectId = "project-new",
            projectName = "New Project",
            inputType = "tool-approval",
            summary = "Run: Bash",
            requestId = "request-new",
        )

        assertTrue(dataLayer.shellState.value.sessions.any { it.id == "session-new" && it.hasUnread })
        assertTrue(dataLayer.shellState.value.pendingRequests.any { it.id == "request-new" })

        dataLayer.clearSessionAttention("session-new")

        assertFalse(dataLayer.shellState.value.sessions.first { it.id == "session-new" }.hasUnread)
        assertFalse(dataLayer.shellState.value.pendingRequests.any { it.id == "request-new" })
    }
}
