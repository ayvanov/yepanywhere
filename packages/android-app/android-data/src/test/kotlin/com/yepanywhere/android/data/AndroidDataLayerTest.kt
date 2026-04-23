package com.yepanywhere.android.data

import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.core.usecase.SecureRelayAuthHandshakeResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidDataLayerTest {
    @Test
    fun loginDoesNotWaitForInitialBackendRefresh() = runTest(UnconfinedTestDispatcher()) {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseRequests = CompletableDeferred<Unit>()
        val dataLayer = AndroidDataLayer(
            relayRealtimeGatewayOverride = SlowRelayRealtimeGateway(
                requestStarted = requestStarted,
                releaseRequests = releaseRequests,
            ),
            relayAuthHandshake = { username, _, relayUrl, _ ->
                SecureRelayAuthHandshakeResult(
                    session = RelaySession(
                        username = username,
                        relayUrl = relayUrl,
                        sessionId = "session-demo",
                    ),
                    persistedSession = StoredRelaySession(
                        wsUrl = relayUrl,
                        username = username,
                        sessionId = "session-demo",
                        sessionKey = "session-key",
                    ),
                    clearedStoredSession = false,
                    transportNonce = null,
                    resumed = false,
                )
            },
        )

        val loginJob = backgroundScope.async {
            dataLayer.login(
                RelayCredentials(
                    relayUrl = "wss://relay.yepanywhere.local",
                    username = "demo@yepanywhere",
                    password = "secret",
                ),
            )
        }

        withTimeout(1_000) { requestStarted.await() }

        assertTrue(loginJob.isCompleted)

        releaseRequests.complete(Unit)
        loginJob.await()
    }

    @Test
    fun reconnectDoesNotWaitForInitialBackendRefresh() = runTest(UnconfinedTestDispatcher()) {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseRequests = CompletableDeferred<Unit>()
        val stateStore = InMemoryRelayAuthStateStore(
            PersistedRelayAuthState(
                credentials = RelayCredentials(
                    relayUrl = "wss://relay.yepanywhere.local",
                    username = "demo@yepanywhere",
                    password = "",
                ),
                storedSession = StoredRelaySession(
                    wsUrl = "wss://relay.yepanywhere.local",
                    username = "demo@yepanywhere",
                    sessionId = "session-persisted",
                    sessionKey = "session-key",
                ),
            ),
        )
        val dataLayer = AndroidDataLayer(
            relayAuthStateStore = stateStore,
            relayRealtimeGatewayOverride = SlowRelayRealtimeGateway(
                requestStarted = requestStarted,
                releaseRequests = releaseRequests,
            ),
            relayAuthHandshake = { username, _, relayUrl, storedSession ->
                assertEquals("session-persisted", storedSession?.sessionId)
                SecureRelayAuthHandshakeResult(
                    session = RelaySession(
                        username = username,
                        relayUrl = relayUrl,
                        sessionId = "session-resumed",
                    ),
                    persistedSession = StoredRelaySession(
                        wsUrl = relayUrl,
                        username = username,
                        sessionId = "session-resumed",
                        sessionKey = "session-key-2",
                    ),
                    clearedStoredSession = false,
                    transportNonce = null,
                    resumed = true,
                )
            },
        )

        val reconnectJob = backgroundScope.async {
            dataLayer.reconnectPersistedSession()
        }

        withTimeout(1_000) { requestStarted.await() }

        assertTrue(reconnectJob.isCompleted)

        releaseRequests.complete(Unit)
        assertTrue(reconnectJob.await())
    }

    @Test
    fun loginTimesOutWhenRealtimeConnectStalls() = runTest {
        val dataLayer = AndroidDataLayer(
            relayRealtimeGatewayOverride = HangingConnectRelayRealtimeGateway(),
            relayAuthHandshake = { username, _, relayUrl, _ ->
                SecureRelayAuthHandshakeResult(
                    session = RelaySession(
                        username = username,
                        relayUrl = relayUrl,
                        sessionId = "session-demo",
                    ),
                    persistedSession = StoredRelaySession(
                        wsUrl = relayUrl,
                        username = username,
                        sessionId = "session-demo",
                        sessionKey = "session-key",
                    ),
                    clearedStoredSession = false,
                    transportNonce = null,
                    resumed = false,
                )
            },
        )

        val loginJob = async {
            runCatching {
                dataLayer.login(
                    RelayCredentials(
                        relayUrl = "relay.yepanywhere.com",
                        username = "home-pc",
                        password = "secret",
                    ),
                )
            }
        }

        advanceTimeBy(59_999)
        runCurrent()
        assertFalse(loginJob.isCompleted)

        advanceTimeBy(1)
        runCurrent()

        assertTrue(loginJob.isCompleted)
        assertEquals(
            "Login timed out after 60 seconds",
            loginJob.await().exceptionOrNull()?.message,
        )
    }

    @Test
    fun reconnectReturnsFalseWhenRealtimeConnectStalls() = runTest {
        val stateStore = InMemoryRelayAuthStateStore(
            PersistedRelayAuthState(
                credentials = RelayCredentials(
                    relayUrl = "relay.yepanywhere.com",
                    username = "home-pc",
                    password = "",
                ),
                storedSession = StoredRelaySession(
                    wsUrl = "relay.yepanywhere.com",
                    username = "home-pc",
                    sessionId = "session-persisted",
                    sessionKey = "session-key",
                ),
            ),
        )
        val dataLayer = AndroidDataLayer(
            relayAuthStateStore = stateStore,
            relayRealtimeGatewayOverride = HangingConnectRelayRealtimeGateway(),
            relayAuthHandshake = { username, _, relayUrl, storedSession ->
                assertEquals("session-persisted", storedSession?.sessionId)
                SecureRelayAuthHandshakeResult(
                    session = RelaySession(
                        username = username,
                        relayUrl = relayUrl,
                        sessionId = "session-resumed",
                    ),
                    persistedSession = StoredRelaySession(
                        wsUrl = relayUrl,
                        username = username,
                        sessionId = "session-resumed",
                        sessionKey = "session-key-2",
                    ),
                    clearedStoredSession = false,
                    transportNonce = null,
                    resumed = true,
                )
            },
        )

        val reconnectJob = async {
            dataLayer.reconnectPersistedSession()
        }

        advanceTimeBy(59_999)
        runCurrent()
        assertFalse(reconnectJob.isCompleted)

        advanceTimeBy(1)
        runCurrent()

        assertTrue(reconnectJob.isCompleted)
        assertFalse(reconnectJob.await())
    }

    @Test
    fun loginPersistsCredentialsAndReconnectsWithStoredSession() = runTest(UnconfinedTestDispatcher()) {
        var callCount = 0
        var reconnectStoredSession: StoredRelaySession? = null
        val stateStore = InMemoryRelayAuthStateStore()
        val relayGateway = FakeRelayRealtimeGateway()
        val createDataLayer = {
            AndroidDataLayer(
                relayAuthStateStore = stateStore,
                relayRealtimeGatewayOverride = relayGateway,
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
            relayRealtimeGatewayOverride = FakeRelayRealtimeGateway(),
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
    fun logoutKeepsRelayUrlAndIdentityForFutureLogin() = runTest(UnconfinedTestDispatcher()) {
        var handshakeCalls = 0
        val credentials = RelayCredentials(
            relayUrl = "wss://relay.yepanywhere.local",
            username = "demo@yepanywhere",
            password = "demo-password",
        )
        val dataLayer = AndroidDataLayer(
            relayAuthStateStore = InMemoryRelayAuthStateStore(),
            relayRealtimeGatewayOverride = FakeRelayRealtimeGateway(),
            relayAuthHandshake = { username, _, relayUrl, _ ->
                handshakeCalls += 1
                SecureRelayAuthHandshakeResult(
                    session = RelaySession(
                        username = username,
                        relayUrl = relayUrl,
                        sessionId = "session-demo",
                    ),
                    persistedSession = StoredRelaySession(
                        wsUrl = relayUrl,
                        username = username,
                        sessionId = "session-demo",
                        sessionKey = "session-key",
                    ),
                    clearedStoredSession = false,
                    transportNonce = null,
                    resumed = false,
                )
            },
        )

        dataLayer.login(credentials)
        dataLayer.logout()

        assertEquals(
            RelayCredentials(
                relayUrl = credentials.relayUrl,
                username = credentials.username,
                password = "",
            ),
            dataLayer.restorePersistedCredentials(),
        )

        val reconnected = dataLayer.reconnectPersistedSession()
        assertFalse(reconnected)
        assertEquals(1, handshakeCalls)
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

    private class FakeRelayRealtimeGateway : RelayRealtimeGateway {
        private val connection = MutableStateFlow(RelayConnectionStatus.DISCONNECTED)
        private val events = MutableSharedFlow<RelayRealtimeEvent>()

        override val connectionState: Flow<RelayConnectionStatus> = connection

        override fun events(): Flow<RelayRealtimeEvent> = events

        override suspend fun connect(
            relayUrl: String,
            storedSession: StoredRelaySession,
            routingUsername: String?,
        ) {
            connection.value = RelayConnectionStatus.CONNECTED
        }

        override suspend fun disconnect() {
            connection.value = RelayConnectionStatus.DISCONNECTED
        }

        override suspend fun ensureConnected() {
            if (connection.value != RelayConnectionStatus.CONNECTED) {
                throw IllegalStateException("not_connected")
            }
        }

        override suspend fun request(
            method: String,
            path: String,
            body: JsonElement?,
        ): JsonElement {
            return when (path) {
                "/api/projects" -> JsonObject(mapOf("projects" to JsonArray(emptyList())))
                "/api/inbox" -> JsonObject(
                    mapOf(
                        "needsAttention" to JsonArray(emptyList()),
                        "active" to JsonArray(emptyList()),
                        "recentActivity" to JsonArray(emptyList()),
                        "unread8h" to JsonArray(emptyList()),
                        "unread24h" to JsonArray(emptyList()),
                    ),
                )

                else -> JsonObject(emptyMap())
            }
        }

        override suspend fun subscribeSession(sessionId: String): String = "session-$sessionId"

        override suspend fun subscribeActivity(): String = "activity"

        override suspend fun unsubscribe(subscriptionId: String) = Unit
    }

    private class SlowRelayRealtimeGateway(
        private val requestStarted: CompletableDeferred<Unit>,
        private val releaseRequests: CompletableDeferred<Unit>,
    ) : RelayRealtimeGateway {
        private val connection = MutableStateFlow(RelayConnectionStatus.DISCONNECTED)
        private val events = MutableSharedFlow<RelayRealtimeEvent>()

        override val connectionState: Flow<RelayConnectionStatus> = connection

        override fun events(): Flow<RelayRealtimeEvent> = events

        override suspend fun connect(
            relayUrl: String,
            storedSession: StoredRelaySession,
            routingUsername: String?,
        ) {
            connection.value = RelayConnectionStatus.CONNECTED
        }

        override suspend fun disconnect() {
            connection.value = RelayConnectionStatus.DISCONNECTED
        }

        override suspend fun ensureConnected() {
            if (connection.value != RelayConnectionStatus.CONNECTED) {
                throw IllegalStateException("not_connected")
            }
        }

        override suspend fun request(
            method: String,
            path: String,
            body: JsonElement?,
        ): JsonElement {
            requestStarted.complete(Unit)
            releaseRequests.await()
            return JsonObject(emptyMap())
        }

        override suspend fun subscribeSession(sessionId: String): String = "session-$sessionId"

        override suspend fun subscribeActivity(): String = "activity"

        override suspend fun unsubscribe(subscriptionId: String) = Unit
    }

    private class HangingConnectRelayRealtimeGateway : RelayRealtimeGateway {
        private val connection = MutableStateFlow(RelayConnectionStatus.DISCONNECTED)
        private val events = MutableSharedFlow<RelayRealtimeEvent>()

        override val connectionState: Flow<RelayConnectionStatus> = connection

        override fun events(): Flow<RelayRealtimeEvent> = events

        override suspend fun connect(
            relayUrl: String,
            storedSession: StoredRelaySession,
            routingUsername: String?,
        ) {
            connection.value = RelayConnectionStatus.CONNECTING
            awaitCancellation()
        }

        override suspend fun disconnect() {
            connection.value = RelayConnectionStatus.DISCONNECTED
        }

        override suspend fun ensureConnected() {
            if (connection.value != RelayConnectionStatus.CONNECTED) {
                throw IllegalStateException("not_connected")
            }
        }

        override suspend fun request(
            method: String,
            path: String,
            body: JsonElement?,
        ): JsonElement {
            return JsonObject(emptyMap())
        }

        override suspend fun subscribeSession(sessionId: String): String = "session-$sessionId"

        override suspend fun subscribeActivity(): String = "activity"

        override suspend fun unsubscribe(subscriptionId: String) = Unit
    }
}
