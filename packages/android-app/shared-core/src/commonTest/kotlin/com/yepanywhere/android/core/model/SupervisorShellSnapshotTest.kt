package com.yepanywhere.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SupervisorShellSnapshotTest {
    @Test
    fun countsUnreadInboxItemsAndNeedsAttentionSessions() {
        val snapshot = SupervisorShellSnapshot(
            connectionStatus = RelayConnectionStatus.CONNECTED,
            projects = listOf(ProjectSummary(id = "p1", name = "Yep Anywhere")),
            sessions = listOf(
                SessionSummary(
                    id = "s1",
                    projectId = "p1",
                    title = "Healthy session",
                    status = SessionStatus.RUNNING,
                    updatedLabel = "now",
                    hasUnread = false,
                ),
                SessionSummary(
                    id = "s2",
                    projectId = "p1",
                    title = "Needs approval",
                    status = SessionStatus.NEEDS_ATTENTION,
                    updatedLabel = "1m",
                    hasUnread = true,
                ),
            ),
            inboxItems = listOf(
                InboxItem(
                    id = "i1",
                    projectId = "p1",
                    sessionId = "s2",
                    title = "Approval needed",
                    subtitle = "Awaiting action",
                    kind = InboxItemKind.APPROVAL,
                    isUnread = true,
                ),
                InboxItem(
                    id = "i2",
                    projectId = "p1",
                    sessionId = "s1",
                    title = "Session updated",
                    subtitle = "Already seen",
                    kind = InboxItemKind.NOTIFICATION,
                    isUnread = false,
                ),
            ),
            timeline = SessionTimeline(
                sessionId = "s2",
                connectionStatus = RelayConnectionStatus.CONNECTED,
                messages = emptyList(),
            ),
            pendingRequests = emptyList(),
        )

        assertEquals(1, snapshot.unreadInboxCount)
        assertEquals(1, snapshot.sessionsNeedingAttentionCount)
    }
}
