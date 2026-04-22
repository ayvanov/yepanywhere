package com.yepanywhere.android.core.usecase

import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.InboxItemKind
import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.SessionMessage
import com.yepanywhere.android.core.model.SessionMessageAuthor
import com.yepanywhere.android.core.model.SessionStatus
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import com.yepanywhere.android.core.repository.ApprovalsRepository
import com.yepanywhere.android.core.repository.InboxRepository
import com.yepanywhere.android.core.repository.ProjectsRepository
import com.yepanywhere.android.core.repository.SessionsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveSupervisorFeaturesUseCasesTest {
    @Test
    fun observeProjectsReturnsRepositoryProjects() = runTest(UnconfinedTestDispatcher()) {
        val projects = MutableStateFlow(
            listOf(ProjectSummary(id = "project-yep", name = "Yep Anywhere", isActive = true)),
        )

        val useCase = ObserveProjectsUseCase(
            projectsRepository = FakeProjectsRepository(projects),
        )

        assertEquals(projects.value, useCase().first())
    }

    @Test
    fun observeSessionsReturnsRepositorySessions() = runTest(UnconfinedTestDispatcher()) {
        val sessions = MutableStateFlow(
            listOf(
                SessionSummary(
                    id = "session-1",
                    projectId = "project-yep",
                    title = "Android shell",
                    status = SessionStatus.RUNNING,
                    updatedLabel = "now",
                    hasUnread = false,
                ),
            ),
        )

        val useCase = ObserveSessionsUseCase(
            sessionsRepository = FakeSessionsRepository(
                sessions = sessions,
                timeline = MutableStateFlow(emptyTimeline()),
            ),
        )

        assertEquals(sessions.value, useCase().first())
    }

    @Test
    fun observeInboxReturnsRepositoryInboxItems() = runTest(UnconfinedTestDispatcher()) {
        val inboxItems = MutableStateFlow(
            listOf(
                InboxItem(
                    id = "inbox-1",
                    projectId = "project-yep",
                    sessionId = "session-1",
                    title = "Approval required",
                    subtitle = "Review network permission request",
                    kind = InboxItemKind.APPROVAL,
                    isUnread = true,
                ),
            ),
        )

        val useCase = ObserveInboxUseCase(
            inboxRepository = FakeInboxRepository(inboxItems),
        )

        assertEquals(inboxItems.value, useCase().first())
    }

    @Test
    fun observeActiveSessionCombinesTimelineAndPendingRequests() = runTest(UnconfinedTestDispatcher()) {
        val pendingRequests = MutableStateFlow(
            listOf(
                PendingInputRequest(
                    id = "request-1",
                    sessionId = "session-1",
                    title = "Grant network access",
                    body = "Allow relay diagnostics to run?",
                    kind = InboxItemKind.APPROVAL,
                ),
            ),
        )
        val timeline = MutableStateFlow(
            SessionTimeline(
                sessionId = "session-1",
                connectionStatus = com.yepanywhere.android.core.model.RelayConnectionStatus.CONNECTED,
                messages = listOf(
                    SessionMessage(
                        id = "msg-1",
                        author = SessionMessageAuthor.ASSISTANT,
                        body = "Session detail is ready for extraction.",
                        timestampLabel = "09:45",
                    ),
                ),
            ),
        )

        val useCase = ObserveActiveSessionUseCase(
            sessionsRepository = FakeSessionsRepository(
                sessions = MutableStateFlow(emptyList()),
                timeline = timeline,
            ),
            approvalsRepository = FakeApprovalsRepository(pendingRequests),
        )

        val activeSession = useCase(sessionId = "session-1").first()

        assertEquals(timeline.value, activeSession.timeline)
        assertEquals(pendingRequests.value, activeSession.pendingRequests)
    }

    private class FakeProjectsRepository(
        private val projects: MutableStateFlow<List<ProjectSummary>>,
    ) : ProjectsRepository {
        override fun observeProjects(): Flow<List<ProjectSummary>> = projects

        override suspend fun refreshProjects() = Unit
    }

    private class FakeSessionsRepository(
        private val sessions: MutableStateFlow<List<SessionSummary>>,
        private val timeline: MutableStateFlow<SessionTimeline>,
    ) : SessionsRepository {
        override fun observeSessions(projectId: String?): Flow<List<SessionSummary>> = sessions

        override suspend fun refreshSessions(projectId: String?) = Unit

        override fun observeSessionTimeline(sessionId: String): Flow<SessionTimeline> = timeline

        override suspend fun sendReply(sessionId: String, text: String) = Unit
    }

    private class FakeInboxRepository(
        private val inboxItems: MutableStateFlow<List<InboxItem>>,
    ) : InboxRepository {
        override fun observeInboxItems(): Flow<List<InboxItem>> = inboxItems

        override suspend fun refreshInbox() = Unit
    }

    private class FakeApprovalsRepository(
        private val pendingRequests: MutableStateFlow<List<PendingInputRequest>>,
    ) : ApprovalsRepository {
        override fun observePendingApprovals(): Flow<List<PendingInputRequest>> = pendingRequests

        override suspend fun approve(requestId: String) = Unit

        override suspend fun deny(requestId: String, feedback: String?) = Unit

        override suspend fun answerQuestion(requestId: String, answer: String) = Unit
    }

    private fun emptyTimeline(): SessionTimeline {
        return SessionTimeline(
            sessionId = "session-1",
            connectionStatus = com.yepanywhere.android.core.model.RelayConnectionStatus.CONNECTED,
            messages = emptyList(),
        )
    }
}
