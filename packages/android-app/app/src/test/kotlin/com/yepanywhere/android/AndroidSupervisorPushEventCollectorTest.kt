package com.yepanywhere.android

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
        val events = MutableSharedFlow<Map<String, String>>()
        val handled = mutableListOf<Map<String, String>>()
        val collector = AndroidSupervisorPushEventCollector(
            eventStream = events,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            handleEvent = {
                handled += it
                true
            },
        )

        val job = collector.start(backgroundScope)
        events.emit(mapOf("type" to "pending-input", "sessionId" to "session-1"))
        events.emit(mapOf("type" to "dismiss", "sessionId" to "session-1"))
        advanceUntilIdle()

        job.cancel()
        events.emit(mapOf("type" to "pending-input", "sessionId" to "session-2"))
        advanceUntilIdle()

        assertEquals(
            listOf(
                mapOf("type" to "pending-input", "sessionId" to "session-1"),
                mapOf("type" to "dismiss", "sessionId" to "session-1"),
            ),
            handled,
        )
    }

    @Test
    fun keepsCollectingAfterHandlerFailure() = runTest {
        val events = MutableSharedFlow<Map<String, String>>()
        val handledTypes = mutableListOf<String>()
        val collector = AndroidSupervisorPushEventCollector(
            eventStream = events,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            handleEvent = { event ->
                handledTypes += event.getValue("type")
                if (event["type"] == "bad") {
                    error("bad event")
                }
                true
            },
        )

        val job = collector.start(backgroundScope)
        events.emit(mapOf("type" to "bad"))
        events.emit(mapOf("type" to "pending-input"))
        advanceUntilIdle()

        assertTrue(job.isActive)
        assertEquals(listOf("bad", "pending-input"), handledTypes)
        job.cancel()
    }

    @Test
    fun handlerCancellationStopsCollector() = runTest {
        val events = MutableSharedFlow<Map<String, String>>()
        val collector = AndroidSupervisorPushEventCollector(
            eventStream = events,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            handleEvent = {
                throw CancellationException("stop")
            },
        )

        val job = collector.start(backgroundScope)
        events.emit(mapOf("type" to "pending-input"))
        advanceUntilIdle()

        assertFalse(job.isActive)
    }
}
