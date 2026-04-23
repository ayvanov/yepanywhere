package com.yepanywhere.android

import com.yepanywhere.android.core.model.SupervisorPushEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidSupervisorPushEventHandlerTest {
    @Test
    fun pendingInputPayloadUpdatesCacheBeforePostingNotification() = runTest {
        val calls = mutableListOf<String>()
        val handler = AndroidSupervisorPushEventHandler(
            handleNotificationPayload = {
                calls += "notify:${it.type}"
                true
            },
            applyPendingInputNotification = { sessionId, projectId, projectName, inputType, summary, requestId ->
                calls += "cache:$sessionId|$projectId|$projectName|$inputType|$summary|$requestId"
            },
            clearSessionAttention = { calls += "clear:$it" },
        )

        assertTrue(
            handler.handle(
                SupervisorPushEvent.PendingInput(
                    sessionId = "session-1",
                    projectId = "project-1",
                    projectName = "Yep Anywhere",
                    inputType = "tool-approval",
                    summary = "Run: Bash",
                    requestId = "request-1",
                ),
            ),
        )

        assertEquals(
            listOf(
                "cache:session-1|project-1|Yep Anywhere|tool-approval|Run: Bash|request-1",
                "notify:pending-input",
            ),
            calls,
        )
    }

    @Test
    fun dismissPayloadClearsCacheBeforeCancellingNotification() = runTest {
        val calls = mutableListOf<String>()
        val handler = AndroidSupervisorPushEventHandler(
            handleNotificationPayload = {
                calls += "notify:${it.type}"
                true
            },
            applyPendingInputNotification = { _, _, _, _, _, _ -> },
            clearSessionAttention = { calls += "clear:$it" },
        )

        assertTrue(
            handler.handle(
                SupervisorPushEvent.Dismiss(sessionId = "session-1"),
            ),
        )

        assertEquals(
            listOf(
                "clear:session-1",
                "notify:dismiss",
            ),
            calls,
        )
    }
}
