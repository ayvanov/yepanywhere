package com.yepanywhere.android

import com.yepanywhere.android.data.SupervisorShellDataSource
import com.yepanywhere.android.data.defaultSupervisorShellSnapshot
import com.yepanywhere.android.ui.SupervisorShellSection
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
class SupervisorShellViewModelTest {
    @Test
    fun mapsDataSourceSnapshotIntoScreenStateAndTracksSelectedSection() = runTest {
        val source = FakeSupervisorShellDataSource()
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val viewModel = SupervisorShellViewModel(
            dataSource = source,
            scope = externalScope,
        )
        val collectionJob = externalScope.launch {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()

        assertEquals("Yep Anywhere Android", viewModel.uiState.value.title)
        assertEquals(source.summary, viewModel.uiState.value.subtitle)
        assertEquals(SupervisorShellSection.PROJECTS, viewModel.uiState.value.selectedSection)
        assertEquals(source.shellState.value, viewModel.uiState.value.snapshot)

        source.shellState.value = source.shellState.value.copy(
            pendingRequests = emptyList(),
        )
        viewModel.selectSection(SupervisorShellSection.INBOX)

        advanceUntilIdle()

        assertEquals(SupervisorShellSection.INBOX, viewModel.uiState.value.selectedSection)
        assertEquals(0, viewModel.uiState.value.snapshot.pendingRequests.size)

        collectionJob.cancel()
        externalScope.cancel()
    }

    @Test
    fun selectingProjectOpensSessionsFilteredToThatProject() = runTest {
        val source = FakeSupervisorShellDataSource()
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val viewModel = SupervisorShellViewModel(
            dataSource = source,
            scope = externalScope,
        )
        val collectionJob = externalScope.launch {
            viewModel.uiState.collect {}
        }

        viewModel.selectProject("project-yepanywhere")

        advanceUntilIdle()

        assertEquals(SupervisorShellSection.SESSIONS, viewModel.uiState.value.selectedSection)
        assertEquals("project-yepanywhere", viewModel.uiState.value.selectedProjectId)

        collectionJob.cancel()
        externalScope.cancel()
    }

    @Test
    fun selectingSessionOpensSessionDetailWithRouteArguments() = runTest {
        val source = FakeSupervisorShellDataSource()
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val viewModel = SupervisorShellViewModel(
            dataSource = source,
            scope = externalScope,
        )
        val collectionJob = externalScope.launch {
            viewModel.uiState.collect {}
        }

        viewModel.selectSession(projectId = "project-yepanywhere", sessionId = "session-1")

        advanceUntilIdle()

        assertEquals(SupervisorShellSection.ACTIVE, viewModel.uiState.value.selectedSection)
        assertEquals("project-yepanywhere", viewModel.uiState.value.selectedProjectId)
        assertEquals("session-1", viewModel.uiState.value.selectedSessionId)

        collectionJob.cancel()
        externalScope.cancel()
    }

    @Test
    fun appliesNotificationRouteBySelectingTargetSection() = runTest {
        val source = FakeSupervisorShellDataSource()
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val viewModel = SupervisorShellViewModel(
            dataSource = source,
            scope = externalScope,
        )
        val collectionJob = externalScope.launch {
            viewModel.uiState.collect {}
        }

        viewModel.applyNotificationRoute(
            AndroidNotificationRoute(section = SupervisorShellSection.INBOX),
        )

        advanceUntilIdle()

        assertEquals(SupervisorShellSection.INBOX, viewModel.uiState.value.selectedSection)

        viewModel.applyNotificationRoute(
            AndroidNotificationRoute(section = SupervisorShellSection.ACTIVE, sessionId = "session-1"),
        )

        advanceUntilIdle()

        assertEquals(SupervisorShellSection.ACTIVE, viewModel.uiState.value.selectedSection)
        assertEquals("session-1", viewModel.uiState.value.selectedSessionId)

        collectionJob.cancel()
        externalScope.cancel()
    }

    @Test
    fun exposesExpandedTopLevelNavigationDestinations() {
        assertEquals(
            listOf(
                SupervisorShellSection.PROJECTS,
                SupervisorShellSection.SESSIONS,
                SupervisorShellSection.AGENTS,
                SupervisorShellSection.INBOX,
                SupervisorShellSection.GIT_STATUS,
                SupervisorShellSection.SETTINGS,
            ),
            SupervisorShellSection.topLevelEntries,
        )
    }

    private class FakeSupervisorShellDataSource : SupervisorShellDataSource {
        override val summary: String = "Android-owned cache and secure relay session persistence."
        override val shellState = MutableStateFlow(defaultSupervisorShellSnapshot())

        override suspend fun reconnectPersistedSession(): Boolean = false

        override suspend fun login(credentials: com.yepanywhere.android.data.RelayCredentials) = Unit

        override suspend fun logout() = Unit

        override suspend fun restorePersistedCredentials(): com.yepanywhere.android.data.RelayCredentials? = null
    }
}
