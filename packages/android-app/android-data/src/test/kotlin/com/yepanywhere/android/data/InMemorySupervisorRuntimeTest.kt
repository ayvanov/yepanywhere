package com.yepanywhere.android.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
}
