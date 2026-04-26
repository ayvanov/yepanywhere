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
        assertEquals(
            SupervisorShellSection.SETTINGS,
            AndroidNotificationRoute.fromPayload(target = "settings")?.section,
        )
        assertEquals(
            SupervisorShellSection.AGENTS,
            AndroidNotificationRoute.fromPayload(target = "agents")?.section,
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
    fun dataPayloadRoutesLikeFcmMetadata() {
        assertEquals(
            AndroidNotificationRoute(
                section = SupervisorShellSection.INBOX,
                projectId = "project-1",
                sessionId = "session-1",
                inboxItemId = "inbox-1",
            ),
            AndroidNotificationRoute.fromData(
                mapOf(
                    "target" to "inbox",
                    "projectId" to "project-1",
                    "sessionId" to "session-1",
                    "inboxItemId" to "inbox-1",
                ),
            ),
        )
        assertEquals(
            SupervisorShellSection.ACTIVE,
            AndroidNotificationRoute.fromData(mapOf("sessionId" to "session-1"))?.section,
        )
        assertEquals(
            AndroidNotificationRoute(
                section = SupervisorShellSection.DEVICES,
                deviceId = "pixel-8",
            ),
            AndroidNotificationRoute.fromData(
                mapOf(
                    "target" to "devices",
                    "deviceId" to "pixel-8",
                ),
            ),
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
        assertEquals(
            AndroidNotificationRoute(
                section = SupervisorShellSection.FILE,
                projectId = "project-1",
                filePath = "packages/client/src/api/client.ts",
            ),
            AndroidNotificationRoute.fromDeepLink(
                "yepanywhere://open/file?projectId=project-1&path=packages/client/src/api/client.ts",
            ),
        )
    }

    @Test
    fun ignoresUnknownPayloadsAndLinks() {
        assertNull(AndroidNotificationRoute.fromPayload(target = null))
        assertNull(AndroidNotificationRoute.fromDeepLink("https://example.com/session/session-1"))
        assertNull(AndroidNotificationRoute.fromDeepLink("yepanywhere://other/session/session-1"))
        assertNull(AndroidNotificationRoute.fromDeepLink("not a uri"))
    }
}
