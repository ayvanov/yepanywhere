package com.yepanywhere.android

import com.yepanywhere.android.ui.SupervisorShellSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AndroidNotificationEventDispatcherTest {
    @Test
    fun dispatchesPayloadAsNotificationContentWithStableId() {
        var capturedId: Int? = null
        var capturedContent: AndroidNotificationContent? = null
        val dispatcher = AndroidNotificationEventDispatcher { id, content ->
            capturedId = id
            capturedContent = content
            true
        }
        val data = mapOf(
            "target" to "inbox",
            "projectId" to "project-1",
            "sessionId" to "session-1",
            "inboxItemId" to "inbox-1",
        )

        assertTrue(
            dispatcher.dispatch(
                title = "Approval needed",
                body = "Session asks to run a command.",
                data = data,
            ),
        )

        assertEquals(
            AndroidNotificationContent(
                title = "Approval needed",
                body = "Session asks to run a command.",
                route = AndroidNotificationRoute(
                    section = SupervisorShellSection.INBOX,
                    projectId = "project-1",
                    sessionId = "session-1",
                    inboxItemId = "inbox-1",
                ),
            ),
            capturedContent,
        )
        assertEquals(capturedId, AndroidNotificationContent.fromPayload("x", "y", data)?.notificationId())
    }

    @Test
    fun notificationIdChangesAcrossDifferentRoutes() {
        val first = AndroidNotificationContent(
            title = "First",
            body = "Body",
            route = AndroidNotificationRoute(
                section = SupervisorShellSection.ACTIVE,
                sessionId = "session-1",
            ),
        )
        val second = first.copy(
            route = AndroidNotificationRoute(
                section = SupervisorShellSection.ACTIVE,
                sessionId = "session-2",
            ),
        )

        assertNotEquals(first.notificationId(), second.notificationId())
    }

    @Test
    fun rejectsPayloadWithoutRouteMetadata() {
        var postCalls = 0
        val dispatcher = AndroidNotificationEventDispatcher { _, _ ->
            postCalls += 1
            true
        }

        assertFalse(
            dispatcher.dispatch(
                title = "Ignored",
                body = "No route.",
                data = emptyMap(),
            ),
        )
        assertEquals(0, postCalls)
    }
}
