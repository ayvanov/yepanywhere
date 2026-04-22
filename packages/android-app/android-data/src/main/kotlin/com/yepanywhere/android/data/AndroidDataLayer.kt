package com.yepanywhere.android.data

import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.InboxItemKind
import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.SessionMessage
import com.yepanywhere.android.core.model.SessionMessageAuthor
import com.yepanywhere.android.core.model.SessionStatus
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import com.yepanywhere.android.core.model.SupervisorShellSnapshot

object AndroidDataLayer {
    const val summary: String =
        "Android-owned cache/storage layer for Room, DataStore, and secure relay session persistence."

    val previewSnapshot = SupervisorShellSnapshot(
        connectionStatus = RelayConnectionStatus.SYNCING,
        projects = listOf(
            ProjectSummary(
                id = "project-yepanywhere",
                name = "Yep Anywhere",
                isActive = true,
            ),
            ProjectSummary(
                id = "project-relay",
                name = "Relay hardening",
            ),
        ),
        sessions = listOf(
            SessionSummary(
                id = "session-android-shell",
                projectId = "project-yepanywhere",
                title = "Native Android MVP shell",
                status = SessionStatus.RUNNING,
                updatedLabel = "just now",
                hasUnread = false,
            ),
            SessionSummary(
                id = "session-approval",
                projectId = "project-yepanywhere",
                title = "Relay approval pending",
                status = SessionStatus.NEEDS_ATTENTION,
                updatedLabel = "2 min ago",
                hasUnread = true,
            ),
            SessionSummary(
                id = "session-cache",
                projectId = "project-relay",
                title = "Session cache strategy",
                status = SessionStatus.IDLE,
                updatedLabel = "15 min ago",
                hasUnread = false,
            ),
        ),
        inboxItems = listOf(
            InboxItem(
                id = "inbox-approval",
                projectId = "project-yepanywhere",
                sessionId = "session-approval",
                title = "Approval required",
                subtitle = "Grant network access to relay diagnostics",
                kind = InboxItemKind.APPROVAL,
                isUnread = true,
            ),
            InboxItem(
                id = "inbox-question",
                projectId = "project-yepanywhere",
                sessionId = "session-android-shell",
                title = "Question from active session",
                subtitle = "Choose between cached shell states and live sync",
                kind = InboxItemKind.QUESTION,
                isUnread = true,
            ),
            InboxItem(
                id = "inbox-notification",
                projectId = "project-relay",
                sessionId = "session-cache",
                title = "Resync complete",
                subtitle = "Cached read-only snapshot refreshed",
                kind = InboxItemKind.NOTIFICATION,
                isUnread = false,
            ),
        ),
        timeline = SessionTimeline(
            sessionId = "session-android-shell",
            connectionStatus = RelayConnectionStatus.CONNECTED,
            messages = listOf(
                SessionMessage(
                    id = "msg-1",
                    author = SessionMessageAuthor.USER,
                    body = "Continue from the Android scaffold and add real shared-core contracts.",
                    timestampLabel = "09:42",
                ),
                SessionMessage(
                    id = "msg-2",
                    author = SessionMessageAuthor.ASSISTANT,
                    body = "Scaffold is in place. Moving next to repository interfaces and a first Supervisor shell.",
                    timestampLabel = "09:43",
                ),
                SessionMessage(
                    id = "msg-3",
                    author = SessionMessageAuthor.SYSTEM,
                    body = "Foreground realtime only. Background updates rely on push plus resync.",
                    timestampLabel = "09:43",
                ),
            ),
        ),
        pendingRequests = listOf(
            PendingInputRequest(
                id = "request-1",
                sessionId = "session-approval",
                title = "Approve relay diagnostics",
                body = "Allow this session to run network diagnostics against the relay host.",
                kind = InboxItemKind.APPROVAL,
            ),
            PendingInputRequest(
                id = "request-2",
                sessionId = "session-android-shell",
                title = "Choose sync strategy",
                body = "Use cache-first read-only snapshots for offline mode?",
                kind = InboxItemKind.QUESTION,
            ),
        ),
    )
}
