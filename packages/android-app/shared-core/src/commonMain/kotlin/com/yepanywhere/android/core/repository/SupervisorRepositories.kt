package com.yepanywhere.android.core.repository

import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import kotlinx.coroutines.flow.Flow

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
}

interface ProjectsRepository {
    fun observeProjects(): Flow<List<ProjectSummary>>

    suspend fun refreshProjects()
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
