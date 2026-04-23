package com.yepanywhere.android

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
                calls += "notify:${it["type"]}"
                true
            },
            applyPendingInputNotification = { sessionId, projectId, projectName, inputType, summary, requestId ->
                calls += "cache:$sessionId|$projectId|$projectName|$inputType|$summary|$requestId"
            },
            clearSessionAttention = { calls += "clear:$it" },
        )

        assertTrue(
            handler.handle(
                mapOf(
                    "type" to "pending-input",
                    "sessionId" to "session-1",
                    "projectId" to "project-1",
                    "projectName" to "Yep Anywhere",
                    "inputType" to "tool-approval",
                    "summary" to "Run: Bash",
                    "requestId" to "request-1",
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
                calls += "notify:${it["type"]}"
                true
            },
            applyPendingInputNotification = { _, _, _, _, _, _ -> },
            clearSessionAttention = { calls += "clear:$it" },
        )

        assertTrue(
            handler.handle(
                mapOf(
                    "type" to "dismiss",
                    "sessionId" to "session-1",
                ),
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
