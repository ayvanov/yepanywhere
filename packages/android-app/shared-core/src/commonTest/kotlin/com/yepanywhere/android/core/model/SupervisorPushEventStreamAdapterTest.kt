package com.yepanywhere.android.core.model

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SupervisorPushEventStreamAdapterTest {
    @Test
    fun convertsValidPayloadsIntoTypedEvents() = runTest(UnconfinedTestDispatcher()) {
        val payloads = MutableSharedFlow<SupervisorPushPayload>()
        val events = mutableListOf<SupervisorPushEvent>()
        val adapter = SupervisorPushEventStreamAdapter(payloads)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            adapter.events().take(1).toList(events)
        }

        payloads.emit(
            SupervisorPushPayload.PendingInput(
                timestamp = "2026-04-23T10:15:30Z",
                sessionId = "session-1",
                projectId = "project-1",
                projectName = "Yep Anywhere",
                inputType = "tool-approval",
                summary = "Run: Bash",
                requestId = "request-1",
            ),
        )
        advanceUntilIdle()

        assertEquals(
            listOf<SupervisorPushEvent>(
                SupervisorPushEvent.PendingInput(
                    sessionId = "session-1",
                    projectId = "project-1",
                    projectName = "Yep Anywhere",
                    inputType = "tool-approval",
                    summary = "Run: Bash",
                    requestId = "request-1",
                ),
            ),
            events,
        )
    }

    @Test
    fun dropsInvalidPayloadsWithoutBreakingTheStream() = runTest(UnconfinedTestDispatcher()) {
        val payloads = MutableSharedFlow<SupervisorPushPayload>()
        val events = mutableListOf<SupervisorPushEvent>()
        val adapter = SupervisorPushEventStreamAdapter(payloads)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            adapter.events().take(1).toList(events)
        }

        payloads.emit(
            SupervisorPushPayload.Test(
                timestamp = "2026-04-23T10:15:30Z",
                message = "Ignore me",
                urgency = "silent",
            ),
        )
        payloads.emit(
            SupervisorPushPayload.Dismiss(
                timestamp = "2026-04-23T10:15:30Z",
                sessionId = "session-1",
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf<SupervisorPushEvent>(SupervisorPushEvent.Dismiss("session-1")), events)
        assertTrue(events.none { it is SupervisorPushEvent.Unknown })
    }
}
