package com.yepanywhere.android.data

import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.InboxItemKind
import com.yepanywhere.android.core.model.GlobalSessionFilters
import com.yepanywhere.android.core.model.GlobalSessionStats
import com.yepanywhere.android.core.model.GlobalSessionsPage
import com.yepanywhere.android.core.model.NewSessionDefaults
import com.yepanywhere.android.core.model.NewSessionOptions
import com.yepanywhere.android.core.model.NewSessionSettings
import com.yepanywhere.android.core.model.NewSessionStartResult
import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.ProcessControlResult
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.SessionInputRequest
import com.yepanywhere.android.core.model.SessionMessage
import com.yepanywhere.android.core.model.SessionMessageAuthor
import com.yepanywhere.android.core.model.SessionDetail
import com.yepanywhere.android.core.model.SessionDetailQuery
import com.yepanywhere.android.core.model.SessionMetadataUpdate
import com.yepanywhere.android.core.model.SessionStatus
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.core.model.SupervisorPushEvent
import com.yepanywhere.android.core.model.SupervisorPushPayload
import com.yepanywhere.android.core.model.SupervisorPushEventStreamAdapter
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class InMemorySupervisorRuntime(
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
    ) -> SecureRelayAuthHandshakeResult = { username, _, relayUrl, _ ->
        SecureRelayAuthHandshakeResult(
            session = RelaySession(
                username = username,
                relayUrl = relayUrl,
                sessionId = "relay-session-demo",
            ),
            persistedSession = StoredRelaySession(
                wsUrl = relayUrl,
                username = username,
                sessionId = "relay-session-demo",
                sessionKey = "demo-session-key",
            ),
            clearedStoredSession = false,
            transportNonce = null,
            resumed = false,
        )
    },
) : SupervisorRuntime {
    private val initialTimeline = initialSnapshot.timeline
    override val activeSessionId: String = initialTimeline.sessionId
    private val cache = InMemorySessionCacheStore(initialSnapshot)
    private val storedSession = MutableStateFlow<RelaySession?>(null)
    private val storedSecureSession = MutableStateFlow<StoredRelaySession?>(null)
    private val newSessionDefaults = MutableStateFlow(NewSessionDefaults())
    private val connectionState = MutableStateFlow(initialSnapshot.connectionStatus)
    private val inboxInvalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val localSupervisorPushPayloads = MutableSharedFlow<SupervisorPushPayload>(extraBufferCapacity = 64)
    private val supervisorPushPayloads = merge(
        localSupervisorPushPayloads,
        supervisorPushPayloadSource.payloadStream(),
    )
    private val supervisorPushEvents = SupervisorPushEventStreamAdapter(supervisorPushPayloads).events()

    override val relayAuthRepository: RelayAuthRepository = object : RelayAuthRepository {
        override suspend fun login(
            username: String,
            password: String?,
            relayUrl: String,
        ): RelaySession {
            connectionState.value = RelayConnectionStatus.CONNECTING
            val result = try {
                relayAuthHandshake(username, password, relayUrl, storedSecureSession.value)
            } catch (error: Throwable) {
                connectionState.value = RelayConnectionStatus.DISCONNECTED
                throw error
            }

            if (result.clearedStoredSession) {
                storedSecureSession.value = null
            }
            result.persistedSession?.let { persistedSession ->
                storedSecureSession.value = persistedSession
            }

            val session = result.session
            storedSession.value = session
            connectionState.value = RelayConnectionStatus.CONNECTED
            return session
        }

        override suspend fun persistStoredSession(session: StoredRelaySession) {
            storedSecureSession.value = session
        }

        override suspend fun restoreStoredSession(): StoredRelaySession? = storedSecureSession.value

        override suspend fun restoreSession(): RelaySession? = storedSession.value

        override suspend fun clearSession() {
            storedSession.value = null
            storedSecureSession.value = null
            connectionState.value = RelayConnectionStatus.DISCONNECTED
        }
    }

    override val relayConnectionClient: RelayConnectionClient = object : RelayConnectionClient {
        override val connectionState: Flow<RelayConnectionStatus> = this@InMemorySupervisorRuntime.connectionState

        override suspend fun connect(session: RelaySession) {
            storedSession.value = session
            this@InMemorySupervisorRuntime.connectionState.value = RelayConnectionStatus.CONNECTED
        }

        override suspend fun disconnect() {
            this@InMemorySupervisorRuntime.connectionState.value = RelayConnectionStatus.DISCONNECTED
        }

        override suspend fun ensureConnected() {
            if (storedSession.value != null) {
                this@InMemorySupervisorRuntime.connectionState.value = RelayConnectionStatus.CONNECTED
            }
        }

        override fun sessionStream(sessionId: String): Flow<SessionTimeline> {
            return cache.observeTimeline(sessionId).map { timeline ->
                timeline ?: initialTimeline.copy(
                    sessionId = sessionId,
                    connectionStatus = this@InMemorySupervisorRuntime.connectionState.value,
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
            connectionState.value = RelayConnectionStatus.SYNCING
            connectionState.value = RelayConnectionStatus.CONNECTED
        }

        override suspend fun getProject(projectId: String): ProjectSummary {
            return cache.observeProjects().first().first { it.id == projectId }
        }

        override suspend fun addProject(path: String): ProjectSummary {
            val name = path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifBlank { path }
            val project = ProjectSummary(
                id = "project-${name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}",
                name = name,
                path = path,
            )
            cache.storeProjects(cache.observeProjects().first().filterNot { it.id == project.id } + project)
            return project
        }
    }

    override val inboxRepository: InboxRepository = object : InboxRepository {
        override fun observeInboxItems(): Flow<List<InboxItem>> = cache.observeInboxItems()

        override suspend fun refreshInbox() {
            connectionState.value = RelayConnectionStatus.SYNCING
            connectionState.value = RelayConnectionStatus.CONNECTED
        }
    }

    override val sessionsRepository: SessionsRepository = object : SessionsRepository {
        override fun observeSessions(projectId: String?): Flow<List<SessionSummary>> {
            return cache.observeSessions(projectId)
        }

        override suspend fun refreshSessions(projectId: String?) {
            connectionState.value = RelayConnectionStatus.SYNCING
            connectionState.value = RelayConnectionStatus.CONNECTED
        }

        override suspend fun loadGlobalSessions(
            filters: GlobalSessionFilters,
            after: String?,
            limit: Int,
        ): GlobalSessionsPage {
            val project = filters.project
            val query = filters.query
            val status = filters.status
            val provider = filters.provider
            val executor = filters.executor
            val filtered = cache.observeSessions().first()
                .filter { session -> project == null || session.projectId == project }
                .filter { session -> query == null || session.title.contains(query, ignoreCase = true) }
                .filter { session -> status == null || session.status.name.equals(status, ignoreCase = true) }
                .filter { session -> provider == null || session.provider?.equals(provider, ignoreCase = true) == true }
                .filter { session -> executor == null || session.executor?.equals(executor, ignoreCase = true) == true }
                .filter { session -> filters.includeArchived || !session.isArchived }
                .filter { session -> !filters.starred || session.isStarred }
            val startIndex = after?.let { cursor ->
                filtered.indexOfFirst { it.id == cursor }.takeIf { it >= 0 }?.plus(1)
            } ?: 0
            val page = filtered.drop(startIndex).take(limit)
            cache.storeSessions(
                if (after == null) {
                    page
                } else {
                    (cache.observeSessions().first() + page).distinctBy { it.id }
                },
            )
            return GlobalSessionsPage(
                sessions = page,
                hasMore = startIndex + page.size < filtered.size,
                nextAfter = page.lastOrNull()?.id,
                stats = GlobalSessionStats(
                    total = filtered.size,
                    unread = filtered.count { it.hasUnread },
                    starred = filtered.count { it.isStarred },
                    archived = filtered.count { it.isArchived },
                ),
            )
        }

        override fun observeSessionTimeline(sessionId: String): Flow<SessionTimeline> {
            return cache.observeTimeline(sessionId).map { timeline ->
                timeline ?: initialTimeline.copy(sessionId = sessionId, messages = emptyList())
            }
        }

        override suspend fun loadSessionDetail(
            projectId: String,
            sessionId: String,
            query: SessionDetailQuery,
        ): SessionDetail {
            val session = cache.observeSessions().first().firstOrNull { it.id == sessionId }
                ?: SessionSummary(
                    id = sessionId,
                    projectId = projectId,
                    title = "New session",
                    status = SessionStatus.IDLE,
                    updatedLabel = "just now",
                    hasUnread = false,
                )
            val timeline = cache.observeTimeline(sessionId).first()
                ?: initialTimeline.copy(sessionId = sessionId, messages = emptyList())
            return SessionDetail(
                session = session,
                timeline = timeline,
                ownership = session.ownership ?: "none",
                processState = session.activity,
                permissionMode = null,
                model = session.model,
            )
        }

        override suspend fun loadSessionMetadata(
            projectId: String,
            sessionId: String,
        ): SessionDetail {
            return loadSessionDetail(
                projectId = projectId,
                sessionId = sessionId,
                query = SessionDetailQuery(),
            )
        }

        override suspend fun sendReply(
            sessionId: String,
            text: String,
        ) {
            val current = cache.observeTimeline(sessionId).first()
                ?: initialTimeline.copy(sessionId = sessionId, messages = emptyList())

            val nextMessage = SessionMessage(
                id = "msg-user-${current.messages.size + 1}",
                author = SessionMessageAuthor.USER,
                body = text,
                timestampLabel = "now",
            )
            val updatedTimeline = current.copy(
                connectionStatus = RelayConnectionStatus.CONNECTED,
                messages = current.messages + nextMessage,
            )
            cache.storeTimeline(updatedTimeline)

            val sessions = cache.observeSessions().first().map { summary ->
                if (summary.id == sessionId) {
                    summary.copy(
                        status = SessionStatus.RUNNING,
                        updatedLabel = "just now",
                        hasUnread = false,
                    )
                } else {
                    summary
                }
            }
            cache.storeSessions(sessions)
        }

        override suspend fun updateSessionMetadata(
            sessionId: String,
            updates: SessionMetadataUpdate,
        ): Boolean {
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
            return true
        }

        override suspend fun markSessionSeen(
            sessionId: String,
            timestamp: String?,
            messageId: String?,
        ): Boolean {
            cache.storeSessions(
                cache.observeSessions().first().map { session ->
                    if (session.id == sessionId) session.copy(hasUnread = false) else session
                },
            )
            return true
        }

        override suspend fun markSessionUnread(sessionId: String): Boolean {
            cache.storeSessions(
                cache.observeSessions().first().map { session ->
                    if (session.id == sessionId) session.copy(hasUnread = true) else session
                },
            )
            return true
        }

        override suspend fun getNewSessionSettings(): NewSessionSettings {
            return NewSessionSettings(
                defaults = newSessionDefaults.value,
                remoteExecutors = listOf("local"),
            )
        }

        override suspend fun saveNewSessionDefaults(defaults: NewSessionDefaults): Boolean {
            newSessionDefaults.value = defaults
            return true
        }

        override suspend fun startSession(
            projectId: String,
            prompt: String,
            options: NewSessionOptions,
        ): NewSessionStartResult {
            val sessionId = "session-${projectId}-${cache.observeSessions().first().size + 1}"
            cache.storeSessions(
                cache.observeSessions().first() + SessionSummary(
                    id = sessionId,
                    projectId = projectId,
                    title = prompt.take(48).ifBlank { "New session" },
                    status = SessionStatus.RUNNING,
                    updatedLabel = "just now",
                    hasUnread = false,
                    provider = options.provider,
                    model = options.model,
                    executor = options.executor,
                ),
            )
            return NewSessionStartResult(
                sessionId = sessionId,
                processId = "process-$sessionId",
                permissionMode = options.permissionMode,
                modeVersion = 1,
            )
        }

        override suspend fun createSession(
            projectId: String,
            options: NewSessionOptions,
        ): NewSessionStartResult {
            return startSession(
                projectId = projectId,
                prompt = "New session",
                options = options,
            )
        }

        override suspend fun queueMessage(
            sessionId: String,
            prompt: String,
            options: NewSessionOptions,
        ): Boolean {
            sendReply(sessionId, prompt)
            return true
        }

        override suspend fun queueSessionInput(
            sessionId: String,
            request: SessionInputRequest,
        ): Boolean {
            if (!request.deferred) {
                sendReply(sessionId, request.message)
            }
            return true
        }

        override suspend fun cancelDeferredMessage(sessionId: String, tempId: String): Boolean = true

        override suspend fun setSessionHold(sessionId: String, hold: Boolean): Boolean = hold

        override suspend fun interruptProcess(processId: String): ProcessControlResult {
            return ProcessControlResult(success = true, supported = true)
        }

        override suspend fun abortProcess(processId: String): Boolean = true
    }

    override val approvalsRepository: ApprovalsRepository = object : ApprovalsRepository {
        override fun observePendingApprovals(): Flow<List<PendingInputRequest>> {
            return cache.observePendingRequests()
        }

        override suspend fun approve(requestId: String) {
            resolveRequest(
                requestId = requestId,
                resolutionLabel = "Approved on Android",
            )
        }

        override suspend fun deny(
            requestId: String,
            feedback: String?,
        ) {
            resolveRequest(
                requestId = requestId,
                resolutionLabel = feedback ?: "Denied on Android",
            )
        }

        override suspend fun answerQuestion(
            requestId: String,
            answer: String,
        ) {
            resolveRequest(
                requestId = requestId,
                resolutionLabel = answer,
            )
        }
    }

    private val observeSupervisorShellUseCase = ObserveSupervisorShellUseCase(
        relayConnectionClient = relayConnectionClient,
        projectsRepository = projectsRepository,
        sessionsRepository = sessionsRepository,
        inboxRepository = inboxRepository,
        approvalsRepository = approvalsRepository,
    )

    override val shellState = observeSupervisorShellUseCase(activeSessionId = initialTimeline.sessionId).stateIn(
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

        val projects = cache.observeProjects().first()
        if (projects.none { it.id == projectId }) {
            cache.storeProjects(
                projects + ProjectSummary(
                    id = projectId,
                    name = projectName,
                ),
            )
        }

        val sessions = cache.observeSessions().first()
        cache.storeSessions(
            sessions
                .filterNot { it.id == sessionId }
                .plus(
                    SessionSummary(
                        id = sessionId,
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
                        sessionId = sessionId,
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
                        sessionId = sessionId,
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
        val typedPayload = SupervisorPushPayload.fromFields(payload) ?: return false
        localSupervisorPushPayloads.emit(typedPayload)
        return true
    }

    private suspend fun resolveRequest(
        requestId: String,
        resolutionLabel: String,
    ) {
        val currentRequests = cache.observePendingRequests().first()
        val resolved = currentRequests.firstOrNull { it.id == requestId } ?: return

        cache.storePendingRequests(currentRequests.filterNot { it.id == requestId })

        val currentInbox = cache.observeInboxItems().first().map { item ->
            if (item.id.endsWith(requestId.removePrefix("request-")) || item.sessionId == resolved.sessionId) {
                item.copy(
                    isUnread = false,
                    subtitle = resolutionLabel,
                )
            } else {
                item
            }
        }
        cache.storeInboxItems(currentInbox)

        val currentSessions = cache.observeSessions().first().map { session ->
            if (session.id == resolved.sessionId) {
                session.copy(
                    status = SessionStatus.RUNNING,
                    updatedLabel = "resolved now",
                    hasUnread = false,
                )
            } else {
                session
            }
        }
        cache.storeSessions(currentSessions)

        inboxInvalidations.tryEmit(Unit)
    }
}

fun defaultSupervisorShellSnapshot(): SupervisorShellSnapshot {
    return SupervisorShellSnapshot(
        connectionStatus = RelayConnectionStatus.SYNCING,
        projects = listOf(
            ProjectSummary(
                id = "project-yepanywhere",
                name = "Yep Anywhere",
                isActive = true,
            ),
            ProjectSummary(
                id = "project-relay",
                name = "Relay hardening",
            ),
        ),
        sessions = listOf(
            SessionSummary(
                id = "session-android-shell",
                projectId = "project-yepanywhere",
                title = "Native Android MVP shell",
                status = SessionStatus.RUNNING,
                updatedLabel = "just now",
                hasUnread = false,
            ),
            SessionSummary(
                id = "session-approval",
                projectId = "project-yepanywhere",
                title = "Relay approval pending",
                status = SessionStatus.NEEDS_ATTENTION,
                updatedLabel = "2 min ago",
                hasUnread = true,
            ),
            SessionSummary(
                id = "session-cache",
                projectId = "project-relay",
                title = "Session cache strategy",
                status = SessionStatus.IDLE,
                updatedLabel = "15 min ago",
                hasUnread = false,
            ),
        ),
        inboxItems = listOf(
            InboxItem(
                id = "inbox-request-1",
                projectId = "project-yepanywhere",
                sessionId = "session-approval",
                title = "Approval required",
                subtitle = "Grant network access to relay diagnostics",
                kind = InboxItemKind.APPROVAL,
                isUnread = true,
            ),
            InboxItem(
                id = "inbox-request-2",
                projectId = "project-yepanywhere",
                sessionId = "session-android-shell",
                title = "Question from active session",
                subtitle = "Choose between cached shell states and live sync",
                kind = InboxItemKind.QUESTION,
                isUnread = true,
            ),
            InboxItem(
                id = "inbox-notification-1",
                projectId = "project-relay",
                sessionId = "session-cache",
                title = "Resync complete",
                subtitle = "Cached read-only snapshot refreshed",
                kind = InboxItemKind.NOTIFICATION,
                isUnread = false,
            ),
        ),
        timeline = SessionTimeline(
            sessionId = "session-android-shell",
            connectionStatus = RelayConnectionStatus.CONNECTED,
            messages = listOf(
                SessionMessage(
                    id = "msg-1",
                    author = SessionMessageAuthor.USER,
                    body = "Continue from the Android scaffold and add real shared-core contracts.",
                    timestampLabel = "09:42",
                ),
                SessionMessage(
                    id = "msg-2",
                    author = SessionMessageAuthor.ASSISTANT,
                    body = "Scaffold is in place. Moving next to repository interfaces and a first Supervisor shell.",
                    timestampLabel = "09:43",
                ),
                SessionMessage(
                    id = "msg-3",
                    author = SessionMessageAuthor.SYSTEM,
                    body = "Foreground realtime only. Background updates rely on push plus resync.",
                    timestampLabel = "09:43",
                ),
            ),
        ),
        pendingRequests = listOf(
            PendingInputRequest(
                id = "request-1",
                sessionId = "session-approval",
                title = "Approve relay diagnostics",
                body = "Allow this session to run network diagnostics against the relay host.",
                kind = InboxItemKind.APPROVAL,
            ),
            PendingInputRequest(
                id = "request-2",
                sessionId = "session-android-shell",
                title = "Choose sync strategy",
                body = "Use cache-first read-only snapshots for offline mode?",
                kind = InboxItemKind.QUESTION,
            ),
        ),
    )
}
