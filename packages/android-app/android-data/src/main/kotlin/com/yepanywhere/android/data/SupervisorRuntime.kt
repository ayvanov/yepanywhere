package com.yepanywhere.android.data

import com.yepanywhere.android.core.model.SupervisorPushEvent
import com.yepanywhere.android.core.model.SupervisorPushPayload
import com.yepanywhere.android.core.model.SupervisorShellSnapshot
import com.yepanywhere.android.core.repository.ApprovalsRepository
import com.yepanywhere.android.core.repository.InboxRepository
import com.yepanywhere.android.core.repository.ProjectsRepository
import com.yepanywhere.android.core.repository.RelayAuthRepository
import com.yepanywhere.android.core.repository.RelayConnectionClient
import com.yepanywhere.android.core.repository.SessionsRepository
import kotlinx.coroutines.flow.StateFlow

interface SupervisorRuntime {
    val shellState: StateFlow<SupervisorShellSnapshot>
    val activeSessionId: String
    val relayAuthRepository: RelayAuthRepository
    val relayConnectionClient: RelayConnectionClient
    val projectsRepository: ProjectsRepository
    val sessionsRepository: SessionsRepository
    val inboxRepository: InboxRepository
    val approvalsRepository: ApprovalsRepository

    suspend fun applyPendingInputNotification(
        sessionId: String,
        projectId: String,
        projectName: String,
        inputType: String,
        summary: String,
        requestId: String,
    )

    suspend fun clearSessionAttention(sessionId: String)

    suspend fun emitSupervisorPushEvent(event: SupervisorPushEvent)

    suspend fun emitSupervisorPushPayload(payload: SupervisorPushPayload)

    suspend fun emitSupervisorPushPayload(payload: Map<String, String>): Boolean
}
