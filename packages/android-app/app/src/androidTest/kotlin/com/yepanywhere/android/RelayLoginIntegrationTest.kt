package com.yepanywhere.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.SupervisorShellSnapshot
import com.yepanywhere.android.data.AndroidDataLayer
import com.yepanywhere.android.data.InMemoryRelayAuthStateStore
import com.yepanywhere.android.data.RelayCredentials
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelayLoginIntegrationTest {
    @Test
    fun relayLoginAndReconnectWithPersistedSession() = runBlocking {
        val relayUrl = BuildConfig.TEST_RELAY_URL.trim()
        val identity = BuildConfig.TEST_RELAY_IDENTITY.trim()
        val password = BuildConfig.TEST_RELAY_PASSWORD
        require(relayUrl.isNotBlank()) { "test.relay.url is missing" }
        require(identity.isNotBlank()) { "test.relay.identity is missing" }
        require(password.isNotBlank()) { "test.relay.password is missing" }

        val settings = AndroidRelayAuthSettings(
            mode = AndroidRelayAuthMode.RELAY,
            relayUrl = relayUrl,
            relayUsername = null,
        )
        val relayRunner = AndroidKtorRelayAuthRunner.createDefault()
        val handshakeExecutor = AndroidRelayAuthHandshakeExecutor(
            settings = settings,
            relayAuthRunner = relayRunner,
        )
        val stateStore = InMemoryRelayAuthStateStore()
        val firstLaunchDataLayer = AndroidDataLayer(
            relayAuthHandshake = handshakeExecutor::execute,
            relayAuthStateStore = stateStore,
        )

        firstLaunchDataLayer.login(
            RelayCredentials(
                relayUrl = relayUrl,
                username = identity,
                password = password,
            ),
        )

        val persisted = firstLaunchDataLayer.restorePersistedCredentials()
        assertEquals(relayUrl, persisted?.relayUrl)
        assertEquals(identity, persisted?.username)

        val secondLaunchDataLayer = AndroidDataLayer(
            relayAuthHandshake = handshakeExecutor::execute,
            relayAuthStateStore = stateStore,
        )
        val reconnected = secondLaunchDataLayer.reconnectPersistedSession()

        assertTrue("Persisted reconnect failed", reconnected)
        awaitConnectionStatus(
            snapshot = { secondLaunchDataLayer.shellState.value },
            expected = RelayConnectionStatus.CONNECTED,
        )
        assertEquals(
            RelayConnectionStatus.CONNECTED,
            secondLaunchDataLayer.shellState.value.connectionStatus,
        )
    }

    private suspend fun awaitConnectionStatus(
        snapshot: () -> SupervisorShellSnapshot,
        expected: RelayConnectionStatus,
        timeoutMs: Long = 15_000L,
    ) {
        withTimeout(timeoutMs) {
            while (snapshot().connectionStatus != expected) {
                delay(100)
            }
        }
    }
}
