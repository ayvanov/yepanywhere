package com.yepanywhere.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.yepanywhere.android.ui.ActiveSessionCallbacks
import com.yepanywhere.android.ui.ActiveSessionScreenState
import com.yepanywhere.android.ui.InboxScreenState
import com.yepanywhere.android.ui.ProjectsScreenState
import com.yepanywhere.android.ui.SessionsScreenState
import com.yepanywhere.android.ui.SupervisorShellScreen
import com.yepanywhere.android.ui.SupervisorShellScreenState
import com.yepanywhere.android.ui.SupervisorShellSection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupervisorShellScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun activeSessionShellUsesScrollableContentContainer() {
        composeRule.setContent {
            SupervisorShellScreen(
                state = shellState(),
                projectsState = ProjectsScreenState(
                    title = "Projects",
                    subtitle = "Project summary",
                    projects = listOf(ProjectSummary(id = "project-1", name = "Yep Anywhere", isActive = true)),
                ),
                sessionsState = SessionsScreenState(
                    title = "Sessions",
                    subtitle = "Session summary",
                    sessions = listOf(
                        SessionSummary(
                            id = "session-1",
                            projectId = "project-1",
                            title = "Android shell",
                            status = SessionStatus.RUNNING,
                            updatedLabel = "now",
                            hasUnread = false,
                        ),
                    ),
                ),
                inboxState = InboxScreenState(
                    title = "Inbox",
                    subtitle = "Inbox summary",
                    items = listOf(
                        InboxItem(
                            id = "inbox-1",
                            projectId = "project-1",
                            sessionId = "session-1",
                            title = "Question from active session",
                            subtitle = "Choose between cached shell states and live sync",
                            kind = InboxItemKind.QUESTION,
                            isUnread = true,
                        ),
                    ),
                ),
                activeSessionState = ActiveSessionScreenState(
                    title = "Active session",
                    subtitle = "Foreground realtime shell for session detail and approvals.",
                    timeline = SessionTimeline(
                        sessionId = "session-1",
                        connectionStatus = RelayConnectionStatus.CONNECTED,
                        messages = listOf(
                            SessionMessage(
                                id = "msg-1",
                                author = SessionMessageAuthor.SYSTEM,
                                body = "Foreground realtime only. Background updates rely on push plus resync.",
                                timestampLabel = "09:43",
                            ),
                            SessionMessage(
                                id = "msg-2",
                                author = SessionMessageAuthor.USER,
                                body = "Timeline message that should stay reachable on smaller screens.",
                                timestampLabel = "09:44",
                            ),
                        ),
                    ),
                    pendingRequests = listOf(
                        PendingInputRequest(
                            id = "request-1",
                            sessionId = "session-1",
                            title = "Choose sync strategy",
                            body = "Use cache-first read-only snapshots for offline mode?",
                            kind = InboxItemKind.QUESTION,
                        ),
                    ),
                ),
                activeSessionCallbacks = ActiveSessionCallbacks(
                    onSendReply = {},
                    onApproveRequest = {},
                    onDenyRequest = { _, _ -> },
                    onAnswerQuestion = { _, _ -> },
                ),
                onSectionSelected = {},
            )
        }

        composeRule.onNodeWithTag("supervisor-shell-scroll")
            .assert(hasScrollAction())
    }

    private fun shellState(): SupervisorShellScreenState {
        return SupervisorShellScreenState(
            title = "Yep Anywhere Android",
            subtitle = "Android-owned cache/storage layer for Room, DataStore, and secure relay session persistence.",
            snapshot = SupervisorShellSnapshot(
                connectionStatus = RelayConnectionStatus.CONNECTED,
                projects = listOf(ProjectSummary(id = "project-1", name = "Yep Anywhere", isActive = true)),
                sessions = listOf(
                    SessionSummary(
                        id = "session-1",
                        projectId = "project-1",
                        title = "Android shell",
                        status = SessionStatus.RUNNING,
                        updatedLabel = "now",
                        hasUnread = false,
                    ),
                ),
                inboxItems = listOf(
                    InboxItem(
                        id = "inbox-1",
                        projectId = "project-1",
                        sessionId = "session-1",
                        title = "Question from active session",
                        subtitle = "Choose between cached shell states and live sync",
                        kind = InboxItemKind.QUESTION,
                        isUnread = true,
                    ),
                ),
                timeline = SessionTimeline(
                    sessionId = "session-1",
                    connectionStatus = RelayConnectionStatus.CONNECTED,
                    messages = listOf(
                        SessionMessage(
                            id = "msg-1",
                            author = SessionMessageAuthor.SYSTEM,
                            body = "Foreground realtime only. Background updates rely on push plus resync.",
                            timestampLabel = "09:43",
                        ),
                    ),
                ),
                pendingRequests = listOf(
                    PendingInputRequest(
                        id = "request-1",
                        sessionId = "session-1",
                        title = "Choose sync strategy",
                        body = "Use cache-first read-only snapshots for offline mode?",
                        kind = InboxItemKind.QUESTION,
                    ),
                ),
            ),
            selectedSection = SupervisorShellSection.ACTIVE,
        )
    }
}
