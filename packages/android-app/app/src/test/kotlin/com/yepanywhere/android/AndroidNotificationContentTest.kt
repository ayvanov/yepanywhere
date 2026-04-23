package com.yepanywhere.android

import com.yepanywhere.android.ui.SupervisorShellSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidNotificationContentTest {
    @Test
    fun payloadContentUsesDataRouteAndText() {
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
            AndroidNotificationContent.fromPayload(
                title = "Approval needed",
                body = "Session asks to run a command.",
                data = mapOf(
                    "target" to "inbox",
                    "projectId" to "project-1",
                    "sessionId" to "session-1",
                    "inboxItemId" to "inbox-1",
                ),
            ),
        )
    }

    @Test
    fun payloadContentFallsBackForBlankTextAndRejectsMissingRoute() {
        assertEquals(
            AndroidNotificationContent(
                title = "Yep Anywhere",
                body = "Open the app to review this update.",
                route = AndroidNotificationRoute(
                    section = SupervisorShellSection.ACTIVE,
                    sessionId = "session-1",
                ),
            ),
            AndroidNotificationContent.fromPayload(
                title = "",
                body = " ",
                data = mapOf("sessionId" to "session-1"),
            ),
        )

        assertNull(
            AndroidNotificationContent.fromPayload(
                title = "Ignored",
                body = "No route metadata.",
                data = emptyMap(),
            ),
        )
    }
}
