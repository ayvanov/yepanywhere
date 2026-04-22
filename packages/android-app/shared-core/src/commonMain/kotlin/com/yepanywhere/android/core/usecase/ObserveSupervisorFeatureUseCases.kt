package com.yepanywhere.android.core.usecase

import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import com.yepanywhere.android.core.repository.ApprovalsRepository
import com.yepanywhere.android.core.repository.InboxRepository
import com.yepanywhere.android.core.repository.ProjectsRepository
import com.yepanywhere.android.core.repository.SessionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class ObserveProjectsUseCase(
    private val projectsRepository: ProjectsRepository,
) {
    operator fun invoke(): Flow<List<ProjectSummary>> = projectsRepository.observeProjects()
}

class ObserveSessionsUseCase(
    private val sessionsRepository: SessionsRepository,
) {
    operator fun invoke(projectId: String? = null): Flow<List<SessionSummary>> {
        return sessionsRepository.observeSessions(projectId)
    }
}

class ObserveInboxUseCase(
    private val inboxRepository: InboxRepository,
) {
    operator fun invoke(): Flow<List<InboxItem>> = inboxRepository.observeInboxItems()
}

data class ActiveSessionSnapshot(
    val timeline: SessionTimeline,
    val pendingRequests: List<PendingInputRequest>,
)

class ObserveActiveSessionUseCase(
    private val sessionsRepository: SessionsRepository,
    private val approvalsRepository: ApprovalsRepository,
) {
    operator fun invoke(sessionId: String): Flow<ActiveSessionSnapshot> {
        val timeline = sessionsRepository.observeSessionTimeline(sessionId)
        val pendingRequests = approvalsRepository.observePendingApprovals().map { requests ->
            requests.filter { request -> request.sessionId == sessionId }
        }

        return combine(timeline, pendingRequests) { activeTimeline, activeRequests ->
            ActiveSessionSnapshot(
                timeline = activeTimeline,
                pendingRequests = activeRequests,
            )
        }
    }
}
