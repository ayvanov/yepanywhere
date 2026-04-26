package com.yepanywhere.android.data

import com.yepanywhere.android.core.cache.SessionCacheStore
import com.yepanywhere.android.core.model.GlobalSessionFilters
import com.yepanywhere.android.core.model.GlobalSessionStats
import com.yepanywhere.android.core.model.GlobalSessionsPage
import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.InboxItemKind
import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.SessionMessage
import com.yepanywhere.android.core.model.SessionMessageAuthor
import com.yepanywhere.android.core.model.SessionMetadataUpdate
import com.yepanywhere.android.core.model.SessionStatus
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.core.model.SupervisorPushEvent
import com.yepanywhere.android.core.model.SupervisorPushEventStreamAdapter
import com.yepanywhere.android.core.model.SupervisorPushPayload
import com.yepanywhere.android.core.model.SupervisorShellSnapshot
import com.yepanywhere.android.core.repository.ApprovalsRepository
import com.yepanywhere.android.core.repository.InboxRepository
import com.yepanywhere.android.core.repository.ProjectsRepository
import com.yepanywhere.android.core.repository.RelayAuthRepository
import com.yepanywhere.android.core.repository.RelayConnectionClient
import com.yepanywhere.android.core.repository.RelayPushPayloadSource
import com.yepanywhere.android.core.repository.SessionsRepository
import com.yepanywhere.android.core.usecase.ObserveSupervisorShellUseCase
import com.yepanywhere.android.core.usecase.SecureRelayAuthHandshakeResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class RelaySupervisorRuntime(
    initialSnapshot: SupervisorShellSnapshot = defaultSupervisorShellSnapshot(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    supervisorPushPayloadSource: RelayPushPayloadSource = object : RelayPushPayloadSource {
        override fun payloadStream(): Flow<SupervisorPushPayload> = emptyFlow()
    },
    private val relayAuthHandshake: suspend (
        username: String,
        password: String?,
        relayUrl: String,
        storedSession: StoredRelaySession?,
    ) -> SecureRelayAuthHandshakeResult,
    private val relayRoutingUsername: String? = null,
    realtimeGatewayOverride: RelayRealtimeGateway? = null,
    cacheStore: SessionCacheStore = InMemorySessionCacheStore(initialSnapshot),
) : SupervisorRuntime {
    private val cache = cacheStore
    private val connectionState = MutableStateFlow(initialSnapshot.connectionStatus)
    private val storedSession = MutableStateFlow<RelaySession?>(null)
    private val storedSecureSession = MutableStateFlow<StoredRelaySession?>(null)
    private val inboxInvalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val localSupervisorPushPayloads = MutableSharedFlow<SupervisorPushPayload>(extraBufferCapacity = 64)
    private val supervisorPushPayloads = merge(
        localSupervisorPushPayloads,
        supervisorPushPayloadSource.payloadStream(),
    )
    private val supervisorPushEvents = SupervisorPushEventStreamAdapter(supervisorPushPayloads).events()
    private val realtime: RelayRealtimeGateway = realtimeGatewayOverride ?: RelayRealtimeClient(
        scope = scope,
        httpClient = createRealtimeHttpClient(),
    )
    private val subscriptionMutex = Mutex()
    private val sessionSubscriptions = mutableMapOf<String, String>()
    private val subscriptionToSession = mutableMapOf<String, String>()
    private var activitySubscriptionId: String? = null
    private val activeSessionUiId: String = initialSnapshot.timeline.sessionId
    private var activeSessionBackendId: String = activeSessionUiId

    override val activeSessionId: String
        get() = activeSessionUiId

    init {
        scope.launch {
            realtime.connectionState.collectLatest { status ->
                connectionState.value = status
            }
        }
        scope.launch {
            realtime.events().collectLatest(::handleRealtimeEvent)
        }
    }

    override val relayAuthRepository: RelayAuthRepository = object : RelayAuthRepository {
        override suspend fun login(
            username: String,
            password: String?,
            relayUrl: String,
        ): RelaySession {
            connectionState.value = RelayConnectionStatus.CONNECTING
            val handshake = try {
                relayAuthHandshake(username, password, relayUrl, storedSecureSession.value)
            } catch (error: Throwable) {
                connectionState.value = RelayConnectionStatus.DISCONNECTED
                throw error
            }

            if (handshake.clearedStoredSession) {
                storedSecureSession.value = null
            }
            handshake.persistedSession?.let { persisted ->
                storedSecureSession.value = persisted
            }

            storedSession.value = handshake.session
            val secureSession = storedSecureSession.value
            if (secureSession != null) {
                connectRealtimeSession(
                    relayUrl = handshake.session.relayUrl,
                    storedSession = secureSession,
                )
                launchInitialRefresh()
            } else {
                connectionState.value = RelayConnectionStatus.CONNECTED
            }
            return handshake.session
        }

        override suspend fun persistStoredSession(session: StoredRelaySession) {
            storedSecureSession.value = session
        }

        override suspend fun restoreStoredSession(): StoredRelaySession? = storedSecureSession.value

        override suspend fun restoreSession(): RelaySession? = storedSession.value

        override suspend fun clearSession() {
            storedSession.value = null
            storedSecureSession.value = null
            clearSubscriptions()
            realtime.disconnect()
            connectionState.value = RelayConnectionStatus.DISCONNECTED
        }
    }

    override val relayConnectionClient: RelayConnectionClient = object : RelayConnectionClient {
        override val connectionState: Flow<RelayConnectionStatus> = this@RelaySupervisorRuntime.connectionState

        override suspend fun connect(session: RelaySession) {
            val secureSession = storedSecureSession.value
                ?: throw IllegalStateException("missing_stored_relay_session")
            storedSession.value = session
            connectRealtimeSession(
                relayUrl = session.relayUrl,
                storedSession = secureSession,
            )
            launchInitialRefresh()
        }

        override suspend fun disconnect() {
            clearSubscriptions()
            realtime.disconnect()
            this@RelaySupervisorRuntime.connectionState.value = RelayConnectionStatus.DISCONNECTED
        }

        override suspend fun ensureConnected() {
            realtime.ensureConnected()
        }

        override fun sessionStream(sessionId: String): Flow<SessionTimeline> {
            val backendSessionId = toBackendSessionId(sessionId)
            scope.launch {
                runCatching {
                    ensureSessionSubscription(backendSessionId)
                    refreshSessionTimeline(backendSessionId)
                }
            }
            return cache.observeTimeline(sessionId).map { timeline ->
                timeline ?: SessionTimeline(
                    sessionId = sessionId,
                    connectionStatus = this@RelaySupervisorRuntime.connectionState.value,
                    messages = emptyList(),
                )
            }
        }

        override fun inboxInvalidationStream(): Flow<Unit> = inboxInvalidations

        override fun supervisorPushEventStream(): Flow<SupervisorPushEvent> = supervisorPushEvents
    }

    override val projectsRepository: ProjectsRepository = object : ProjectsRepository {
        override fun observeProjects(): Flow<List<ProjectSummary>> = cache.observeProjects()

        override suspend fun refreshProjects() {
            withSyncState {
                refreshProjectsInternal()
            }
        }

        override suspend fun getProject(projectId: String): ProjectSummary {
            val payload = requestObject(
                method = "GET",
                path = "/projects/$projectId",
            )
            val project = payload["project"].asObject()?.toProjectSummary()
                ?: throw IllegalStateException("missing_project")
            cache.storeProjects(
                sortProjectsForDisplay(
                    cache.observeProjects().first().filterNot { it.id == project.id } + project,
                ),
            )
            return project
        }

        override suspend fun addProject(path: String): ProjectSummary {
            val payload = requestObject(
                method = "POST",
                path = "/projects",
                body = buildJsonObject {
                    put("path", path)
                },
            )
            val project = payload["project"].asObject()?.toProjectSummary()
                ?: throw IllegalStateException("missing_project")
            cache.storeProjects(
                sortProjectsForDisplay(
                    cache.observeProjects().first().filterNot { it.id == project.id } + project,
                ),
            )
            return project
        }
    }

    override val sessionsRepository: SessionsRepository = object : SessionsRepository {
        override fun observeSessions(projectId: String?): Flow<List<SessionSummary>> {
            return cache.observeSessions(projectId)
        }

        override suspend fun refreshSessions(projectId: String?) {
            withSyncState {
                refreshSessionsInternal(projectId)
                refreshActiveSessionBinding()
            }
        }

        override suspend fun loadGlobalSessions(
            filters: GlobalSessionFilters,
            after: String?,
            limit: Int,
        ): GlobalSessionsPage {
            val payload = requestObject(
                method = "GET",
                path = buildGlobalSessionsPath(
                    filters = filters,
                    after = after,
                    limit = limit,
                ),
            )
            val sessions = payload["sessions"].asJsonArray().mapNotNull { element ->
                (element as? JsonObject)?.toSessionSummary(fallbackProjectId = filters.project)
            }
            cache.storeSessions(
                if (after == null) {
                    sessions
                } else {
                    (cache.observeSessions().first() + sessions).distinctBy { it.id }
                },
            )
            return GlobalSessionsPage(
                sessions = sessions,
                hasMore = payload["hasMore"].asBoolean() ?: false,
                nextAfter = payload["nextAfter"].asString() ?: sessions.lastOrNull()?.id,
                stats = payload["stats"].asObject()?.toGlobalSessionStats()
                    ?: GlobalSessionStats(total = sessions.size),
            )
        }

        override fun observeSessionTimeline(sessionId: String): Flow<SessionTimeline> {
            val backendSessionId = toBackendSessionId(sessionId)
            scope.launch {
                runCatching {
                    ensureSessionSubscription(backendSessionId)
                    refreshSessionTimeline(backendSessionId)
                }
            }
            return cache.observeTimeline(sessionId).map { timeline ->
                timeline ?: SessionTimeline(
                    sessionId = sessionId,
                    connectionStatus = this@RelaySupervisorRuntime.connectionState.value,
                    messages = emptyList(),
                )
            }
        }

        override suspend fun sendReply(
            sessionId: String,
            text: String,
        ) {
            val backendSessionId = toBackendSessionId(sessionId)
            requestJson(
                method = "POST",
                path = "/sessions/$backendSessionId/messages",
                body = buildJsonObject {
                    put("message", text)
                },
            )
            refreshSessionTimeline(backendSessionId)
            refreshSessionsInternal(projectId = null)
            refreshInboxInternal()
        }

        override suspend fun updateSessionMetadata(
            sessionId: String,
            updates: SessionMetadataUpdate,
        ): Boolean {
            val backendSessionId = toBackendSessionId(sessionId)
            val payload = requestJson(
                method = "PUT",
                path = "/sessions/$backendSessionId/metadata",
                body = buildJsonObject {
                    updates.title?.let { put("title", it) }
                    updates.archived?.let { put("archived", it) }
                    updates.starred?.let { put("starred", it) }
                },
            )
            cache.storeSessions(
                cache.observeSessions().first().map { session ->
                    if (session.id != sessionId) {
                        session
                    } else {
                        session.copy(
                            title = updates.title ?: session.title,
                            isArchived = updates.archived ?: session.isArchived,
                            isStarred = updates.starred ?: session.isStarred,
                        )
                    }
                },
            )
            return payload.asObject()?.get("accepted").asBoolean()
                ?: payload.asObject()?.get("ok").asBoolean()
                ?: true
        }

        override suspend fun markSessionSeen(
            sessionId: String,
            timestamp: String?,
            messageId: String?,
        ): Boolean {
            val backendSessionId = toBackendSessionId(sessionId)
            val payload = requestJson(
                method = "POST",
                path = "/sessions/$backendSessionId/mark-seen",
                body = buildJsonObject {
                    timestamp?.let { put("timestamp", it) }
                    messageId?.let { put("messageId", it) }
                },
            )
            cache.storeSessions(
                cache.observeSessions().first().map { session ->
                    if (session.id == sessionId) session.copy(hasUnread = false) else session
                },
            )
            return payload.asObject()?.get("accepted").asBoolean()
                ?: payload.asObject()?.get("ok").asBoolean()
                ?: true
        }

        override suspend fun markSessionUnread(sessionId: String): Boolean {
            val backendSessionId = toBackendSessionId(sessionId)
            val payload = requestJson(
                method = "DELETE",
                path = "/sessions/$backendSessionId/mark-seen",
            )
            cache.storeSessions(
                cache.observeSessions().first().map { session ->
                    if (session.id == sessionId) session.copy(hasUnread = true) else session
                },
            )
            return payload.asObject()?.get("accepted").asBoolean()
                ?: payload.asObject()?.get("ok").asBoolean()
                ?: true
        }
    }

    override val inboxRepository: InboxRepository = object : InboxRepository {
        override fun observeInboxItems(): Flow<List<InboxItem>> = cache.observeInboxItems()

        override suspend fun refreshInbox() {
            withSyncState {
                refreshInboxInternal()
            }
        }
    }

    override val approvalsRepository: ApprovalsRepository = object : ApprovalsRepository {
        override fun observePendingApprovals(): Flow<List<PendingInputRequest>> {
            return cache.observePendingRequests()
        }

        override suspend fun approve(requestId: String) {
            respondToRequest(requestId = requestId, response = "approve", feedback = null, answer = null)
        }

        override suspend fun deny(
            requestId: String,
            feedback: String?,
        ) {
            respondToRequest(requestId = requestId, response = "deny", feedback = feedback, answer = null)
        }

        override suspend fun answerQuestion(
            requestId: String,
            answer: String,
        ) {
            respondToRequest(requestId = requestId, response = "approve", feedback = null, answer = answer)
        }
    }

    private val observeSupervisorShellUseCase = ObserveSupervisorShellUseCase(
        relayConnectionClient = relayConnectionClient,
        projectsRepository = projectsRepository,
        sessionsRepository = sessionsRepository,
        inboxRepository = inboxRepository,
        approvalsRepository = approvalsRepository,
    )

    override val shellState = observeSupervisorShellUseCase(activeSessionId = activeSessionUiId).stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = initialSnapshot,
    )

    override suspend fun applyPendingInputNotification(
        sessionId: String,
        projectId: String,
        projectName: String,
        inputType: String,
        summary: String,
        requestId: String,
    ) {
        val kind = when (inputType) {
            "tool-approval" -> InboxItemKind.APPROVAL
            else -> InboxItemKind.QUESTION
        }
        val uiSessionId = toUiSessionId(sessionId)

        val projects = cache.observeProjects().first()
        if (projects.none { it.id == projectId }) {
            cache.storeProjects(projects + ProjectSummary(id = projectId, name = projectName))
        }

        val sessions = cache.observeSessions().first()
        cache.storeSessions(
            sessions
                .filterNot { it.id == uiSessionId }
                .plus(
                    SessionSummary(
                        id = uiSessionId,
                        projectId = projectId,
                        title = summary,
                        status = SessionStatus.NEEDS_ATTENTION,
                        updatedLabel = "just now",
                        hasUnread = true,
                    ),
                ),
        )

        val inboxId = "inbox-$requestId"
        val inboxItems = cache.observeInboxItems().first()
        cache.storeInboxItems(
            inboxItems
                .filterNot { it.id == inboxId }
                .plus(
                    InboxItem(
                        id = inboxId,
                        projectId = projectId,
                        sessionId = uiSessionId,
                        title = if (kind == InboxItemKind.APPROVAL) "Approval required" else "Question",
                        subtitle = summary,
                        kind = kind,
                        isUnread = true,
                    ),
                ),
        )

        val pendingRequests = cache.observePendingRequests().first()
        cache.storePendingRequests(
            pendingRequests
                .filterNot { it.id == requestId }
                .plus(
                    PendingInputRequest(
                        id = requestId,
                        sessionId = uiSessionId,
                        title = if (kind == InboxItemKind.APPROVAL) "Approval required" else "Question",
                        body = summary,
                        kind = kind,
                    ),
                ),
        )

        inboxInvalidations.tryEmit(Unit)
    }

    override suspend fun clearSessionAttention(sessionId: String) {
        cache.storePendingRequests(
            cache.observePendingRequests().first().filterNot { it.sessionId == sessionId },
        )
        cache.storeInboxItems(
            cache.observeInboxItems().first().map { item ->
                if (item.sessionId == sessionId) {
                    item.copy(isUnread = false)
                } else {
                    item
                }
            },
        )
        cache.storeSessions(
            cache.observeSessions().first().map { session ->
                if (session.id == sessionId) {
                    session.copy(
                        status = SessionStatus.RUNNING,
                        updatedLabel = "resolved now",
                        hasUnread = false,
                    )
                } else {
                    session
                }
            },
        )
        inboxInvalidations.tryEmit(Unit)
    }

    override suspend fun emitSupervisorPushEvent(event: SupervisorPushEvent) {
        val payload = event.toPushPayload() ?: return
        localSupervisorPushPayloads.emit(payload)
    }

    override suspend fun emitSupervisorPushPayload(payload: SupervisorPushPayload) {
        localSupervisorPushPayloads.emit(payload)
    }

    override suspend fun emitSupervisorPushPayload(payload: Map<String, String>): Boolean {
        val typed = SupervisorPushPayload.fromFields(payload) ?: return false
        localSupervisorPushPayloads.emit(typed)
        return true
    }

    private suspend fun refreshAllFromBackend() {
        withSyncState {
            refreshProjectsInternal()
            refreshSessionsInternal(projectId = null)
            refreshActiveSessionBinding()
            ensureActivitySubscription()
            refreshInboxInternal()
            refreshSessionTimeline(activeSessionBackendId)
        }
    }

    private fun launchInitialRefresh() {
        scope.launch {
            runCatching {
                refreshAllFromBackend()
            }
        }
    }

    private suspend fun connectRealtimeSession(
        relayUrl: String,
        storedSession: StoredRelaySession,
    ) {
        try {
            withTimeout(RELAY_REALTIME_CONNECT_TIMEOUT_MS) {
                realtime.connect(
                    relayUrl = relayUrl,
                    storedSession = storedSession,
                    routingUsername = relayRoutingUsername ?: storedSession.username,
                )
                ensureActivitySubscription()
            }
        } catch (_: TimeoutCancellationException) {
            clearSubscriptions()
            realtime.disconnect()
            connectionState.value = RelayConnectionStatus.DISCONNECTED
            throw IllegalStateException(RELAY_REALTIME_CONNECT_TIMEOUT_ERROR_MESSAGE)
        }
    }

    private suspend fun refreshProjectsInternal() {
        val payload = requestObject(
            method = "GET",
            path = "/projects",
        )
        val projects = payload["projects"].asJsonArray().mapNotNull { element ->
            val project = element as? JsonObject ?: return@mapNotNull null
            project.toProjectSummary()
        }
        cache.storeProjects(sortProjectsForDisplay(projects))
    }

    private suspend fun refreshSessionsInternal(projectId: String?) {
        val sessions = mutableListOf<SessionSummary>()
        val projectIds = if (projectId != null) {
            listOf(projectId)
        } else {
            val projects = cache.observeProjects().first()
            if (projects.isEmpty()) {
                refreshProjectsInternal()
            }
            cache.observeProjects().first().map { it.id }
        }

        projectIds.forEach { id ->
            val payload = requestObject(
                method = "GET",
                path = "/projects/$id/sessions",
            )
            payload["sessions"].asJsonArray().forEach { element ->
                val session = element as? JsonObject ?: return@forEach
                val summary = session.toSessionSummary(fallbackProjectId = id)
                    ?: return@forEach
                if (summary.isArchived) {
                    return@forEach
                }
                sessions += summary
            }
        }

        cache.storeSessions(sessions)
    }

    private suspend fun refreshInboxInternal() {
        val payload = requestObject(
            method = "GET",
            path = "/inbox",
        )
        val orderedSections = listOf(
            "needsAttention" to InboxItemKind.APPROVAL,
            "active" to InboxItemKind.NOTIFICATION,
            "recentActivity" to InboxItemKind.NOTIFICATION,
            "unread8h" to InboxItemKind.NOTIFICATION,
            "unread24h" to InboxItemKind.NOTIFICATION,
        )

        val inboxItems = mutableListOf<InboxItem>()
        orderedSections.forEach { (sectionKey, fallbackKind) ->
            payload[sectionKey].asJsonArray().forEach { element ->
                val item = element as? JsonObject ?: return@forEach
                val sessionId = item["sessionId"].asString()
                val pendingInputType = item["pendingInputType"].asString()
                val kind = when (pendingInputType) {
                    "tool-approval" -> InboxItemKind.APPROVAL
                    "user-question" -> InboxItemKind.QUESTION
                    else -> fallbackKind
                }
                val uiSessionId = sessionId?.let(::toUiSessionId)
                val title = when (kind) {
                    InboxItemKind.APPROVAL -> "Approval required"
                    InboxItemKind.QUESTION -> "Question"
                    InboxItemKind.NOTIFICATION -> "Session update"
                }
                inboxItems += InboxItem(
                    id = "inbox-$sectionKey-${sessionId ?: inboxItems.size}",
                    projectId = item["projectId"].asString(),
                    sessionId = uiSessionId,
                    title = title,
                    subtitle = item["sessionTitle"].asString() ?: item["projectName"].asString() ?: "",
                    kind = kind,
                    isUnread = item["hasUnread"].asBoolean() ?: (pendingInputType != null),
                )
            }
        }

        cache.storeInboxItems(inboxItems)
    }

    private suspend fun refreshSessionTimeline(sessionId: String) {
        val projectId = resolveProjectIdForSession(sessionId) ?: return
        val response = requestObject(
            method = "GET",
            path = "/projects/$projectId/sessions/$sessionId",
        )

        val messagesElement = response["messages"] ?: response["session"].asObject()?.get("messages")
        val messages = messagesElement.asJsonArray().mapIndexed { index, element ->
            val message = element as? JsonObject
            message.toSessionMessage(index)
        }
        val connection = connectionState.value
        val uiSessionId = toUiSessionId(sessionId)
        cache.storeTimeline(
            SessionTimeline(
                sessionId = uiSessionId,
                connectionStatus = connection,
                messages = messages,
            ),
        )

        val pending = response["pendingInputRequest"].asObject()?.toPendingRequest(uiSessionId)
        val requests = cache.observePendingRequests().first().filterNot { request ->
            request.sessionId == uiSessionId || (pending != null && request.id == pending.id)
        }
        cache.storePendingRequests(
            if (pending == null) requests else requests + pending,
        )
    }

    private suspend fun respondToRequest(
        requestId: String,
        response: String,
        feedback: String?,
        answer: String?,
    ) {
        val request = cache.observePendingRequests().first().firstOrNull { it.id == requestId }
            ?: return
        val backendSessionId = toBackendSessionId(request.sessionId)
        requestJson(
            method = "POST",
            path = "/sessions/$backendSessionId/input",
            body = buildJsonObject {
                put("requestId", requestId)
                put("response", response)
                if (!feedback.isNullOrBlank()) {
                    put("feedback", feedback)
                }
                if (!answer.isNullOrBlank()) {
                    put(
                        "answers",
                        JsonObject(
                            mapOf(
                                "answer" to JsonPrimitive(answer),
                            ),
                        ),
                    )
                }
            },
        )
        refreshSessionTimeline(backendSessionId)
        refreshSessionsInternal(projectId = null)
        refreshInboxInternal()
    }

    private suspend fun ensureActivitySubscription() {
        if (activitySubscriptionId != null) {
            return
        }
        activitySubscriptionId = realtime.subscribeActivity()
    }

    private suspend fun ensureSessionSubscription(sessionId: String) {
        subscriptionMutex.withLock {
            if (sessionSubscriptions.containsKey(sessionId)) {
                return
            }
            val subscriptionId = realtime.subscribeSession(sessionId)
            sessionSubscriptions[sessionId] = subscriptionId
            subscriptionToSession[subscriptionId] = sessionId
        }
    }

    private suspend fun clearSubscriptions() {
        val subscriptions = subscriptionMutex.withLock {
            val ids = buildList {
                activitySubscriptionId?.let(::add)
                addAll(sessionSubscriptions.values)
            }
            activitySubscriptionId = null
            sessionSubscriptions.clear()
            subscriptionToSession.clear()
            ids
        }
        subscriptions.forEach { id ->
            runCatching {
                realtime.unsubscribe(id)
            }
        }
    }

    private suspend fun handleRealtimeEvent(event: RelayRealtimeEvent) {
        val activitySubscription = activitySubscriptionId
        if (activitySubscription != null && event.subscriptionId == activitySubscription) {
            if (event.eventType != "connected" && event.eventType != "heartbeat") {
                refreshProjectsInternal()
                refreshSessionsInternal(projectId = null)
                refreshInboxInternal()
                val sessionId = event.data.asObject()?.get("sessionId").asString()
                if (sessionId != null) {
                    refreshSessionTimeline(sessionId)
                }
                inboxInvalidations.tryEmit(Unit)
            }
            return
        }

        val sessionId = subscriptionMutex.withLock {
            subscriptionToSession[event.subscriptionId]
        } ?: return
        if (event.eventType == "connected" || event.eventType == "heartbeat") {
            return
        }

        refreshSessionTimeline(sessionId)
        refreshSessionsInternal(projectId = null)
        refreshInboxInternal()
        inboxInvalidations.tryEmit(Unit)
    }

    private suspend fun refreshActiveSessionBinding() {
        val sessions = cache.observeSessions().first()
        if (sessions.isEmpty()) {
            return
        }
        if (sessions.none { it.id == activeSessionBackendId }) {
            activeSessionBackendId = sessions.first().id
        }
    }

    private suspend fun resolveProjectIdForSession(sessionId: String): String? {
        val sessions = cache.observeSessions().first()
        return sessions.firstOrNull { it.id == sessionId }?.projectId
    }

    private fun toBackendSessionId(sessionId: String): String {
        return if (sessionId == activeSessionUiId) activeSessionBackendId else sessionId
    }

    private fun toUiSessionId(sessionId: String): String {
        return if (sessionId == activeSessionBackendId) activeSessionUiId else sessionId
    }

    private suspend fun withSyncState(block: suspend () -> Unit) {
        val previous = connectionState.value
        if (previous == RelayConnectionStatus.CONNECTED) {
            connectionState.value = RelayConnectionStatus.SYNCING
        }
        try {
            block()
        } finally {
            if (connectionState.value == RelayConnectionStatus.SYNCING) {
                connectionState.value = RelayConnectionStatus.CONNECTED
            }
        }
    }

    private suspend fun requestObject(
        method: String,
        path: String,
        body: JsonObject? = null,
    ): JsonObject {
        val response = realtime.request(
            method = method,
            path = path,
            body = body,
        )
        return response.asObject() ?: JsonObject(emptyMap())
    }

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JsonObject? = null,
    ): JsonElement? {
        return realtime.request(
            method = method,
            path = path,
            body = body,
        )
    }
}

private fun createRealtimeHttpClient(): HttpClient {
    return HttpClient(OkHttp) {
        install(WebSockets)
        engine {
            config {
                callTimeout(RELAY_REALTIME_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                connectTimeout(RELAY_REALTIME_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                readTimeout(RELAY_REALTIME_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                writeTimeout(RELAY_REALTIME_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                pingInterval(15, TimeUnit.SECONDS)
            }
        }
    }
}

private fun JsonObject?.asObject(): JsonObject? = this

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.asJsonArray(): JsonArray {
    return this as? JsonArray ?: JsonArray(emptyList())
}

private fun JsonElement?.asString(): String? {
    return (this as? JsonPrimitive)?.contentOrNull
}

private fun JsonElement?.asInt(): Int? {
    return (this as? JsonPrimitive)?.intOrNull
}

private fun JsonElement?.asBoolean(): Boolean? {
    return (this as? JsonPrimitive)?.booleanOrNull
}

private fun JsonObject.toProjectSummary(): ProjectSummary {
    val activeOwnedCount = this["activeOwnedCount"].asInt() ?: 0
    val activeExternalCount = this["activeExternalCount"].asInt() ?: 0
    return ProjectSummary(
        id = this["id"].asString() ?: throw IllegalStateException("missing_project_id"),
        name = this["name"].asString() ?: "Untitled project",
        path = this["path"].asString(),
        activeOwnedCount = activeOwnedCount,
        activeExternalCount = activeExternalCount,
        thinkingCount = this["thinkingCount"].asInt() ?: 0,
        needsAttentionCount = this["needsAttentionCount"].asInt() ?: 0,
        latestActivityAt = this["latestActivityAt"].asString() ?: this["updatedAt"].asString(),
        isActive = activeOwnedCount + activeExternalCount > 0,
    )
}

private fun sortProjectsForDisplay(projects: List<ProjectSummary>): List<ProjectSummary> {
    return projects.sortedWith(
        compareByDescending<ProjectSummary> { it.needsAttentionCount }
            .thenByDescending { it.latestActivityAt.orEmpty() },
    )
}

private fun buildGlobalSessionsPath(
    filters: GlobalSessionFilters,
    after: String?,
    limit: Int,
): String {
    val params = buildList {
        filters.project?.let { add("project" to it) }
        filters.query?.let { add("q" to it) }
        filters.status?.let { add("status" to it) }
        filters.provider?.let { add("provider" to it) }
        filters.executor?.let { add("executor" to it) }
        filters.age?.let { add("age" to it) }
        after?.let { add("after" to it) }
        add("limit" to limit.toString())
        if (filters.includeArchived) add("includeArchived" to "true")
        if (filters.starred) add("starred" to "true")
        if (filters.includeStats) add("includeStats" to "true")
    }
    if (params.isEmpty()) {
        return "/sessions"
    }
    return "/sessions?" + params.joinToString("&") { (key, value) ->
        "${key.urlEncode()}=${value.urlEncode()}"
    }
}

private fun String.urlEncode(): String {
    return URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}

private fun JsonObject.toSessionSummary(fallbackProjectId: String? = null): SessionSummary? {
    val sessionId = this["id"].asString() ?: this["sessionId"].asString() ?: return null
    val title = this["customTitle"].asString()
        ?: this["title"].asString()
        ?: this["summary"].asString()
        ?: "Untitled session"
    val pendingInputType = this["pendingInputType"].asString()
    val activity = this["activity"].asString()
    val statusText = this["status"].asString()
    val status = when {
        pendingInputType != null -> SessionStatus.NEEDS_ATTENTION
        statusText == "needs_attention" || statusText == "needs-attention" -> SessionStatus.NEEDS_ATTENTION
        statusText == "running" || statusText == "active" -> SessionStatus.RUNNING
        activity == "in-turn" || activity == "waiting-input" -> SessionStatus.RUNNING
        else -> SessionStatus.IDLE
    }
    val isArchived = this["isArchived"].asBoolean() ?: this["archived"].asBoolean() ?: false
    val isStarred = this["isStarred"].asBoolean() ?: this["starred"].asBoolean() ?: false
    return SessionSummary(
        id = sessionId,
        projectId = this["projectId"].asString() ?: fallbackProjectId ?: "unknown-project",
        title = title,
        status = status,
        updatedLabel = this["updatedAt"].asString()
            ?: this["lastActivityAt"].asString()
            ?: this["createdAt"].asString()
            ?: "just now",
        hasUnread = this["hasUnread"].asBoolean() ?: (pendingInputType != null),
        provider = this["provider"].asString(),
        model = this["model"].asString(),
        ownership = this["ownership"].asString(),
        activity = activity,
        isArchived = isArchived,
        isStarred = isStarred,
        executor = this["executor"].asString(),
    )
}

private fun JsonObject.toGlobalSessionStats(): GlobalSessionStats {
    return GlobalSessionStats(
        total = this["total"].asInt() ?: 0,
        unread = this["unread"].asInt() ?: 0,
        starred = this["starred"].asInt() ?: 0,
        archived = this["archived"].asInt() ?: 0,
    )
}

private fun JsonObject?.toSessionMessage(index: Int): SessionMessage {
    val message = this ?: JsonObject(emptyMap())
    val author = when (message["type"].asString()) {
        "user" -> SessionMessageAuthor.USER
        "assistant" -> SessionMessageAuthor.ASSISTANT
        else -> SessionMessageAuthor.SYSTEM
    }
    return SessionMessage(
        id = message["id"].asString() ?: message["uuid"].asString() ?: "msg-$index",
        author = author,
        body = message.extractMessageBody(),
        timestampLabel = message["timestamp"].asString()
            ?: message["createdAt"].asString()
            ?: message["updatedAt"].asString()
            ?: "now",
    )
}

private fun JsonObject.extractMessageBody(): String {
    val content = this["content"]
    if (content is JsonPrimitive) {
        content.contentOrNull?.let { return it }
    }
    if (content is JsonArray) {
        val chunks = content.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull
                is JsonObject -> {
                    item["text"].asString()
                        ?: item["thinking"].asString()
                        ?: item["message"].asString()
                        ?: item["prompt"].asString()
                }

                else -> null
            }
        }.filter { it.isNotBlank() }
        if (chunks.isNotEmpty()) {
            return chunks.joinToString("\n")
        }
    }
    return this["text"].asString()
        ?: this["message"].asString()
        ?: this["prompt"].asString()
        ?: "[non-text message]"
}

private fun JsonObject.toPendingRequest(sessionId: String): PendingInputRequest {
    val type = this["type"].asString()
    val kind = if (type == "tool-approval") InboxItemKind.APPROVAL else InboxItemKind.QUESTION
    return PendingInputRequest(
        id = this["id"].asString() ?: "request-$sessionId",
        sessionId = sessionId,
        title = if (kind == InboxItemKind.APPROVAL) "Approval required" else "Question",
        body = this["prompt"].asString() ?: "",
        kind = kind,
    )
}
