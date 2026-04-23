package com.yepanywhere.android

import com.yepanywhere.android.data.AndroidDataLayer
import com.yepanywhere.android.core.model.SessionTimeline
import com.yepanywhere.android.core.repository.InboxRepository
import com.yepanywhere.android.core.repository.ProjectsRepository
import com.yepanywhere.android.core.repository.RelayConnectionClient
import com.yepanywhere.android.core.repository.SessionsRepository
import com.yepanywhere.android.ui.SupervisorShellSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AndroidNotificationRouteResyncOrchestrator(
    private val reconnectPersistedSession: suspend () -> Boolean,
    private val ensureConnected: suspend () -> Unit,
    private val projectsRepository: ProjectsRepository,
    private val sessionsRepository: SessionsRepository,
    private val inboxRepository: InboxRepository,
    private val sessionStream: (String) -> Flow<SessionTimeline>,
    private val activeSessionId: () -> String,
) {
    constructor(
        dataLayer: AndroidDataLayer,
        relayConnectionClient: RelayConnectionClient = dataLayer.relayConnectionClient,
        projectsRepository: ProjectsRepository = dataLayer.projectsRepository,
        sessionsRepository: SessionsRepository = dataLayer.sessionsRepository,
        inboxRepository: InboxRepository = dataLayer.inboxRepository,
    ) : this(
        reconnectPersistedSession = dataLayer::reconnectPersistedSession,
        ensureConnected = relayConnectionClient::ensureConnected,
        projectsRepository = projectsRepository,
        sessionsRepository = sessionsRepository,
        inboxRepository = inboxRepository,
        sessionStream = relayConnectionClient::sessionStream,
        activeSessionId = { dataLayer.activeSessionId },
    )

    suspend fun resync(route: AndroidNotificationRoute): Boolean {
        if (!ensureConnectionReady()) {
            return false
        }

        when (route.section) {
            SupervisorShellSection.PROJECTS -> projectsRepository.refreshProjects()
            SupervisorShellSection.SESSIONS -> sessionsRepository.refreshSessions(route.projectId)
            SupervisorShellSection.INBOX -> {
                inboxRepository.refreshInbox()
                route.sessionId?.let { sessionId ->
                    sessionStream(sessionId).first()
                }
            }

            SupervisorShellSection.ACTIVE -> {
                val targetSessionId = route.sessionId ?: activeSessionId()
                sessionsRepository.refreshSessions(route.projectId)
                inboxRepository.refreshInbox()
                sessionStream(targetSessionId).first()
            }
        }
        return true
    }

    private suspend fun ensureConnectionReady(): Boolean {
        if (runCatching { ensureConnected() }.isSuccess) {
            return true
        }
        if (!reconnectPersistedSession()) {
            return false
        }
        return runCatching { ensureConnected() }.isSuccess
    }
}
