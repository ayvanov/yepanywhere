package com.yepanywhere.android.core.usecase

import com.yepanywhere.android.core.model.SupervisorShellSnapshot
import com.yepanywhere.android.core.repository.ApprovalsRepository
import com.yepanywhere.android.core.repository.InboxRepository
import com.yepanywhere.android.core.repository.ProjectsRepository
import com.yepanywhere.android.core.repository.RelayConnectionClient
import com.yepanywhere.android.core.repository.SessionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveSupervisorShellUseCase(
    private val relayConnectionClient: RelayConnectionClient,
    private val projectsRepository: ProjectsRepository,
    private val sessionsRepository: SessionsRepository,
    private val inboxRepository: InboxRepository,
    private val approvalsRepository: ApprovalsRepository,
) {
    private data class ShellLists(
        val connectionStatus: com.yepanywhere.android.core.model.RelayConnectionStatus,
        val projects: List<com.yepanywhere.android.core.model.ProjectSummary>,
        val sessions: List<com.yepanywhere.android.core.model.SessionSummary>,
        val inboxItems: List<com.yepanywhere.android.core.model.InboxItem>,
    )

    operator fun invoke(activeSessionId: String): Flow<SupervisorShellSnapshot> {
        val shellLists = combine(
            relayConnectionClient.connectionState,
            projectsRepository.observeProjects(),
            sessionsRepository.observeSessions(),
            inboxRepository.observeInboxItems(),
        ) { connectionStatus, projects, sessions, inboxItems ->
            ShellLists(
                connectionStatus = connectionStatus,
                projects = projects,
                sessions = sessions,
                inboxItems = inboxItems,
            )
        }

        return combine(
            shellLists,
            sessionsRepository.observeSessionTimeline(activeSessionId),
            approvalsRepository.observePendingApprovals(),
        ) { lists, timeline, pendingRequests ->
            SupervisorShellSnapshot(
                connectionStatus = lists.connectionStatus,
                projects = lists.projects,
                sessions = lists.sessions,
                inboxItems = lists.inboxItems,
                timeline = timeline,
                pendingRequests = pendingRequests,
            )
        }
    }
}
