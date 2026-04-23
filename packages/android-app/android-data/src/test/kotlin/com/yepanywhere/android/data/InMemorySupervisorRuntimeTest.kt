package com.yepanywhere.android.data

import com.yepanywhere.android.core.model.SupervisorPushEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InMemorySupervisorRuntimeTest {
    @Test
    fun approveRemovesPendingRequestAndClearsSessionAttention() = runTest(UnconfinedTestDispatcher()) {
        val runtime = InMemorySupervisorRuntime(
            scope = backgroundScope,
        )

        runtime.approvalsRepository.approve("request-1")

        val snapshot = runtime.shellState.value
        assertEquals(1, snapshot.pendingRequests.size)
        assertFalse(snapshot.pendingRequests.any { it.id == "request-1" })

        val resolvedSession = snapshot.sessions.first { it.id == "session-approval" }
        assertEquals("resolved now", resolvedSession.updatedLabel)
        assertFalse(resolvedSession.hasUnread)
        assertEquals(1, snapshot.unreadInboxCount)
    }

    @Test
    fun sendReplyAppendsUserMessageToActiveTimeline() = runTest(UnconfinedTestDispatcher()) {
        val runtime = InMemorySupervisorRuntime(
            scope = backgroundScope,
        )

        runtime.sessionsRepository.sendReply(
            sessionId = "session-android-shell",
            text = "Ship the in-memory supervisor runtime next.",
        )

        val snapshot = runtime.shellState.value
        assertTrue(
            snapshot.timeline.messages.any { message ->
                message.body == "Ship the in-memory supervisor runtime next."
            },
        )
        assertEquals("just now", snapshot.sessions.first { it.id == "session-android-shell" }.updatedLabel)
    }

    @Test
    fun pendingInputNotificationUpdatesCacheSnapshot() = runTest(UnconfinedTestDispatcher()) {
        val runtime = InMemorySupervisorRuntime(
            scope = backgroundScope,
        )

        runtime.applyPendingInputNotification(
            sessionId = "session-new",
            projectId = "project-new",
            projectName = "New Project",
            inputType = "user-question",
            summary = "Choose deployment target?",
            requestId = "request-new",
        )

        val snapshot = runtime.shellState.value
        assertTrue(snapshot.projects.any { it.id == "project-new" && it.name == "New Project" })
        assertTrue(
            snapshot.sessions.any { session ->
                session.id == "session-new" &&
                    session.status.name == "NEEDS_ATTENTION" &&
                    session.hasUnread
            },
        )
        assertTrue(
            snapshot.inboxItems.any { item ->
                item.id == "inbox-request-new" &&
                    item.sessionId == "session-new" &&
                    item.isUnread
            },
        )
        assertTrue(
            snapshot.pendingRequests.any { request ->
                request.id == "request-new" &&
                    request.sessionId == "session-new" &&
                    request.body == "Choose deployment target?"
            },
        )
    }

    @Test
    fun clearingSessionAttentionResolvesCachedPendingInput() = runTest(UnconfinedTestDispatcher()) {
        val runtime = InMemorySupervisorRuntime(
            scope = backgroundScope,
        )

        runtime.clearSessionAttention("session-approval")

        val snapshot = runtime.shellState.value
        assertFalse(snapshot.pendingRequests.any { it.sessionId == "session-approval" })
        assertFalse(snapshot.inboxItems.first { it.sessionId == "session-approval" }.isUnread)
        assertFalse(snapshot.sessions.first { it.id == "session-approval" }.hasUnread)
    }

    @Test
    fun relayClientStreamsForegroundSupervisorPushEvents() = runTest(UnconfinedTestDispatcher()) {
        val runtime = InMemorySupervisorRuntime(
            scope = backgroundScope,
        )
        val event = SupervisorPushEvent.PendingInput(
            sessionId = "session-stream",
            projectId = "project-stream",
            projectName = "Stream Project",
            inputType = "user-question",
            summary = "Stream question",
            requestId = "request-stream",
        )
        val events = mutableListOf<SupervisorPushEvent>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            runtime.relayConnectionClient.supervisorPushEventStream().take(1).toList(events)
        }
        runtime.emitSupervisorPushEvent(event)
        advanceUntilIdle()

        assertEquals(listOf<SupervisorPushEvent>(event), events)
    }
}
