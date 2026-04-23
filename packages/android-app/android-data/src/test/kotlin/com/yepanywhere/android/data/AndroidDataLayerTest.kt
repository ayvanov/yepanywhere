package com.yepanywhere.android.data

import com.yepanywhere.android.core.model.RelaySession
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
    fun connectDemoSessionUsesInjectedRelayAuthHandshake() = runTest(UnconfinedTestDispatcher()) {
        var called = false
        var capturedUsername: String? = null
        var capturedPassword: String? = null
        var capturedRelayUrl: String? = null
        val dataLayer = AndroidDataLayer(
            relayAuthHandshake = { username, password, relayUrl, _ ->
                called = true
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

        dataLayer.connectDemoSession()

        assertTrue(called)
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
