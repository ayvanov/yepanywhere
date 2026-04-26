package com.yepanywhere.android.data

import com.yepanywhere.android.core.model.InboxItemKind
import com.yepanywhere.android.core.model.GlobalSessionFilters
import com.yepanywhere.android.core.model.MessageContentBlock
import com.yepanywhere.android.core.model.NewSessionDefaults
import com.yepanywhere.android.core.model.NewSessionOptions
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.SessionAttachment
import com.yepanywhere.android.core.model.SessionDetailQuery
import com.yepanywhere.android.core.model.SessionInputRequest
import com.yepanywhere.android.core.model.SessionMetadataUpdate
import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.core.usecase.SecureRelayAuthHandshakeResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        assertEquals(3, snapshot.projects.size)
        assertEquals("project-1", snapshot.projects.first().id)
        assertEquals(1, snapshot.projects.first().activeCount)
        assertEquals(1, snapshot.projects.first().thinkingCount)
        assertEquals(2, snapshot.projects.first().needsAttentionCount)
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
    fun projectsAreSortedByAttentionThenRecentActivity() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = successfulHandshake(),
        )

        runtime.relayAuthRepository.login(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )

        assertEquals(
            listOf("project-1", "project-2", "project-3"),
            runtime.shellState.value.projects.map { it.id },
        )
    }

    @Test
    fun addProjectPostsPathAndCachesReturnedProject() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = successfulHandshake(),
        )
        runtime.relayAuthRepository.login(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )

        val added = runtime.projectsRepository.addProject("~/code/new-project")

        assertEquals("project-added", added.id)
        assertTrue(runtime.projectsRepository.observeProjects().first().any { it.id == "project-added" })
        assertTrue(
            gateway.requests.any { request ->
                request.method == "POST" && request.path == "/projects"
            },
        )
    }

    @Test
    fun getProjectFetchesProjectDetailById() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = successfulHandshake(),
        )
        runtime.relayAuthRepository.login(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )

        val project = runtime.projectsRepository.getProject("project-1")

        assertEquals("Yep Anywhere", project.name)
        assertEquals("/repo/yepanywhere", project.path)
    }

    @Test
    fun loadGlobalSessionsUsesFiltersPaginationAndMapsMetadata() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = successfulHandshake(),
        )
        runtime.relayAuthRepository.login(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )

        val page = runtime.sessionsRepository.loadGlobalSessions(
            filters = GlobalSessionFilters(
                project = "project-1",
                query = "android",
                status = "running",
                provider = "claude",
                executor = "local",
                age = "24h",
                includeArchived = true,
                starred = true,
            ),
            after = "cursor-1",
            limit = 2,
        )

        assertEquals(1, page.sessions.size)
        val session = page.sessions.first()
        assertEquals("global-1", session.id)
        assertEquals("project-1", session.projectId)
        assertEquals("Claude supervisor", session.title)
        assertEquals("claude", session.provider)
        assertEquals("local", session.executor)
        assertTrue(session.isStarred)
        assertFalse(session.isArchived)
        assertTrue(page.hasMore)
        assertEquals("cursor-2", page.nextAfter)
        assertEquals(12, page.stats.total)
        assertTrue(
            gateway.requests.any { request ->
                request.method == "GET" &&
                    request.path.startsWith("/sessions?") &&
                    request.path.contains("project=project-1") &&
                    request.path.contains("q=android") &&
                    request.path.contains("status=running") &&
                    request.path.contains("provider=claude") &&
                    request.path.contains("executor=local") &&
                    request.path.contains("age=24h") &&
                    request.path.contains("after=cursor-1") &&
                    request.path.contains("limit=2") &&
                    request.path.contains("includeArchived=true") &&
                    request.path.contains("starred=true")
            },
        )
    }

    @Test
    fun sessionMetadataAndReadStateActionsHitBackendAndUpdateCache() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = successfulHandshake(),
        )
        runtime.relayAuthRepository.login(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )
        runtime.sessionsRepository.loadGlobalSessions()

        runtime.sessionsRepository.updateSessionMetadata(
            sessionId = "global-1",
            updates = SessionMetadataUpdate(
                archived = true,
                starred = true,
            ),
        )
        runtime.sessionsRepository.markSessionSeen(
            sessionId = "global-1",
            timestamp = "2026-04-26T12:00:00Z",
            messageId = "msg-1",
        )
        runtime.sessionsRepository.markSessionUnread(sessionId = "global-1")

        assertEquals(
            jsonObject(
                "archived" to JsonPrimitive(true),
                "starred" to JsonPrimitive(true),
            ),
            gateway.recordedRequest("PUT", "/sessions/global-1/metadata").body,
        )
        assertEquals(
            jsonObject(
                "timestamp" to JsonPrimitive("2026-04-26T12:00:00Z"),
                "messageId" to JsonPrimitive("msg-1"),
            ),
            gateway.recordedRequest("POST", "/sessions/global-1/mark-seen").body,
        )
        assertTrue(gateway.requests.any { it.method == "DELETE" && it.path == "/sessions/global-1/mark-seen" })
        assertTrue(runtime.sessionsRepository.observeSessions().first().first { it.id == "global-1" }.hasUnread)
    }

    @Test
    fun newSessionSettingsRoundTripThroughServerSettings() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = successfulHandshake(),
        )
        runtime.relayAuthRepository.login(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )

        val settings = runtime.sessionsRepository.getNewSessionSettings()
        runtime.sessionsRepository.saveNewSessionDefaults(
            NewSessionDefaults(
                provider = "codex",
                model = "gpt-5.2",
                permissionMode = "acceptEdits",
                thinking = "enabled",
                executor = "build-host",
            ),
        )

        assertEquals(listOf("local", "build-host"), settings.remoteExecutors)
        assertEquals("claude", settings.defaults.provider)
        assertEquals("sonnet", settings.defaults.model)
        assertEquals(
            jsonObject(
                "newSessionDefaults" to jsonObject(
                    "provider" to JsonPrimitive("codex"),
                    "model" to JsonPrimitive("gpt-5.2"),
                    "permissionMode" to JsonPrimitive("acceptEdits"),
                    "thinking" to jsonObject("type" to JsonPrimitive("enabled")),
                    "executor" to JsonPrimitive("build-host"),
                ),
            ),
            gateway.recordedRequest("PUT", "/settings").body,
        )
    }

    @Test
    fun startAndTwoPhaseNewSessionHitBackendWithSelectedOptions() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = successfulHandshake(),
        )
        runtime.relayAuthRepository.login(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )
        val options = NewSessionOptions(
            provider = "claude",
            model = "opus",
            permissionMode = "bypassPermissions",
            thinking = "enabled",
            executor = "build-host",
        )

        val direct = runtime.sessionsRepository.startSession(
            projectId = "project-1",
            prompt = "Build Android parity",
            options = options,
        )
        val created = runtime.sessionsRepository.createSession(
            projectId = "project-1",
            options = options,
        )
        runtime.sessionsRepository.queueMessage(
            sessionId = created.sessionId,
            prompt = "Upload-free follow-up",
            options = options,
        )

        assertEquals("new-session-direct", direct.sessionId)
        assertEquals("new-session-created", created.sessionId)
        assertEquals(
            jsonObject(
                "message" to JsonPrimitive("Build Android parity"),
                "mode" to JsonPrimitive("bypassPermissions"),
                "model" to JsonPrimitive("opus"),
                "thinking" to jsonObject("type" to JsonPrimitive("enabled")),
                "provider" to JsonPrimitive("claude"),
                "executor" to JsonPrimitive("build-host"),
            ),
            gateway.recordedRequest("POST", "/projects/project-1/sessions").body,
        )
        assertEquals(
            jsonObject(
                "mode" to JsonPrimitive("bypassPermissions"),
                "model" to JsonPrimitive("opus"),
                "thinking" to jsonObject("type" to JsonPrimitive("enabled")),
                "provider" to JsonPrimitive("claude"),
                "executor" to JsonPrimitive("build-host"),
            ),
            gateway.recordedRequest("POST", "/projects/project-1/sessions/create").body,
        )
        assertEquals(
            jsonObject(
                "message" to JsonPrimitive("Upload-free follow-up"),
                "mode" to JsonPrimitive("bypassPermissions"),
                "thinking" to jsonObject("type" to JsonPrimitive("enabled")),
            ),
            gateway.recordedRequest("POST", "/sessions/new-session-created/messages").body,
        )
    }

    @Test
    fun loadSessionDetailUsesProjectRouteAndMapsMetadataPaginationAndCommands() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = successfulHandshake(),
        )
        runtime.relayAuthRepository.login(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )

        val detail = runtime.sessionsRepository.loadSessionDetail(
            projectId = "project-1",
            sessionId = "session-detail",
            query = SessionDetailQuery(
                afterMessageId = "msg-1",
                beforeMessageId = "msg-0",
                tailCompactions = 2,
            ),
        )

        assertEquals("session-detail", detail.session.id)
        assertEquals("project-1", detail.session.projectId)
        assertEquals("claude", detail.session.provider)
        assertEquals("opus", detail.session.model)
        assertEquals("self", detail.ownership)
        assertEquals("process-1", detail.processId)
        assertEquals("waiting-input", detail.processState)
        assertEquals("acceptEdits", detail.permissionMode)
        assertEquals(2, detail.modeVersion)
        assertEquals("request-detail", detail.pendingInputRequest?.id)
        assertEquals(listOf("/compact", "/model"), detail.slashCommands.map { it.name })
        assertEquals(true, detail.pagination?.hasOlderMessages)
        assertEquals(42, detail.pagination?.totalMessageCount)
        assertEquals(listOf("msg-1", "msg-2"), detail.timeline.messages.map { it.id })
        assertEquals(
            listOf(
                MessageContentBlock.Text("hello"),
                MessageContentBlock.Thinking("checking project state"),
                MessageContentBlock.ToolUse(
                    name = "Bash",
                    input = "npm test",
                    callId = "tool-1",
                ),
                MessageContentBlock.ToolResult(
                    content = "all tests passed",
                    toolUseId = "tool-1",
                ),
            ),
            detail.timeline.messages.first().blocks,
        )
        assertTrue(
            gateway.requests.any { request ->
                request.method == "GET" &&
                    request.path == "/projects/project-1/sessions/session-detail?afterMessageId=msg-1&beforeMessageId=msg-0&tailCompactions=2"
            },
        )
    }

    @Test
    fun loadSessionMetadataUsesLightweightMetadataEndpoint() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = successfulHandshake(),
        )
        runtime.relayAuthRepository.login(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )

        val metadata = runtime.sessionsRepository.loadSessionMetadata(
            projectId = "project-1",
            sessionId = "session-detail",
        )

        assertEquals("session-detail", metadata.session.id)
        assertEquals("metadata title", metadata.session.title)
        assertEquals("external", metadata.ownership)
        assertEquals("idle", metadata.processState)
        assertEquals("sonnet", metadata.session.model)
        assertEquals(listOf("/help"), metadata.slashCommands.map { it.name })
        assertTrue(
            gateway.requests.any { request ->
                request.method == "GET" &&
                    request.path == "/projects/project-1/sessions/session-detail/metadata"
            },
        )
    }

    @Test
    fun sessionInputCommandsUseDeferredAttachmentHoldAndProcessEndpoints() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = successfulHandshake(),
        )
        runtime.relayAuthRepository.login(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )

        runtime.sessionsRepository.queueSessionInput(
            sessionId = "session-detail",
            request = SessionInputRequest(
                message = "queued from android",
                mode = "acceptEdits",
                thinking = "enabled",
                attachments = listOf(
                    SessionAttachment(
                        id = "upload-1",
                        name = "trace.log",
                        sizeBytes = 1_024,
                        mimeType = "text/plain",
                    ),
                ),
                tempId = "android-1",
                deferred = true,
            ),
        )
        runtime.sessionsRepository.cancelDeferredMessage("session-detail", "android-1")
        runtime.sessionsRepository.setSessionHold("session-detail", true)
        val interrupted = runtime.sessionsRepository.interruptProcess("process-1")
        val aborted = runtime.sessionsRepository.abortProcess("process-1")

        assertEquals(
            jsonObject(
                "message" to JsonPrimitive("queued from android"),
                "mode" to JsonPrimitive("acceptEdits"),
                "thinking" to jsonObject("type" to JsonPrimitive("enabled")),
                "tempId" to JsonPrimitive("android-1"),
                "attachments" to JsonArray(
                    listOf(
                        jsonObject(
                            "id" to JsonPrimitive("upload-1"),
                            "originalName" to JsonPrimitive("trace.log"),
                            "name" to JsonPrimitive("trace.log"),
                            "path" to JsonPrimitive("upload-1"),
                            "size" to JsonPrimitive(1_024L),
                            "mimeType" to JsonPrimitive("text/plain"),
                        ),
                    ),
                ),
                "deferred" to JsonPrimitive(true),
            ),
            gateway.recordedRequest("POST", "/sessions/session-detail/messages").body,
        )
        assertEquals("DELETE", gateway.recordedRequest("DELETE", "/sessions/session-detail/deferred/android-1").method)
        assertEquals(
            jsonObject("hold" to JsonPrimitive(true)),
            gateway.recordedRequest("PUT", "/sessions/session-detail/hold").body,
        )
        assertEquals(true, interrupted.success)
        assertEquals(true, interrupted.supported)
        assertEquals(true, aborted)
    }

    @Test
    fun processModelControlsUseBackendEndpoints() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = successfulHandshake(),
        )
        runtime.relayAuthRepository.login(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )

        val processInfo = runtime.sessionsRepository.getProcessInfo("session-detail")
        val models = runtime.sessionsRepository.getProcessModels("process-1")
        val switchResult = runtime.sessionsRepository.setProcessModel("process-1", "opus")

        assertEquals("process-1", processInfo?.id)
        assertEquals("claude", processInfo?.provider)
        assertEquals("sonnet", processInfo?.model)
        assertEquals(listOf("sonnet", "opus"), models.map { it.id })
        assertEquals(true, switchResult.success)
        assertEquals("opus", switchResult.model)
        assertEquals("GET", gateway.recordedRequest("GET", "/sessions/session-detail/process").method)
        assertEquals("GET", gateway.recordedRequest("GET", "/processes/process-1/models").method)
        assertEquals(
            jsonObject("model" to JsonPrimitive("opus")),
            gateway.recordedRequest("POST", "/processes/process-1/model").body,
        )
    }

    @Test
    fun agentsPageAndSubagentFetchUseBackendEndpoints() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = successfulHandshake(),
        )
        runtime.relayAuthRepository.login(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )

        val processes = runtime.sessionsRepository.loadAgentProcesses(includeTerminated = true)
        val mappings = runtime.sessionsRepository.loadAgentMappings(
            projectId = "project-1",
            sessionId = "session-detail",
        )
        val agentSession = runtime.sessionsRepository.loadAgentSession(
            projectId = "project-1",
            sessionId = "session-detail",
            agentId = "agent-1",
        )

        assertEquals(listOf("process-1"), processes.processes.map { it.id })
        assertEquals(listOf("process-old"), processes.terminatedProcesses.map { it.id })
        assertEquals("agent-1", mappings.single().agentId)
        assertEquals("tool-1", mappings.single().toolUseId)
        assertEquals("completed", agentSession?.status)
        assertEquals(listOf("agent-msg-1"), agentSession?.messages?.map { it.id })
        assertEquals("GET", gateway.recordedRequest("GET", "/processes?includeTerminated=true").method)
        assertEquals(
            "GET",
            gateway.recordedRequest("GET", "/projects/project-1/sessions/session-detail/agents").method,
        )
        assertEquals(
            "GET",
            gateway.recordedRequest("GET", "/projects/project-1/sessions/session-detail/agents/agent-1").method,
        )
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

        val request = gateway.recordedRequest("POST", "/sessions/session-1/input")
        assertEquals(
            jsonObject(
                "requestId" to JsonPrimitive("request-1"),
                "response" to JsonPrimitive("approve"),
            ),
            request.body,
        )
    }

    @Test
    fun approveAcceptEditsRequestUsesWebInputResponseShape() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeRelayRealtimeGateway()
        val runtime = RelaySupervisorRuntime(
            scope = backgroundScope,
            realtimeGatewayOverride = gateway,
            relayAuthHandshake = successfulHandshake(),
        )
        runtime.relayAuthRepository.login(
            username = "demo@yepanywhere",
            password = "secret",
            relayUrl = "wss://relay.yepanywhere.local",
        )

        runtime.approvalsRepository.approveAcceptEdits("request-1")

        val request = gateway.recordedRequest("POST", "/sessions/session-1/input")
        assertEquals(
            jsonObject(
                "requestId" to JsonPrimitive("request-1"),
                "response" to JsonPrimitive("approve_accept_edits"),
            ),
            request.body,
        )
    }

    private fun successfulHandshake(): suspend (String, String?, String, StoredRelaySession?) -> SecureRelayAuthHandshakeResult {
        return { username, _, relayUrl, _ ->
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
        }
    }

    @Test
    fun sendReplyHitsBackendMessagesEndpointWithPayload() = runTest(UnconfinedTestDispatcher()) {
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

        runtime.sessionsRepository.sendReply(
            sessionId = "session-1",
            text = "Reply from Android",
        )

        val request = gateway.recordedRequest("POST", "/sessions/session-1/messages")
        assertEquals(
            jsonObject("message" to JsonPrimitive("Reply from Android")),
            request.body,
        )
    }

    @Test
    fun denyRequestHitsBackendInputEndpointWithPayload() = runTest(UnconfinedTestDispatcher()) {
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

        runtime.approvalsRepository.deny(
            requestId = "request-1",
            feedback = "Needs tests first",
        )

        val request = gateway.recordedRequest("POST", "/sessions/session-1/input")
        assertEquals(
            jsonObject(
                "requestId" to JsonPrimitive("request-1"),
                "response" to JsonPrimitive("deny"),
                "feedback" to JsonPrimitive("Needs tests first"),
            ),
            request.body,
        )
    }

    @Test
    fun answerQuestionHitsBackendInputEndpointWithPayload() = runTest(UnconfinedTestDispatcher()) {
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
        runtime.applyPendingInputNotification(
            sessionId = "session-1",
            projectId = "project-1",
            projectName = "Yep Anywhere",
            inputType = "user-question",
            summary = "Which branch should continue?",
            requestId = "question-1",
        )

        runtime.approvalsRepository.answerQuestion(
            requestId = "question-1",
            answer = "Use native Android MVP.",
        )

        val request = gateway.recordedRequest("POST", "/sessions/session-1/input")
        assertEquals(
            jsonObject(
                "requestId" to JsonPrimitive("question-1"),
                "response" to JsonPrimitive("approve"),
                "answers" to jsonObject("answer" to JsonPrimitive("Use native Android MVP.")),
            ),
            request.body,
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
            if (method == "POST" && path == "/projects") {
                return jsonObject(
                    "project" to jsonObject(
                        "id" to JsonPrimitive("project-added"),
                        "name" to JsonPrimitive("New Project"),
                        "path" to JsonPrimitive("/home/demo/code/new-project"),
                        "activeOwnedCount" to JsonPrimitive(0),
                        "activeExternalCount" to JsonPrimitive(0),
                        "thinkingCount" to JsonPrimitive(0),
                        "needsAttentionCount" to JsonPrimitive(0),
                        "latestActivityAt" to JsonPrimitive("2026-04-25T12:00:00Z"),
                    ),
                )
            }
            if (method == "GET" && path.startsWith("/sessions?")) {
                return globalSessionsResponse()
            }
            if (method == "GET" && path == "/sessions?limit=50&includeStats=true") {
                return globalSessionsResponse()
            }
            if (method == "PUT" && path == "/sessions/global-1/metadata") {
                return jsonObject("accepted" to JsonPrimitive(true))
            }
            if (method == "POST" && path == "/sessions/global-1/mark-seen") {
                return jsonObject("accepted" to JsonPrimitive(true))
            }
            if (method == "DELETE" && path == "/sessions/global-1/mark-seen") {
                return jsonObject("accepted" to JsonPrimitive(true))
            }
            if (method == "GET" && path == "/settings") {
                return jsonObject(
                    "settings" to jsonObject(
                        "remoteExecutors" to JsonArray(listOf(JsonPrimitive("local"), JsonPrimitive("build-host"))),
                        "newSessionDefaults" to jsonObject(
                            "provider" to JsonPrimitive("claude"),
                            "model" to JsonPrimitive("sonnet"),
                            "permissionMode" to JsonPrimitive("default"),
                            "thinking" to jsonObject("type" to JsonPrimitive("disabled")),
                            "executor" to JsonPrimitive("local"),
                        ),
                    ),
                )
            }
            if (method == "PUT" && path == "/settings") {
                return jsonObject("settings" to JsonObject(emptyMap()))
            }
            if (method == "POST" && path == "/projects/project-1/sessions") {
                return jsonObject(
                    "sessionId" to JsonPrimitive("new-session-direct"),
                    "processId" to JsonPrimitive("process-direct"),
                    "permissionMode" to JsonPrimitive("bypassPermissions"),
                    "modeVersion" to JsonPrimitive(1),
                )
            }
            if (method == "POST" && path == "/projects/project-1/sessions/create") {
                return jsonObject(
                    "sessionId" to JsonPrimitive("new-session-created"),
                    "processId" to JsonPrimitive("process-created"),
                    "permissionMode" to JsonPrimitive("bypassPermissions"),
                    "modeVersion" to JsonPrimitive(1),
                )
            }
            if (method == "POST" && path == "/sessions/new-session-created/messages") {
                return jsonObject("queued" to JsonPrimitive(true))
            }
            if (
                method == "GET" &&
                path == "/projects/project-1/sessions/session-detail?afterMessageId=msg-1&beforeMessageId=msg-0&tailCompactions=2"
            ) {
                return jsonObject(
                    "session" to jsonObject(
                        "id" to JsonPrimitive("session-detail"),
                        "projectId" to JsonPrimitive("project-1"),
                        "title" to JsonPrimitive("Loaded detail"),
                        "updatedAt" to JsonPrimitive("2026-04-26T13:00:00Z"),
                        "provider" to JsonPrimitive("claude"),
                        "model" to JsonPrimitive("opus"),
                        "processState" to JsonPrimitive("waiting-input"),
                        "permissionMode" to JsonPrimitive("acceptEdits"),
                    ),
                    "messages" to JsonArray(
                        listOf(
                        jsonObject(
                            "id" to JsonPrimitive("msg-1"),
                            "type" to JsonPrimitive("assistant"),
                            "timestamp" to JsonPrimitive("2026-04-26T13:00:00Z"),
                            "content" to JsonArray(
                                listOf(
                                    jsonObject("type" to JsonPrimitive("text"), "text" to JsonPrimitive("hello")),
                                    jsonObject("type" to JsonPrimitive("thinking"), "thinking" to JsonPrimitive("checking project state")),
                                    jsonObject(
                                        "type" to JsonPrimitive("tool_use"),
                                        "id" to JsonPrimitive("tool-1"),
                                        "name" to JsonPrimitive("Bash"),
                                        "input" to jsonObject("command" to JsonPrimitive("npm test")),
                                    ),
                                    jsonObject(
                                        "type" to JsonPrimitive("tool_result"),
                                        "tool_use_id" to JsonPrimitive("tool-1"),
                                        "content" to JsonPrimitive("all tests passed"),
                                    ),
                                ),
                            ),
                        ),
                            jsonObject(
                                "id" to JsonPrimitive("msg-2"),
                                "type" to JsonPrimitive("user"),
                                "timestamp" to JsonPrimitive("2026-04-26T13:01:00Z"),
                                "content" to JsonArray(listOf(jsonObject("type" to JsonPrimitive("text"), "text" to JsonPrimitive("continue")))),
                            ),
                        ),
                    ),
                    "ownership" to jsonObject(
                        "owner" to JsonPrimitive("self"),
                        "processId" to JsonPrimitive("process-1"),
                        "permissionMode" to JsonPrimitive("acceptEdits"),
                        "modeVersion" to JsonPrimitive(2),
                        "state" to JsonPrimitive("waiting-input"),
                    ),
                    "pendingInputRequest" to jsonObject(
                        "id" to JsonPrimitive("request-detail"),
                        "type" to JsonPrimitive("tool-approval"),
                        "prompt" to JsonPrimitive("Approve command"),
                    ),
                    "slashCommands" to JsonArray(
                        listOf(
                            jsonObject("name" to JsonPrimitive("/compact"), "description" to JsonPrimitive("Compact")),
                            jsonObject("name" to JsonPrimitive("/model"), "description" to JsonPrimitive("Switch model")),
                        ),
                    ),
                    "pagination" to jsonObject(
                        "hasOlderMessages" to JsonPrimitive(true),
                        "totalMessageCount" to JsonPrimitive(42),
                        "returnedMessageCount" to JsonPrimitive(2),
                        "truncatedBeforeMessageId" to JsonPrimitive("msg-0"),
                        "totalCompactions" to JsonPrimitive(2),
                    ),
                )
            }
            if (method == "GET" && path == "/projects/project-1/sessions/session-detail/metadata") {
                return jsonObject(
                    "session" to jsonObject(
                        "id" to JsonPrimitive("session-detail"),
                        "projectId" to JsonPrimitive("project-1"),
                        "title" to JsonPrimitive("metadata title"),
                        "updatedAt" to JsonPrimitive("2026-04-26T13:02:00Z"),
                        "provider" to JsonPrimitive("claude"),
                        "model" to JsonPrimitive("sonnet"),
                        "processState" to JsonPrimitive("idle"),
                        "permissionMode" to JsonPrimitive("default"),
                    ),
                    "ownership" to jsonObject(
                        "owner" to JsonPrimitive("external"),
                        "state" to JsonPrimitive("idle"),
                    ),
                    "slashCommands" to JsonArray(
                        listOf(jsonObject("name" to JsonPrimitive("/help"), "description" to JsonPrimitive("Help"))),
                    ),
                )
            }
            return when (path) {
                "/projects" -> jsonObject(
                    "projects" to JsonArray(
                        listOf(
                            jsonObject(
                                "id" to JsonPrimitive("project-1"),
                                "name" to JsonPrimitive("Yep Anywhere"),
                                "path" to JsonPrimitive("/repo/yepanywhere"),
                                "activeOwnedCount" to JsonPrimitive(1),
                                "activeExternalCount" to JsonPrimitive(0),
                                "thinkingCount" to JsonPrimitive(1),
                                "needsAttentionCount" to JsonPrimitive(2),
                                "latestActivityAt" to JsonPrimitive("2026-04-23T12:00:00Z"),
                            ),
                            jsonObject(
                                "id" to JsonPrimitive("project-2"),
                                "name" to JsonPrimitive("Relay Backend"),
                                "activeOwnedCount" to JsonPrimitive(0),
                                "activeExternalCount" to JsonPrimitive(1),
                                "thinkingCount" to JsonPrimitive(0),
                                "needsAttentionCount" to JsonPrimitive(0),
                                "latestActivityAt" to JsonPrimitive("2026-04-24T12:00:00Z"),
                            ),
                            jsonObject(
                                "id" to JsonPrimitive("project-3"),
                                "name" to JsonPrimitive("Archive"),
                                "activeOwnedCount" to JsonPrimitive(0),
                                "activeExternalCount" to JsonPrimitive(0),
                                "thinkingCount" to JsonPrimitive(0),
                                "needsAttentionCount" to JsonPrimitive(0),
                                "latestActivityAt" to JsonPrimitive("2026-04-20T12:00:00Z"),
                            ),
                        ),
                    ),
                )

                "/projects/project-1" -> jsonObject(
                    "project" to jsonObject(
                        "id" to JsonPrimitive("project-1"),
                        "name" to JsonPrimitive("Yep Anywhere"),
                        "path" to JsonPrimitive("/repo/yepanywhere"),
                        "activeOwnedCount" to JsonPrimitive(1),
                        "activeExternalCount" to JsonPrimitive(0),
                        "thinkingCount" to JsonPrimitive(1),
                        "needsAttentionCount" to JsonPrimitive(2),
                        "latestActivityAt" to JsonPrimitive("2026-04-23T12:00:00Z"),
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
            "/sessions/session-detail/messages" -> jsonObject("queued" to JsonPrimitive(true))
            "/sessions/session-detail/deferred/android-1" -> jsonObject("cancelled" to JsonPrimitive(true))
            "/sessions/session-detail/hold" -> jsonObject(
                "isHeld" to JsonPrimitive(true),
                "state" to JsonPrimitive("held"),
            )
            "/processes/process-1/interrupt" -> jsonObject(
                "interrupted" to JsonPrimitive(true),
                "supported" to JsonPrimitive(true),
            )
            "/processes/process-1/abort" -> jsonObject("aborted" to JsonPrimitive(true))
            "/sessions/session-detail/process" -> jsonObject(
                "process" to jsonObject(
                    "id" to JsonPrimitive("process-1"),
                    "sessionId" to JsonPrimitive("session-detail"),
                    "projectId" to JsonPrimitive("project-1"),
                    "projectName" to JsonPrimitive("Yep Anywhere"),
                    "projectPath" to JsonPrimitive("D:/projects/yepanywhere"),
                    "sessionTitle" to JsonPrimitive("Android supervisor"),
                    "state" to JsonPrimitive("in-turn"),
                    "startedAt" to JsonPrimitive("2026-04-26T12:00:00Z"),
                    "queueDepth" to JsonPrimitive(1),
                    "provider" to JsonPrimitive("claude"),
                    "model" to JsonPrimitive("sonnet"),
                    "thinking" to jsonObject("type" to JsonPrimitive("enabled")),
                    "effort" to JsonPrimitive("medium"),
                    "executor" to JsonPrimitive("local"),
                    "pid" to JsonPrimitive(1234),
                ),
            )
            "/processes/process-1/models" -> jsonObject(
                "models" to JsonArray(
                    listOf(
                        jsonObject(
                            "id" to JsonPrimitive("sonnet"),
                            "name" to JsonPrimitive("Sonnet"),
                            "description" to JsonPrimitive("Balanced model"),
                        ),
                        jsonObject(
                            "id" to JsonPrimitive("opus"),
                            "name" to JsonPrimitive("Opus"),
                            "description" to JsonPrimitive("Deep model"),
                        ),
                    ),
                ),
            )
            "/processes/process-1/model" -> jsonObject(
                "success" to JsonPrimitive(true),
                "model" to JsonPrimitive("opus"),
            )
            "/processes?includeTerminated=true" -> jsonObject(
                "processes" to JsonArray(
                    listOf(
                        jsonObject(
                            "id" to JsonPrimitive("process-1"),
                            "sessionId" to JsonPrimitive("session-detail"),
                            "projectId" to JsonPrimitive("project-1"),
                            "projectName" to JsonPrimitive("Yep Anywhere"),
                            "projectPath" to JsonPrimitive("D:/projects/yepanywhere"),
                            "sessionTitle" to JsonPrimitive("Android supervisor"),
                            "state" to JsonPrimitive("in-turn"),
                            "startedAt" to JsonPrimitive("2026-04-26T12:00:00Z"),
                            "queueDepth" to JsonPrimitive(1),
                            "provider" to JsonPrimitive("claude"),
                            "model" to JsonPrimitive("sonnet"),
                            "executor" to JsonPrimitive("local"),
                        ),
                    ),
                ),
                "terminatedProcesses" to JsonArray(
                    listOf(
                        jsonObject(
                            "id" to JsonPrimitive("process-old"),
                            "sessionId" to JsonPrimitive("session-old"),
                            "projectId" to JsonPrimitive("project-1"),
                            "projectName" to JsonPrimitive("Yep Anywhere"),
                            "sessionTitle" to JsonPrimitive("Old Android supervisor"),
                            "state" to JsonPrimitive("stopped"),
                        ),
                    ),
                ),
            )
            "/projects/project-1/sessions/session-detail/agents" -> jsonObject(
                "mappings" to JsonArray(
                    listOf(
                        jsonObject(
                            "toolUseId" to JsonPrimitive("tool-1"),
                            "agentId" to JsonPrimitive("agent-1"),
                        ),
                    ),
                ),
            )
            "/projects/project-1/sessions/session-detail/agents/agent-1" -> jsonObject(
                "status" to JsonPrimitive("completed"),
                "messages" to JsonArray(
                    listOf(
                        jsonObject(
                            "id" to JsonPrimitive("agent-msg-1"),
                            "type" to JsonPrimitive("assistant"),
                            "timestamp" to JsonPrimitive("2026-04-26T12:30:00Z"),
                            "content" to JsonArray(
                                listOf(
                                    jsonObject(
                                        "type" to JsonPrimitive("text"),
                                        "text" to JsonPrimitive("Subagent completed Android renderer work"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
            else -> JsonObject(emptyMap())
        }
        }

        override suspend fun subscribeSession(sessionId: String): String = "sub-session-$sessionId"

        override suspend fun subscribeActivity(): String = "sub-activity"

        override suspend fun unsubscribe(subscriptionId: String) = Unit

                fun recordedRequest(method: String, path: String): RecordedRequest {
            return assertNotNull(
                requests.lastOrNull { request ->
                    request.method == method && request.path == path
                },
            )
        }

        private fun globalSessionsResponse(): JsonObject {
            return jsonObject(
                "sessions" to JsonArray(
                    listOf(
                        jsonObject(
                            "id" to JsonPrimitive("global-1"),
                            "projectId" to JsonPrimitive("project-1"),
                            "title" to JsonPrimitive("Claude supervisor"),
                            "updatedAt" to JsonPrimitive("2026-04-26T12:00:00Z"),
                            "status" to JsonPrimitive("running"),
                            "provider" to JsonPrimitive("claude"),
                            "model" to JsonPrimitive("opus"),
                            "executor" to JsonPrimitive("local"),
                            "activity" to JsonPrimitive("in-turn"),
                            "hasUnread" to JsonPrimitive(true),
                            "isStarred" to JsonPrimitive(true),
                            "isArchived" to JsonPrimitive(false),
                        ),
                    ),
                ),
                "hasMore" to JsonPrimitive(true),
                "nextAfter" to JsonPrimitive("cursor-2"),
                "stats" to jsonObject(
                    "total" to JsonPrimitive(12),
                    "unread" to JsonPrimitive(3),
                    "starred" to JsonPrimitive(2),
                    "archived" to JsonPrimitive(1),
                ),
            )
        }
    }
}

private fun jsonObject(vararg entries: Pair<String, JsonElement>): JsonObject {
    return JsonObject(entries.toMap())
}
