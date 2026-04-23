package com.yepanywhere.android

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
                mapOf(
                    "type" to "session-halted",
                    "sessionId" to "session-1",
                    "projectId" to "project-1",
                    "projectName" to "Yep Anywhere",
                    "reason" to "error",
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

        assertFalse(handler.handle(mapOf("type" to "unknown")))
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

        assertTrue(handler.handle(mapOf("type" to "dismiss", "sessionId" to "session-1")))
        assertEquals("session-1", dismissedSessionId)
    }
}
