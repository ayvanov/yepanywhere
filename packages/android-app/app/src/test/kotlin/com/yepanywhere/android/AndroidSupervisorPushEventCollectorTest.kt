package com.yepanywhere.android

import com.yepanywhere.android.core.model.SupervisorPushEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidSupervisorPushEventCollectorTest {
    @Test
    fun collectsForegroundEventsUntilCancelled() = runTest {
        val events = MutableSharedFlow<SupervisorPushEvent>()
        val handled = mutableListOf<SupervisorPushEvent>()
        val collector = AndroidSupervisorPushEventCollector(
            eventStream = events,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            handleEvent = {
                handled += it
                true
            },
        )

        val job = collector.start(backgroundScope)
        val first = SupervisorPushEvent.PendingInput(
            sessionId = "session-1",
            projectId = "project-1",
            requestId = "request-1",
        )
        val second = SupervisorPushEvent.Dismiss(sessionId = "session-1")
        events.emit(first)
        events.emit(second)
        advanceUntilIdle()

        job.cancel()
        events.emit(
            SupervisorPushEvent.PendingInput(
                sessionId = "session-2",
                projectId = "project-2",
                requestId = "request-2",
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf(first, second), handled)
    }

    @Test
    fun keepsCollectingAfterHandlerFailure() = runTest {
        val events = MutableSharedFlow<SupervisorPushEvent>()
        val handledTypes = mutableListOf<String>()
        val collector = AndroidSupervisorPushEventCollector(
            eventStream = events,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            handleEvent = { event ->
                handledTypes += event.type
                if (event.type == "bad") {
                    error("bad event")
                }
                true
            },
        )

        val job = collector.start(backgroundScope)
        events.emit(SupervisorPushEvent.Unknown(type = "bad"))
        events.emit(
            SupervisorPushEvent.PendingInput(
                sessionId = "session-1",
                projectId = "project-1",
                requestId = "request-1",
            ),
        )
        advanceUntilIdle()

        assertTrue(job.isActive)
        assertEquals(listOf("bad", "pending-input"), handledTypes)
        job.cancel()
    }

    @Test
    fun handlerCancellationStopsCollector() = runTest {
        val events = MutableSharedFlow<SupervisorPushEvent>()
        val collector = AndroidSupervisorPushEventCollector(
            eventStream = events,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            handleEvent = {
                throw CancellationException("stop")
            },
        )

        val job = collector.start(backgroundScope)
        events.emit(
            SupervisorPushEvent.PendingInput(
                sessionId = "session-1",
                projectId = "project-1",
                requestId = "request-1",
            ),
        )
        advanceUntilIdle()

        assertFalse(job.isActive)
    }
}
