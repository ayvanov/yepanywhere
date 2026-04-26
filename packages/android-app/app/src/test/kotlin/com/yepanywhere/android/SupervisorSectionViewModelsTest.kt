package com.yepanywhere.android

import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.InboxItemKind
import com.yepanywhere.android.core.model.GlobalSessionFilters
import com.yepanywhere.android.core.model.GlobalSessionStats
import com.yepanywhere.android.core.model.GlobalSessionsPage
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.SessionStatus
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import com.yepanywhere.android.core.repository.InboxRepository
import com.yepanywhere.android.core.repository.ProjectsRepository
import com.yepanywhere.android.core.repository.SessionsRepository
import com.yepanywhere.android.core.usecase.ObserveInboxUseCase
import com.yepanywhere.android.core.usecase.ObserveProjectsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SupervisorSectionViewModelsTest {
    @Test
    fun mapsProjectsSessionsAndInboxIntoDedicatedSectionState() = runTest {
        val projects = MutableStateFlow(
            listOf(ProjectSummary(id = "project-yep", name = "Yep Anywhere", isActive = true)),
        )
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
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val projectsViewModel = ProjectsScreenViewModel(
            observeProjectsUseCase = ObserveProjectsUseCase(FakeProjectsRepository(projects)),
            scope = externalScope,
        )
        val sessionsViewModel = SessionsScreenViewModel(
            sessionsRepository = FakeSessionsRepository(
                sessions = sessions,
                timeline = MutableStateFlow(emptyTimeline()),
            ),
            scope = externalScope,
        )
        val inboxViewModel = InboxScreenViewModel(
            observeInboxUseCase = ObserveInboxUseCase(FakeInboxRepository(inboxItems)),
            scope = externalScope,
        )
        val collectionJobs = listOf(
            externalScope.launch { projectsViewModel.uiState.collect {} },
            externalScope.launch { sessionsViewModel.uiState.collect {} },
            externalScope.launch { inboxViewModel.uiState.collect {} },
        )

        advanceUntilIdle()

        assertEquals("Projects", projectsViewModel.uiState.value.title)
        assertEquals(projects.value, projectsViewModel.uiState.value.projects)

        assertEquals("Sessions", sessionsViewModel.uiState.value.title)
        assertEquals(sessions.value, sessionsViewModel.uiState.value.sessions)

        assertEquals("Inbox", inboxViewModel.uiState.value.title)
        assertEquals(inboxItems.value, inboxViewModel.uiState.value.items)

        projects.value = projects.value.take(1)
        sessions.value = sessions.value.takeLast(1)
        inboxItems.value = inboxItems.value + InboxItem(
            id = "inbox-2",
            projectId = "project-yep",
            sessionId = "session-1",
            title = "Question",
            subtitle = "Need a follow-up answer",
            kind = InboxItemKind.QUESTION,
            isUnread = true,
        )

        advanceUntilIdle()

        assertEquals(1, projectsViewModel.uiState.value.projects.size)
        assertEquals(1, sessionsViewModel.uiState.value.sessions.size)
        assertEquals(2, inboxViewModel.uiState.value.items.size)

        collectionJobs.forEach { it.cancel() }
        externalScope.cancel()
    }

    @Test
    fun sessionsViewModelLoadsFilteredGlobalPagesAndRunsBulkActions() = runTest {
        val sessions = MutableStateFlow(emptyList<SessionSummary>())
        val repository = FakeSessionsRepository(
            sessions = sessions,
            timeline = MutableStateFlow(emptyTimeline()),
            pages = listOf(
                GlobalSessionsPage(
                    sessions = listOf(
                        sessionSummary(
                            id = "session-1",
                            title = "Android shell",
                            provider = "claude",
                            executor = "local",
                        ),
                    ),
                    hasMore = true,
                    nextAfter = "session-1",
                    stats = GlobalSessionStats(total = 2, unread = 1, starred = 0, archived = 0),
                ),
                GlobalSessionsPage(
                    sessions = listOf(
                        sessionSummary(
                            id = "session-2",
                            title = "Relay work",
                            provider = "codex",
                            executor = "remote",
                        ),
                    ),
                    hasMore = false,
                    nextAfter = null,
                    stats = GlobalSessionStats(total = 2, unread = 1, starred = 0, archived = 0),
                ),
            ),
        )
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val viewModel = SessionsScreenViewModel(
            sessionsRepository = repository,
            scope = externalScope,
        )
        val collectionJob = externalScope.launch { viewModel.uiState.collect {} }

        viewModel.applyFilters(
            project = "project-yep",
            query = "android",
            status = "running",
            provider = "claude",
            executor = "local",
            age = "24h",
            includeArchived = true,
            starred = false,
        )
        advanceUntilIdle()

        assertEquals(
            GlobalSessionFilters(
                project = "project-yep",
                query = "android",
                status = "running",
                provider = "claude",
                executor = "local",
                age = "24h",
                includeArchived = true,
            ),
            viewModel.uiState.value.filters,
        )
        assertEquals(listOf("session-1"), viewModel.uiState.value.sessions.map { it.id })
        assertTrue(viewModel.uiState.value.hasMore)

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf("session-1", "session-2"), viewModel.uiState.value.sessions.map { it.id })
        assertEquals("session-1", repository.lastAfter)

        viewModel.toggleSelection("session-1")
        viewModel.toggleSelection("session-2")
        viewModel.bulkArchiveSelected()
        viewModel.toggleSelection("session-1")
        viewModel.bulkStarSelected()
        viewModel.toggleSelection("session-2")
        viewModel.bulkMarkReadSelected()
        viewModel.toggleSelection("session-1")
        viewModel.bulkMarkUnreadSelected()
        advanceUntilIdle()

        assertEquals(setOf("session-1", "session-2"), repository.archived)
        assertEquals(setOf("session-1"), repository.starred)
        assertEquals(setOf("session-2"), repository.read)
        assertEquals(setOf("session-1"), repository.unread)
        assertEquals(emptySet(), viewModel.uiState.value.selectedSessionIds)

        collectionJob.cancel()
        externalScope.cancel()
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
        private val pages: List<GlobalSessionsPage> = emptyList(),
    ) : SessionsRepository {
        private var pageIndex = 0
        var lastAfter: String? = null
        val archived = mutableSetOf<String>()
        val starred = mutableSetOf<String>()
        val read = mutableSetOf<String>()
        val unread = mutableSetOf<String>()

        override fun observeSessions(projectId: String?): Flow<List<SessionSummary>> = sessions

        override suspend fun refreshSessions(projectId: String?) = Unit

        override suspend fun loadGlobalSessions(
            filters: GlobalSessionFilters,
            after: String?,
            limit: Int,
        ): GlobalSessionsPage {
            lastAfter = after
            return pages.getOrElse(pageIndex++) { GlobalSessionsPage(sessions = emptyList(), hasMore = false) }
        }

        override fun observeSessionTimeline(sessionId: String): Flow<SessionTimeline> = timeline

        override suspend fun sendReply(sessionId: String, text: String) = Unit

        override suspend fun bulkArchive(sessionIds: Set<String>, archived: Boolean) {
            if (archived) {
                this.archived += sessionIds
            } else {
                this.archived -= sessionIds
            }
        }

        override suspend fun bulkStar(sessionIds: Set<String>, starred: Boolean) {
            if (starred) {
                this.starred += sessionIds
            } else {
                this.starred -= sessionIds
            }
        }

        override suspend fun bulkMarkSeen(sessionIds: Set<String>) {
            read += sessionIds
        }

        override suspend fun bulkMarkUnread(sessionIds: Set<String>) {
            unread += sessionIds
        }
    }

    private class FakeInboxRepository(
        private val inboxItems: MutableStateFlow<List<InboxItem>>,
    ) : InboxRepository {
        override fun observeInboxItems(): Flow<List<InboxItem>> = inboxItems

        override suspend fun refreshInbox() = Unit
    }

    private fun emptyTimeline(): SessionTimeline {
        return SessionTimeline(
            sessionId = "session-1",
            connectionStatus = com.yepanywhere.android.core.model.RelayConnectionStatus.CONNECTED,
            messages = emptyList(),
        )
    }

    private fun sessionSummary(
        id: String,
        title: String,
        provider: String,
        executor: String,
    ): SessionSummary {
        return SessionSummary(
            id = id,
            projectId = "project-yep",
            title = title,
            status = SessionStatus.RUNNING,
            updatedLabel = "now",
            hasUnread = false,
            provider = provider,
            executor = executor,
        )
    }
}
