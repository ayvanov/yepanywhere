package com.yepanywhere.android.core.repository

import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import com.yepanywhere.android.core.model.SupervisorPushEvent
import com.yepanywhere.android.core.model.SupervisorPushPayload
import kotlinx.coroutines.flow.Flow

interface RelayPushPayloadSource {
    fun payloadStream(): Flow<SupervisorPushPayload>
}

interface RelayAuthRepository {
    suspend fun login(
        username: String,
        password: String,
        relayUrl: String,
    ): RelaySession

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
}

interface InboxRepository {
    fun observeInboxItems(): Flow<List<InboxItem>>

    suspend fun refreshInbox()
}

interface SessionsRepository {
    fun observeSessions(projectId: String? = null): Flow<List<SessionSummary>>

    suspend fun refreshSessions(projectId: String? = null)

    fun observeSessionTimeline(sessionId: String): Flow<SessionTimeline>

    suspend fun sendReply(
        sessionId: String,
        text: String,
    )
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
