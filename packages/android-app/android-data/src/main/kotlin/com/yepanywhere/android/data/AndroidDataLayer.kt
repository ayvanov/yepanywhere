package com.yepanywhere.android.data

import com.yepanywhere.android.core.model.SupervisorShellSnapshot
import com.yepanywhere.android.core.repository.ApprovalsRepository
import com.yepanywhere.android.core.repository.InboxRepository
import com.yepanywhere.android.core.repository.ProjectsRepository
import com.yepanywhere.android.core.repository.RelayConnectionClient
import com.yepanywhere.android.core.repository.SessionsRepository
import kotlinx.coroutines.flow.StateFlow

interface SupervisorShellDataSource {
    val summary: String
    val shellState: StateFlow<SupervisorShellSnapshot>

    suspend fun connectDemoSession()
}

interface SupervisorFeatureDependencies {
    val activeSessionId: String
    val projectsRepository: ProjectsRepository
    val sessionsRepository: SessionsRepository
    val inboxRepository: InboxRepository
    val approvalsRepository: ApprovalsRepository
}

class AndroidDataLayer(
    private val runtime: InMemorySupervisorRuntime = InMemorySupervisorRuntime(),
) : SupervisorShellDataSource, SupervisorFeatureDependencies {
    override val summary: String = SUMMARY

    override val shellState = runtime.shellState
    override val activeSessionId: String = runtime.activeSessionId
    override val projectsRepository: ProjectsRepository = runtime.projectsRepository
    override val sessionsRepository: SessionsRepository = runtime.sessionsRepository
    override val inboxRepository: InboxRepository = runtime.inboxRepository
    override val approvalsRepository: ApprovalsRepository = runtime.approvalsRepository
    val relayConnectionClient: RelayConnectionClient = runtime.relayConnectionClient

    override suspend fun connectDemoSession() {
        runtime.connectDemoSession()
    }

    suspend fun applyPendingInputNotification(
        sessionId: String,
        projectId: String,
        projectName: String,
        inputType: String,
        summary: String,
        requestId: String,
    ) {
        runtime.applyPendingInputNotification(
            sessionId = sessionId,
            projectId = projectId,
            projectName = projectName,
            inputType = inputType,
            summary = summary,
            requestId = requestId,
        )
    }

    suspend fun clearSessionAttention(sessionId: String) {
        runtime.clearSessionAttention(sessionId)
    }

    suspend fun emitSupervisorPushEvent(payload: Map<String, String>) {
        runtime.emitSupervisorPushEvent(payload)
    }

    companion object {
        const val SUMMARY: String =
            "Android-owned cache/storage layer for Room, DataStore, and secure relay session persistence."
    }
}
