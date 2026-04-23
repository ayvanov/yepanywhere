package com.yepanywhere.android

import com.yepanywhere.android.ui.SupervisorShellSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidNotificationRouteTest {
    @Test
    fun payloadTargetRoutesToRequestedShellSection() {
        assertEquals(
            SupervisorShellSection.INBOX,
            AndroidNotificationRoute.fromPayload(target = "inbox")?.section,
        )
        assertEquals(
            SupervisorShellSection.ACTIVE,
            AndroidNotificationRoute.fromPayload(target = "session")?.section,
        )
        assertEquals(
            SupervisorShellSection.SESSIONS,
            AndroidNotificationRoute.fromPayload(target = "sessions")?.section,
        )
        assertEquals(
            SupervisorShellSection.PROJECTS,
            AndroidNotificationRoute.fromPayload(target = "projects")?.section,
        )
    }

    @Test
    fun payloadFallsBackToMostSpecificMetadata() {
        assertEquals(
            AndroidNotificationRoute(
                section = SupervisorShellSection.INBOX,
                inboxItemId = "inbox-1",
                sessionId = "session-1",
                projectId = "project-1",
            ),
            AndroidNotificationRoute.fromPayload(
                target = null,
                projectId = "project-1",
                sessionId = "session-1",
                inboxItemId = "inbox-1",
            ),
        )
        assertEquals(
            SupervisorShellSection.ACTIVE,
            AndroidNotificationRoute.fromPayload(target = null, sessionId = "session-1")?.section,
        )
        assertEquals(
            SupervisorShellSection.PROJECTS,
            AndroidNotificationRoute.fromPayload(target = null, projectId = "project-1")?.section,
        )
    }

    @Test
    fun deepLinkRoutesKnownPaths() {
        assertEquals(
            AndroidNotificationRoute(
                section = SupervisorShellSection.INBOX,
                inboxItemId = "inbox-1",
                sessionId = "session-1",
            ),
            AndroidNotificationRoute.fromDeepLink(
                "yepanywhere://open/inbox?inboxItemId=inbox-1&sessionId=session-1",
            ),
        )
        assertEquals(
            AndroidNotificationRoute(
                section = SupervisorShellSection.ACTIVE,
                sessionId = "session-1",
            ),
            AndroidNotificationRoute.fromDeepLink("yepanywhere://open/session/session-1"),
        )
    }

    @Test
    fun ignoresUnknownPayloadsAndLinks() {
        assertNull(AndroidNotificationRoute.fromPayload(target = "settings"))
        assertNull(AndroidNotificationRoute.fromPayload(target = null))
        assertNull(AndroidNotificationRoute.fromDeepLink("https://example.com/session/session-1"))
        assertNull(AndroidNotificationRoute.fromDeepLink("yepanywhere://other/session/session-1"))
        assertNull(AndroidNotificationRoute.fromDeepLink("not a uri"))
    }
}
