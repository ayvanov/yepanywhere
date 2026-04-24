package com.yepanywhere.android.data

import com.yepanywhere.android.core.model.InboxItemKind
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.core.usecase.SecureRelayAuthHandshakeResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RelaySupervisorRuntimeTest {
    @Test
    fun loginUsesStoredSessionUsernameAsRoutingFallbackWhenConfigMissing() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = { username, _, relayUrl, _ ->
                SecureRelayAuthHandshakeResult(
                    session = RelaySession(
                        username = username,
                        relayUrl = relayUrl,
                        sessionId = "relay-session-1",
                    ),
                    persistedSession = StoredRelaySession(
                        wsUrl = relayUrl,
                        username = username,
                        sessionId = "relay-session-1",
                        sessionKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    ),
                    clearedStoredSession = false,
                    transportNonce = null,
                    resumed = false,
                )
            },
        )

        runtime.relayAuthRepository.login(
            username = "home-pc",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )

        assertEquals("home-pc", gateway.routingUsername)
    }

    @Test
    fun loginPrefersConfiguredRelayRoutingUsername() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            relayRoutingUsername = "relay-user",
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = { username, _, relayUrl, _ ->
                SecureRelayAuthHandshakeResult(
                    session = RelaySession(
                        username = username,
                        relayUrl = relayUrl,
                        sessionId = "relay-session-1",
                    ),
                    persistedSession = StoredRelaySession(
                        wsUrl = relayUrl,
                        username = username,
                        sessionId = "relay-session-1",
                        sessionKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    ),
                    clearedStoredSession = false,
                    transportNonce = null,
                    resumed = false,
                )
            },
        )

        runtime.relayAuthRepository.login(
            username = "home-pc",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )

        assertEquals("relay-user", gateway.routingUsername)
    }

    @Test
    fun loginLoadsProjectsSessionsInboxAndTimelineFromBackend() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = { username, _, relayUrl, _ ->
                SecureRelayAuthHandshakeResult(
                    session = RelaySession(
                        username = username,
                        relayUrl = relayUrl,
                        sessionId = "relay-session-1",
                    ),
                    persistedSession = StoredRelaySession(
                        wsUrl = relayUrl,
                        username = username,
                        sessionId = "relay-session-1",
                        sessionKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    ),
                    clearedStoredSession = false,
                    transportNonce = null,
                    resumed = false,
                )
            },
        )

        runtime.relayAuthRepository.login(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )

        val snapshot = runtime.shellState.value
        assertEquals(1, snapshot.projects.size)
        assertEquals("project-1", snapshot.projects.first().id)
        assertEquals(1, snapshot.sessions.size)
        assertEquals("session-1", snapshot.sessions.first().id)
        assertTrue(snapshot.sessions.none { it.id == "archived-session" })
        assertEquals(1, snapshot.inboxItems.size)
        assertEquals(InboxItemKind.APPROVAL, snapshot.inboxItems.first().kind)
        assertTrue(snapshot.timeline.messages.any { it.body.contains("real backend message") })
        assertEquals("request-1", snapshot.pendingRequests.first().id)
        assertEquals(RelayConnectionStatus.CONNECTED, snapshot.connectionStatus)
    }

    @Test
    fun approveRequestHitsBackendInputEndpoint() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = { username, _, relayUrl, _ ->
                SecureRelayAuthHandshakeResult(
                    session = RelaySession(
                        username = username,
                        relayUrl = relayUrl,
                        sessionId = "relay-session-1",
                    ),
                    persistedSession = StoredRelaySession(
                        wsUrl = relayUrl,
                        username = username,
                        sessionId = "relay-session-1",
                        sessionKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    ),
                    clearedStoredSession = false,
                    transportNonce = null,
                    resumed = false,
                )
            },
        )
        runtime.relayAuthRepository.login(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )

        runtime.approvalsRepository.approve("request-1")

        assertTrue(
            gateway.requests.any { request ->
                request.method == "POST" && request.path == "/sessions/session-1/input"
            },
        )
    }

    private class FakeRelayRealtimeGateway : RelayRealtimeGateway {
        data class RecordedRequest(
            val method: String,
            val path: String,
            val body: JsonElement?,
        )

        private val state = MutableStateFlow(RelayConnectionStatus.DISCONNECTED)
        private val events = MutableSharedFlow<RelayRealtimeEvent>()
        val requests = mutableListOf<RecordedRequest>()
        var routingUsername: String? = null

        override val connectionState: Flow<RelayConnectionStatus> = state

        override fun events(): Flow<RelayRealtimeEvent> = events

        override suspend fun connect(
            relayUrl: String,
            storedSession: StoredRelaySession,
            routingUsername: String?,
        ) {
            this.routingUsername = routingUsername
            state.value = RelayConnectionStatus.CONNECTED
        }

        override suspend fun disconnect() {
            state.value = RelayConnectionStatus.DISCONNECTED
        }

        override suspend fun ensureConnected() {
            if (state.value != RelayConnectionStatus.CONNECTED) {
                throw IllegalStateException("not_connected")
            }
        }

        override suspend fun request(
            method: String,
            path: String,
            body: JsonElement?,
        ): JsonElement {
            requests += RecordedRequest(method = method, path = path, body = body)
            return when (path) {
                "/projects" -> jsonObject(
                    "projects" to JsonArray(
                        listOf(
                            jsonObject(
                                "id" to JsonPrimitive("project-1"),
                                "name" to JsonPrimitive("Yep Anywhere"),
                                "activeOwnedCount" to JsonPrimitive(1),
                                "activeExternalCount" to JsonPrimitive(0),
                            ),
                        ),
                    ),
                )

                "/projects/project-1/sessions" -> jsonObject(
                    "sessions" to JsonArray(
                        listOf(
                            jsonObject(
                                "id" to JsonPrimitive("session-1"),
                                "projectId" to JsonPrimitive("project-1"),
                                "title" to JsonPrimitive("Android supervisor"),
                                "updatedAt" to JsonPrimitive("2026-04-23T12:00:00Z"),
                                "pendingInputType" to JsonPrimitive("tool-approval"),
                                "hasUnread" to JsonPrimitive(true),
                            ),
                            jsonObject(
                                "id" to JsonPrimitive("archived-session"),
                                "projectId" to JsonPrimitive("project-1"),
                                "title" to JsonPrimitive("Archived Android supervisor"),
                                "updatedAt" to JsonPrimitive("2026-04-23T11:00:00Z"),
                                "isArchived" to JsonPrimitive(true),
                                "hasUnread" to JsonPrimitive(false),
                            ),
                        ),
                    ),
                )

                "/inbox" -> jsonObject(
                    "needsAttention" to JsonArray(
                        listOf(
                            jsonObject(
                                "sessionId" to JsonPrimitive("session-1"),
                                "projectId" to JsonPrimitive("project-1"),
                                "projectName" to JsonPrimitive("Yep Anywhere"),
                                "sessionTitle" to JsonPrimitive("Android supervisor"),
                                "pendingInputType" to JsonPrimitive("tool-approval"),
                                "hasUnread" to JsonPrimitive(true),
                            ),
                        ),
                    ),
                    "active" to JsonArray(emptyList()),
                    "recentActivity" to JsonArray(emptyList()),
                    "unread8h" to JsonArray(emptyList()),
                    "unread24h" to JsonArray(emptyList()),
                )

                "/projects/project-1/sessions/session-1" -> jsonObject(
                    "messages" to JsonArray(
                        listOf(
                            jsonObject(
                                "id" to JsonPrimitive("msg-1"),
                                "type" to JsonPrimitive("assistant"),
                                "content" to JsonArray(
                                    listOf(
                                        jsonObject("type" to JsonPrimitive("text"), "text" to JsonPrimitive("real backend message")),
                                    ),
                                ),
                                "timestamp" to JsonPrimitive("2026-04-23T12:00:10Z"),
                            ),
                        ),
                    ),
                    "pendingInputRequest" to jsonObject(
                        "id" to JsonPrimitive("request-1"),
                        "type" to JsonPrimitive("tool-approval"),
                        "prompt" to JsonPrimitive("Approve command execution"),
                    ),
                )

                "/sessions/session-1/input" -> jsonObject("accepted" to JsonPrimitive(true))
                else -> JsonObject(emptyMap())
            }
        }

        override suspend fun subscribeSession(sessionId: String): String = "sub-session-$sessionId"

        override suspend fun subscribeActivity(): String = "sub-activity"

        override suspend fun unsubscribe(subscriptionId: String) = Unit
    }
}

private fun jsonObject(vararg entries: Pair<String, JsonElement>): JsonObject {
    return JsonObject(entries.toMap())
}
