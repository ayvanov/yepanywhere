package com.yepanywhere.android.core.repository

import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.AgentMapping
import com.yepanywhere.android.core.model.AgentProcessesPage
import com.yepanywhere.android.core.model.AgentSession
import com.yepanywhere.android.core.model.FileContent
import com.yepanywhere.android.core.model.GitDiffResult
import com.yepanywhere.android.core.model.GitStatusInfo
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.ProcessControlResult
import com.yepanywhere.android.core.model.ProcessModelOption
import com.yepanywhere.android.core.model.ProcessModelSwitchResult
import com.yepanywhere.android.core.model.GlobalSessionFilters
import com.yepanywhere.android.core.model.GlobalSessionsPage
import com.yepanywhere.android.core.model.NewSessionDefaults
import com.yepanywhere.android.core.model.NewSessionOptions
import com.yepanywhere.android.core.model.NewSessionSettings
import com.yepanywhere.android.core.model.NewSessionStartResult
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.SessionMetadataUpdate
import com.yepanywhere.android.core.model.SessionDetail
import com.yepanywhere.android.core.model.SessionDetailQuery
import com.yepanywhere.android.core.model.SessionInputRequest
import com.yepanywhere.android.core.model.SessionProcessInfo
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.core.model.SupervisorPushEvent
import com.yepanywhere.android.core.model.SupervisorPushPayload
import kotlinx.coroutines.flow.Flow

interface RelayPushPayloadSource {
    fun payloadStream(): Flow<SupervisorPushPayload>
}

interface RelayAuthRepository {
    suspend fun login(
        username: String,
        password: String?,
        relayUrl: String,
    ): RelaySession

    suspend fun persistStoredSession(session: StoredRelaySession)

    suspend fun restoreStoredSession(): StoredRelaySession?

    suspend fun restoreSession(): RelaySession?

    suspend fun clearSession()
}

interface RelayConnectionClient {
    val connectionState: Flow<RelayConnectionStatus>

    suspend fun connect(session: RelaySession)

    suspend fun disconnect()

    suspend fun ensureConnected()

    fun sessionStream(sessionId: String): Flow<SessionTimeline>

    fun inboxInvalidationStream(): Flow<Unit>

    fun supervisorPushEventStream(): Flow<SupervisorPushEvent>
}

interface ProjectsRepository {
    fun observeProjects(): Flow<List<ProjectSummary>>

    suspend fun refreshProjects()

    suspend fun getProject(projectId: String): ProjectSummary {
        throw NotImplementedError("Project detail is not implemented by this repository")
    }

    suspend fun addProject(path: String): ProjectSummary {
        throw NotImplementedError("Add project is not implemented by this repository")
    }
}

interface InboxRepository {
    fun observeInboxItems(): Flow<List<InboxItem>>

    suspend fun refreshInbox()
}

interface FilesRepository {
    suspend fun loadFile(
        projectId: String,
        path: String,
        highlight: Boolean = false,
    ): FileContent
}

interface GitRepository {
    suspend fun loadGitStatus(projectId: String): GitStatusInfo

    suspend fun loadGitDiff(
        projectId: String,
        path: String,
        staged: Boolean,
        status: String,
        fullContext: Boolean = false,
    ): GitDiffResult

    suspend fun expandDiffContext(
        projectId: String,
        filePath: String,
        oldString: String,
        newString: String,
        originalFile: String,
    ): GitDiffResult
}

interface SessionsRepository {
    fun observeSessions(projectId: String? = null): Flow<List<SessionSummary>>

    suspend fun refreshSessions(projectId: String? = null)

    suspend fun loadGlobalSessions(
        filters: GlobalSessionFilters = GlobalSessionFilters(),
        after: String? = null,
        limit: Int = 50,
    ): GlobalSessionsPage {
        throw NotImplementedError("Global sessions are not implemented by this repository")
    }

    fun observeSessionTimeline(sessionId: String): Flow<SessionTimeline>

    suspend fun loadSessionDetail(
        projectId: String,
        sessionId: String,
        query: SessionDetailQuery = SessionDetailQuery(),
    ): SessionDetail {
        throw NotImplementedError("Session detail is not implemented by this repository")
    }

    suspend fun loadSessionMetadata(
        projectId: String,
        sessionId: String,
    ): SessionDetail {
        throw NotImplementedError("Session metadata detail is not implemented by this repository")
    }

    suspend fun sendReply(
        sessionId: String,
        text: String,
    )

    suspend fun updateSessionMetadata(
        sessionId: String,
        updates: SessionMetadataUpdate,
    ): Boolean {
        throw NotImplementedError("Session metadata updates are not implemented by this repository")
    }

    suspend fun markSessionSeen(
        sessionId: String,
        timestamp: String? = null,
        messageId: String? = null,
    ): Boolean {
        throw NotImplementedError("Session read-state updates are not implemented by this repository")
    }

    suspend fun markSessionUnread(sessionId: String): Boolean {
        throw NotImplementedError("Session unread-state updates are not implemented by this repository")
    }

    suspend fun bulkArchive(sessionIds: Set<String>, archived: Boolean) {
        sessionIds.forEach { sessionId ->
            updateSessionMetadata(sessionId, SessionMetadataUpdate(archived = archived))
        }
    }

    suspend fun bulkStar(sessionIds: Set<String>, starred: Boolean) {
        sessionIds.forEach { sessionId ->
            updateSessionMetadata(sessionId, SessionMetadataUpdate(starred = starred))
        }
    }

    suspend fun bulkMarkSeen(sessionIds: Set<String>) {
        sessionIds.forEach { sessionId ->
            markSessionSeen(sessionId)
        }
    }

    suspend fun bulkMarkUnread(sessionIds: Set<String>) {
        sessionIds.forEach { sessionId ->
            markSessionUnread(sessionId)
        }
    }

    suspend fun getNewSessionSettings(): NewSessionSettings {
        throw NotImplementedError("New session settings are not implemented by this repository")
    }

    suspend fun saveNewSessionDefaults(defaults: NewSessionDefaults): Boolean {
        throw NotImplementedError("New session defaults are not implemented by this repository")
    }

    suspend fun startSession(
        projectId: String,
        prompt: String,
        options: NewSessionOptions = NewSessionOptions(),
    ): NewSessionStartResult {
        throw NotImplementedError("New session start is not implemented by this repository")
    }

    suspend fun createSession(
        projectId: String,
        options: NewSessionOptions = NewSessionOptions(),
    ): NewSessionStartResult {
        throw NotImplementedError("Session creation is not implemented by this repository")
    }

    suspend fun queueMessage(
        sessionId: String,
        prompt: String,
        options: NewSessionOptions = NewSessionOptions(),
    ): Boolean {
        throw NotImplementedError("Session message queueing is not implemented by this repository")
    }

    suspend fun queueSessionInput(
        sessionId: String,
        request: SessionInputRequest,
    ): Boolean {
        return queueMessage(
            sessionId = sessionId,
            prompt = request.message,
            options = NewSessionOptions(
                permissionMode = request.mode,
                thinking = request.thinking,
            ),
        )
    }

    suspend fun cancelDeferredMessage(
        sessionId: String,
        tempId: String,
    ): Boolean {
        throw NotImplementedError("Deferred message cancellation is not implemented by this repository")
    }

    suspend fun setSessionHold(
        sessionId: String,
        hold: Boolean,
    ): Boolean {
        throw NotImplementedError("Session hold is not implemented by this repository")
    }

    suspend fun interruptProcess(processId: String): ProcessControlResult {
        throw NotImplementedError("Process interrupt is not implemented by this repository")
    }

    suspend fun abortProcess(processId: String): Boolean {
        throw NotImplementedError("Process abort is not implemented by this repository")
    }

    suspend fun getProcessInfo(sessionId: String): SessionProcessInfo? {
        throw NotImplementedError("Process info is not implemented by this repository")
    }

    suspend fun getProcessModels(processId: String): List<ProcessModelOption> {
        throw NotImplementedError("Process models are not implemented by this repository")
    }

    suspend fun setProcessModel(
        processId: String,
        model: String?,
    ): ProcessModelSwitchResult {
        throw NotImplementedError("Process model switching is not implemented by this repository")
    }

    suspend fun loadAgentProcesses(includeTerminated: Boolean = true): AgentProcessesPage {
        throw NotImplementedError("Agent process listing is not implemented by this repository")
    }

    suspend fun loadAgentMappings(
        projectId: String,
        sessionId: String,
    ): List<AgentMapping> {
        throw NotImplementedError("Agent mappings are not implemented by this repository")
    }

    suspend fun loadAgentSession(
        projectId: String,
        sessionId: String,
        agentId: String,
    ): AgentSession? {
        throw NotImplementedError("Agent session fetch is not implemented by this repository")
    }
}

interface ApprovalsRepository {
    fun observePendingApprovals(): Flow<List<PendingInputRequest>>

    suspend fun approve(requestId: String)

    suspend fun approveAcceptEdits(requestId: String) {
        approve(requestId)
    }

    suspend fun deny(
        requestId: String,
        feedback: String? = null,
    )

    suspend fun answerQuestion(
        requestId: String,
        answer: String,
    )
}
