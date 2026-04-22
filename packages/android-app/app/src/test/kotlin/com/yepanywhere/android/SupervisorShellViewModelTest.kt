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
        assertEquals(SupervisorShellSection.ACTIVE, viewModel.uiState.value.selectedSection)
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
    fun connectsDemoSessionOnlyOnce() = runTest {
        val source = FakeSupervisorShellDataSource()
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val viewModel = SupervisorShellViewModel(
            dataSource = source,
            scope = externalScope,
        )

        viewModel.ensureDemoSessionConnected()
        viewModel.ensureDemoSessionConnected()

        advanceUntilIdle()

        assertEquals(1, source.connectCalls)

        externalScope.cancel()
    }

    private class FakeSupervisorShellDataSource : SupervisorShellDataSource {
        override val summary: String = "Android-owned cache and secure relay session persistence."
        override val shellState = MutableStateFlow(defaultSupervisorShellSnapshot())

        var connectCalls: Int = 0

        override suspend fun connectDemoSession() {
            connectCalls += 1
        }
    }
}
