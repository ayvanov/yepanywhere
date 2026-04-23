package com.yepanywhere.android

import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.SessionTimeline
import com.yepanywhere.android.core.repository.InboxRepository
import com.yepanywhere.android.core.repository.ProjectsRepository
import com.yepanywhere.android.core.repository.SessionsRepository
import com.yepanywhere.android.ui.SupervisorShellSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidNotificationRouteResyncOrchestratorTest {
    @Test
    fun activeRouteRefreshesSessionsInboxAndTimelineWhenConnected() = runTest {
        val calls = mutableListOf<String>()
        val orchestrator = createOrchestrator(
            ensureConnected = { calls += "ensureConnected" },
            refreshSessions = { projectId -> calls += "refreshSessions:$projectId" },
            refreshInbox = { calls += "refreshInbox" },
            sessionStream = { sessionId ->
                calls += "sessionStream:$sessionId"
                flowOf(
                    SessionTimeline(
                        sessionId = sessionId,
                        connectionStatus = RelayConnectionStatus.CONNECTED,
                        messages = emptyList(),
                    ),
                )
            },
            activeSessionId = { "active-session" },
        )

        assertTrue(
            orchestrator.resync(
                AndroidNotificationRoute(
                    section = SupervisorShellSection.ACTIVE,
                    sessionId = "session-1",
                    projectId = "project-1",
                ),
            ),
        )
        assertEquals(
            listOf(
                "ensureConnected",
                "refreshSessions:project-1",
                "refreshInbox",
                "sessionStream:session-1",
            ),
            calls,
        )
    }

    @Test
    fun returnsFalseWhenReconnectCannotRestoreConnection() = runTest {
        val calls = mutableListOf<String>()
        val orchestrator = createOrchestrator(
            reconnectPersistedSession = {
                calls += "reconnect"
                false
            },
            ensureConnected = {
                calls += "ensureConnected"
                error("offline")
            },
            refreshProjects = { calls += "refreshProjects" },
        )

        assertFalse(
            orchestrator.resync(
                AndroidNotificationRoute(
                    section = SupervisorShellSection.PROJECTS,
                ),
            ),
        )
        assertEquals(
            listOf("ensureConnected", "reconnect"),
            calls,
        )
    }

    private fun createOrchestrator(
        reconnectPersistedSession: suspend () -> Boolean = { true },
        ensureConnected: suspend () -> Unit = {},
        refreshProjects: suspend () -> Unit = {},
        refreshSessions: suspend (String?) -> Unit = {},
        refreshInbox: suspend () -> Unit = {},
        sessionStream: (String) -> Flow<SessionTimeline> = { emptyFlow() },
        activeSessionId: () -> String = { "active-session" },
    ): AndroidNotificationRouteResyncOrchestrator {
        val projectsRepository = object : ProjectsRepository {
            override fun observeProjects() = emptyFlow<List<com.yepanywhere.android.core.model.ProjectSummary>>()

            override suspend fun refreshProjects() {
                refreshProjects()
            }
        }
        val sessionsRepository = object : SessionsRepository {
            override fun observeSessions(projectId: String?) = emptyFlow<List<com.yepanywhere.android.core.model.SessionSummary>>()

            override suspend fun refreshSessions(projectId: String?) {
                refreshSessions(projectId)
            }

            override fun observeSessionTimeline(sessionId: String) = emptyFlow<SessionTimeline>()

            override suspend fun sendReply(
                sessionId: String,
                text: String,
            ) = Unit
        }
        val inboxRepository = object : InboxRepository {
            override fun observeInboxItems() = emptyFlow<List<com.yepanywhere.android.core.model.InboxItem>>()

            override suspend fun refreshInbox() {
                refreshInbox()
            }
        }

        return AndroidNotificationRouteResyncOrchestrator(
            reconnectPersistedSession = reconnectPersistedSession,
            ensureConnected = ensureConnected,
            projectsRepository = projectsRepository,
            sessionsRepository = sessionsRepository,
            inboxRepository = inboxRepository,
            sessionStream = sessionStream,
            activeSessionId = activeSessionId,
        )
    }
}
