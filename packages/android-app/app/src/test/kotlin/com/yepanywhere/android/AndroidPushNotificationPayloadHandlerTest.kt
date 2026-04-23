package com.yepanywhere.android

import com.yepanywhere.android.core.model.SupervisorPushEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidPushNotificationPayloadHandlerTest {
    @Test
    fun pendingInputPayloadDispatchesInboxNotification() {
        var capturedTitle: String? = null
        var capturedBody: String? = null
        var capturedData: Map<String, String>? = null
        val handler = AndroidPushNotificationPayloadHandler(
            dispatchNotification = { title, body, data ->
                capturedTitle = title
                capturedBody = body
                capturedData = data
                true
            },
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

        assertEquals("Yep Anywhere", capturedTitle)
        assertEquals("Run: Bash", capturedBody)
        assertEquals(
            mapOf(
                "target" to "inbox",
                "projectId" to "project-1",
                "sessionId" to "session-1",
                "inboxItemId" to "request-1",
            ),
            capturedData,
        )
    }

    @Test
    fun sessionHaltedPayloadDispatchesSessionNotification() {
        var capturedBody: String? = null
        var capturedData: Map<String, String>? = null
        val handler = AndroidPushNotificationPayloadHandler(
            dispatchNotification = { _, body, data ->
                capturedBody = body
                capturedData = data
                true
            },
        )

        assertTrue(
            handler.handle(
                SupervisorPushEvent.SessionHalted(
                    sessionId = "session-1",
                    projectId = "project-1",
                    projectName = "Yep Anywhere",
                    reason = "error",
                ),
            ),
        )

        assertEquals("Task encountered an error", capturedBody)
        assertEquals(
            mapOf(
                "target" to "session",
                "projectId" to "project-1",
                "sessionId" to "session-1",
            ),
            capturedData,
        )
    }

    @Test
    fun ignoresPayloadsThatDoNotCreateAndroidNotifications() {
        var dispatchCalls = 0
        val handler = AndroidPushNotificationPayloadHandler(
            dispatchNotification = { _, _, _ ->
                dispatchCalls += 1
                true
            },
        )

        assertFalse(handler.handle(SupervisorPushEvent.Unknown(type = "unknown")))
        assertEquals(0, dispatchCalls)
    }

    @Test
    fun dismissPayloadCancelsSessionNotification() {
        var dismissedSessionId: String? = null
        val handler = AndroidPushNotificationPayloadHandler(
            dispatchNotification = { _, _, _ -> true },
            dismissSessionNotifications = { sessionId ->
                dismissedSessionId = sessionId
                true
            },
        )

        assertTrue(handler.handle(SupervisorPushEvent.Dismiss(sessionId = "session-1")))
        assertEquals("session-1", dismissedSessionId)
    }

    @Test
    fun dataPayloadDispatchesRouteNotificationWithoutTypedEvent() {
        var capturedTitle: String? = null
        var capturedBody: String? = null
        var capturedData: Map<String, String>? = null
        val handler = AndroidPushNotificationPayloadHandler(
            dispatchNotification = { title, body, data ->
                capturedTitle = title
                capturedBody = body
                capturedData = data
                true
            },
        )

        assertTrue(
            handler.handleDataPayload(
                data = mapOf(
                    "sessionId" to "session-1",
                    "projectId" to "project-1",
                    "projectName" to "Yep Anywhere",
                    "summary" to "Tap to review",
                ),
            ),
        )

        assertEquals("Yep Anywhere", capturedTitle)
        assertEquals("Tap to review", capturedBody)
        assertEquals(
            mapOf(
                "target" to "session",
                "projectId" to "project-1",
                "sessionId" to "session-1",
            ),
            capturedData,
        )
    }
}
