package com.yepanywhere.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SupervisorPushPayloadTest {
    @Test
    fun decodesPendingInputJsonIntoTypedPayload() {
        val json = """
            {
              "type": "pending-input",
              "timestamp": "2026-04-23T10:15:30Z",
              "sessionId": "session-1",
              "projectId": "project-1",
              "projectName": "Yep Anywhere",
              "inputType": "tool-approval",
              "summary": "Run: Bash",
              "requestId": "request-1"
            }
        """.trimIndent()

        assertEquals(
            SupervisorPushPayload.PendingInput(
                timestamp = "2026-04-23T10:15:30Z",
                sessionId = "session-1",
                projectId = "project-1",
                projectName = "Yep Anywhere",
                inputType = "tool-approval",
                summary = "Run: Bash",
                requestId = "request-1",
            ),
            SupervisorPushPayload.decode(json),
        )
    }

    @Test
    fun convertsPendingInputPayloadIntoSupervisorEvent() {
        val payload = SupervisorPushPayload.PendingInput(
            timestamp = "2026-04-23T10:15:30Z",
            sessionId = "session-1",
            projectId = "project-1",
            projectName = "Yep Anywhere",
            inputType = "tool-approval",
            summary = "Run: Bash",
            requestId = "request-1",
        )

        assertEquals(
            SupervisorPushEvent.PendingInput(
                sessionId = "session-1",
                projectId = "project-1",
                projectName = "Yep Anywhere",
                inputType = "tool-approval",
                summary = "Run: Bash",
                requestId = "request-1",
            ),
            payload.toEvent(),
        )
    }

    @Test
    fun ignoresPayloadsThatDoNotMapToSupervisorEvents() {
        val payload = SupervisorPushPayload.Test(
            timestamp = "2026-04-23T10:15:30Z",
            message = "Transport online",
            urgency = "normal",
        )

        assertNull(payload.toEvent())
    }
}
