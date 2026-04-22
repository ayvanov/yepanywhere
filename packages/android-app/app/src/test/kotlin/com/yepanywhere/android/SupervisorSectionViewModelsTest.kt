package com.yepanywhere.android

import com.yepanywhere.android.data.SupervisorShellDataSource
import com.yepanywhere.android.data.defaultSupervisorShellSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SupervisorSectionViewModelsTest {
    @Test
    fun mapsProjectsSessionsAndInboxIntoDedicatedSectionState() = runTest {
        val source = FakeSupervisorShellDataSource()
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val projectsViewModel = ProjectsScreenViewModel(
            dataSource = source,
            scope = externalScope,
        )
        val sessionsViewModel = SessionsScreenViewModel(
            dataSource = source,
            scope = externalScope,
        )
        val inboxViewModel = InboxScreenViewModel(
            dataSource = source,
            scope = externalScope,
        )
        val collectionJobs = listOf(
            externalScope.launch { projectsViewModel.uiState.collect {} },
            externalScope.launch { sessionsViewModel.uiState.collect {} },
            externalScope.launch { inboxViewModel.uiState.collect {} },
        )

        advanceUntilIdle()

        assertEquals("Projects", projectsViewModel.uiState.value.title)
        assertEquals(source.shellState.value.projects, projectsViewModel.uiState.value.projects)

        assertEquals("Sessions", sessionsViewModel.uiState.value.title)
        assertEquals(source.shellState.value.sessions, sessionsViewModel.uiState.value.sessions)

        assertEquals("Inbox", inboxViewModel.uiState.value.title)
        assertEquals(source.shellState.value.inboxItems, inboxViewModel.uiState.value.items)

        source.shellState.value = source.shellState.value.copy(
            projects = source.shellState.value.projects.take(1),
            sessions = source.shellState.value.sessions.takeLast(1),
            inboxItems = source.shellState.value.inboxItems.take(2),
        )

        advanceUntilIdle()

        assertEquals(1, projectsViewModel.uiState.value.projects.size)
        assertEquals(1, sessionsViewModel.uiState.value.sessions.size)
        assertEquals(2, inboxViewModel.uiState.value.items.size)

        collectionJobs.forEach { it.cancel() }
        externalScope.cancel()
    }

    private class FakeSupervisorShellDataSource : SupervisorShellDataSource {
        override val summary: String = "Android-owned cache and secure relay session persistence."
        override val shellState = MutableStateFlow(defaultSupervisorShellSnapshot())

        override suspend fun connectDemoSession() = Unit
    }
}
