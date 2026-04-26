package com.yepanywhere.android.core.repository

import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.GlobalSessionFilters
import com.yepanywhere.android.core.model.GlobalSessionsPage
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.SessionMetadataUpdate
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
}

interface ApprovalsRepository {
    fun observePendingApprovals(): Flow<List<PendingInputRequest>>

    suspend fun approve(requestId: String)

    suspend fun deny(
        requestId: String,
        feedback: String? = null,
    )

    suspend fun answerQuestion(
        requestId: String,
        answer: String,
    )
}
