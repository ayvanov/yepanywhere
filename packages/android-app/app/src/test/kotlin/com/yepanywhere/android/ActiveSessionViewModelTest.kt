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
class ActiveSessionViewModelTest {
    @Test
    fun mapsTimelineAndPendingRequestsIntoDedicatedActiveSessionState() = runTest {
        val source = FakeSupervisorShellDataSource()
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val viewModel = ActiveSessionViewModel(
            dataSource = source,
            scope = externalScope,
        )
        val collectionJob = externalScope.launch {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()

        assertEquals("Active session", viewModel.uiState.value.title)
        assertEquals(source.shellState.value.timeline, viewModel.uiState.value.timeline)
        assertEquals(source.shellState.value.pendingRequests, viewModel.uiState.value.pendingRequests)

        source.shellState.value = source.shellState.value.copy(
            timeline = source.shellState.value.timeline.copy(
                messages = source.shellState.value.timeline.messages.takeLast(1),
            ),
            pendingRequests = source.shellState.value.pendingRequests.take(1),
        )

        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.timeline.messages.size)
        assertEquals(1, viewModel.uiState.value.pendingRequests.size)

        collectionJob.cancel()
        externalScope.cancel()
    }

    private class FakeSupervisorShellDataSource : SupervisorShellDataSource {
        override val summary: String = "Android-owned cache and secure relay session persistence."
        override val shellState = MutableStateFlow(defaultSupervisorShellSnapshot())

        override suspend fun connectDemoSession() = Unit
    }
}
