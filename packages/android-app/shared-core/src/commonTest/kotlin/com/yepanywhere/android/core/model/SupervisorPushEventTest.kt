package com.yepanywhere.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SupervisorPushEventTest {
    @Test
    fun parsesPendingInputPayload() {
        assertEquals(
            SupervisorPushEvent.PendingInput(
                sessionId = "session-1",
                projectId = "project-1",
                projectName = "Yep Anywhere",
                inputType = "tool-approval",
                summary = "Run: Bash",
                requestId = "request-1",
            ),
            SupervisorPushEvent.fromPayload(
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
    }

    @Test
    fun parsesSessionHaltedPayload() {
        assertEquals(
            SupervisorPushEvent.SessionHalted(
                sessionId = "session-1",
                projectId = "project-1",
                projectName = "Yep Anywhere",
                reason = "error",
            ),
            SupervisorPushEvent.fromPayload(
                mapOf(
                    "type" to "session-halted",
                    "sessionId" to "session-1",
                    "projectId" to "project-1",
                    "projectName" to "Yep Anywhere",
                    "reason" to "error",
                ),
            ),
        )
    }

    @Test
    fun parsesDismissPayload() {
        assertEquals(
            SupervisorPushEvent.Dismiss(sessionId = "session-1"),
            SupervisorPushEvent.fromPayload(
                mapOf(
                    "type" to "dismiss",
                    "sessionId" to "session-1",
                ),
            ),
        )
    }

    @Test
    fun ignoresUnsupportedOrIncompletePayloads() {
        assertNull(SupervisorPushEvent.fromPayload(mapOf("type" to "unknown")))
        assertNull(SupervisorPushEvent.fromPayload(mapOf("type" to "pending-input", "sessionId" to "session-1")))
        assertNull(SupervisorPushEvent.fromPayload(mapOf("type" to "dismiss")))
    }
}
