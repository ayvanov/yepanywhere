package com.yepanywhere.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupervisorShellScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun activeSessionShellUsesScrollableContentContainer() {
        renderShell()

        composeRule.onNodeWithTag("supervisor-shell-scroll")
            .assert(hasScrollAction())
    }

    @Test
    fun replyComposerSendsTrimmedReplyAndClearsDraft() {
        var sentReply: String? = null

        renderShell(
            callbacks = ActiveSessionCallbacks(
                onSendReply = { sentReply = it },
                onApproveRequest = {},
                onDenyRequest = { _, _ -> },
                onAnswerQuestion = { _, _ -> },
            ),
        )

        composeRule.onNodeWithTag("reply-input")
            .performTextInput("  Need update from Android shell  ")
        composeRule.onNodeWithTag("reply-send")
            .performClick()

        composeRule.runOnIdle {
            assertEquals("Need update from Android shell", sentReply)
        }
        composeRule.onNodeWithTag("reply-send")
            .assertIsNotEnabled()
    }

    @Test
    fun approvalRequestApproveDispatchesRequestId() {
        var approvedRequestId: String? = null

        renderShell(
            activeSessionState = activeSessionState(
                pendingRequests = listOf(approvalRequest()),
            ),
            shellState = shellState(
                pendingRequests = listOf(approvalRequest()),
            ),
            callbacks = ActiveSessionCallbacks(
                onSendReply = {},
                onApproveRequest = { approvedRequestId = it },
                onDenyRequest = { _, _ -> },
                onAnswerQuestion = { _, _ -> },
            ),
        )

        scrollShellTo("pending-request-approve-request-approval")
        composeRule.onNodeWithTag("pending-request-approve-request-approval")
            .performClick()

        composeRule.runOnIdle {
            assertEquals("request-approval", approvedRequestId)
        }
    }

    @Test
    fun approvalRequestDenySendsTrimmedFeedbackAndClearsDraft() {
        var deniedRequestId: String? = null
        var denialFeedback: String? = null

        renderShell(
            activeSessionState = activeSessionState(
                pendingRequests = listOf(approvalRequest()),
            ),
            shellState = shellState(
                pendingRequests = listOf(approvalRequest()),
            ),
            callbacks = ActiveSessionCallbacks(
                onSendReply = {},
                onApproveRequest = {},
                onDenyRequest = { requestId, feedback ->
                    deniedRequestId = requestId
                    denialFeedback = feedback
                },
                onAnswerQuestion = { _, _ -> },
            ),
        )

        scrollShellTo("pending-request-input-request-approval")
        composeRule.onNodeWithTag("pending-request-input-request-approval")
            .performTextInput("  Relay credentials expired  ")
        scrollShellTo("pending-request-deny-request-approval")
        composeRule.onNodeWithTag("pending-request-deny-request-approval")
            .performClick()

        composeRule.runOnIdle {
            assertEquals("request-approval", deniedRequestId)
            assertEquals("Relay credentials expired", denialFeedback)
        }
    }

    @Test
    fun questionRequestSendsTrimmedAnswerAndClearsDraft() {
        var answeredRequestId: String? = null
        var answerBody: String? = null

        renderShell(
            callbacks = ActiveSessionCallbacks(
                onSendReply = {},
                onApproveRequest = {},
                onDenyRequest = { _, _ -> },
                onAnswerQuestion = { requestId, answer ->
                    answeredRequestId = requestId
                    answerBody = answer
                },
            ),
        )

        scrollShellTo("pending-request-input-request-question")
        composeRule.onNodeWithTag("pending-request-input-request-question")
            .performTextInput("  Cache-first, then foreground sync  ")
        scrollShellTo("pending-request-answer-request-question")
        composeRule.onNodeWithTag("pending-request-answer-request-question")
            .performClick()

        composeRule.runOnIdle {
            assertEquals("request-question", answeredRequestId)
            assertEquals("Cache-first, then foreground sync", answerBody)
        }
        composeRule.onNodeWithTag("pending-request-answer-request-question")
            .assertIsNotEnabled()
    }

    @Test
    fun approvalRequestDenyWithoutNoteSendsNullFeedback() {
        var denialFeedback: String? = "seed"

        renderShell(
            activeSessionState = activeSessionState(
                pendingRequests = listOf(approvalRequest()),
            ),
            shellState = shellState(
                pendingRequests = listOf(approvalRequest()),
            ),
            callbacks = ActiveSessionCallbacks(
                onSendReply = {},
                onApproveRequest = {},
                onDenyRequest = { _, feedback ->
                    denialFeedback = feedback
                },
                onAnswerQuestion = { _, _ -> },
            ),
        )

        scrollShellTo("pending-request-deny-request-approval")
        composeRule.onNodeWithTag("pending-request-deny-request-approval")
            .performClick()

        composeRule.runOnIdle {
            assertNull(denialFeedback)
        }
    }

    @Test
    fun disconnectedActiveSessionShowsCachedStateBanner() {
        renderShell(
            activeSessionState = activeSessionState(
                timeline = disconnectedTimeline(),
            ),
            shellState = shellState(
                timeline = disconnectedTimeline(),
            ),
        )

        composeRule.onNodeWithTag("active-session-status-banner")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Offline snapshot")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Actions stay read-only until relay connectivity returns.")
            .assertIsDisplayed()
    }

    @Test
    fun disconnectedActiveSessionDisablesNetworkActions() {
        renderShell(
            activeSessionState = activeSessionState(
                pendingRequests = listOf(approvalRequest(), questionRequest()),
                timeline = disconnectedTimeline(),
            ),
            shellState = shellState(
                pendingRequests = listOf(approvalRequest(), questionRequest()),
                timeline = disconnectedTimeline(),
            ),
        )

        composeRule.onNodeWithTag("reply-input")
            .performTextInput("Need relay back")
        composeRule.onNodeWithTag("reply-send")
            .assertIsNotEnabled()

        scrollShellTo("pending-request-approve-request-approval")
        composeRule.onNodeWithTag("pending-request-approve-request-approval")
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("pending-request-deny-request-approval")
            .assertIsNotEnabled()

        scrollShellTo("pending-request-input-request-question")
        composeRule.onNodeWithTag("pending-request-input-request-question")
            .performTextInput("Use cached snapshot")
        scrollShellTo("pending-request-answer-request-question")
        composeRule.onNodeWithTag("pending-request-answer-request-question")
            .assertIsNotEnabled()
    }

    @Test
    fun navigationSelectionDispatchesRequestedSection() {
        val selectedSections = mutableListOf<SupervisorShellSection>()

        renderShell(
            onSectionSelected = { selectedSections += it },
        )

        composeRule.onNodeWithText("Projects")
            .performClick()
        composeRule.onNodeWithText("Sessions")
            .performClick()
        composeRule.onNodeWithText("Inbox")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    SupervisorShellSection.PROJECTS,
                    SupervisorShellSection.SESSIONS,
                    SupervisorShellSection.INBOX,
                ),
                selectedSections,
            )
        }
    }

    @Test
    fun shellHeaderLogoutDispatchesCallback() {
        var logoutCalled = false

        renderShell(
            onLogout = { logoutCalled = true },
        )

        composeRule.onNodeWithTag("shell-logout")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(true, logoutCalled)
        }
    }

    @Test
    fun projectsSectionRendersWorkspaceSummary() {
        renderShell(
            shellState = shellState(selectedSection = SupervisorShellSection.PROJECTS),
        )

        composeRule.onNodeWithText("Project summary")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Yep Anywhere")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Active relay workspace")
            .assertIsDisplayed()
    }

    @Test
    fun sessionsSectionRendersUnreadIndicators() {
        renderShell(
            shellState = shellState(
                selectedSection = SupervisorShellSection.SESSIONS,
                sessions = listOf(
                    SessionSummary(
                        id = "session-1",
                        projectId = "project-1",
                        title = "Android shell",
                        status = SessionStatus.RUNNING,
                        updatedLabel = "now",
                        hasUnread = true,
                    ),
                ),
            ),
            sessionsState = sessionsState(
                sessions = listOf(
                    SessionSummary(
                        id = "session-1",
                        projectId = "project-1",
                        title = "Android shell",
                        status = SessionStatus.RUNNING,
                        updatedLabel = "now",
                        hasUnread = true,
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("Session summary")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Android shell")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Unread")
            .assertCountEquals(2)
    }

    @Test
    fun inboxSectionRendersUnreadKind() {
        renderShell(
            shellState = shellState(selectedSection = SupervisorShellSection.INBOX),
        )

        composeRule.onNodeWithText("Inbox summary")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Question")
            .assertIsDisplayed()
    }

    private fun renderShell(
        shellState: SupervisorShellScreenState = shellState(),
        projectsState: ProjectsScreenState = projectsState(),
        sessionsState: SessionsScreenState = sessionsState(),
        inboxState: InboxScreenState = inboxState(),
        activeSessionState: ActiveSessionScreenState = activeSessionState(),
        callbacks: ActiveSessionCallbacks = ActiveSessionCallbacks(
            onSendReply = {},
            onApproveRequest = {},
            onDenyRequest = { _, _ -> },
            onAnswerQuestion = { _, _ -> },
        ),
        onSectionSelected: (SupervisorShellSection) -> Unit = {},
        onLogout: () -> Unit = {},
    ) {
        composeRule.setContent {
            SupervisorShellScreen(
                state = shellState,
                projectsState = projectsState,
                sessionsState = sessionsState,
                inboxState = inboxState,
                activeSessionState = activeSessionState,
                activeSessionCallbacks = callbacks,
                onSectionSelected = onSectionSelected,
                onLogout = onLogout,
            )
        }
    }

    private fun scrollShellTo(tag: String) {
        composeRule.onNodeWithTag("supervisor-shell-scroll")
            .performScrollToNode(hasTestTag(tag))
    }

    private fun shellState(
        selectedSection: SupervisorShellSection = SupervisorShellSection.ACTIVE,
        pendingRequests: List<PendingInputRequest> = listOf(questionRequest()),
        timeline: SessionTimeline = timeline(),
        sessions: List<SessionSummary> = sessionsState().sessions,
    ): SupervisorShellScreenState {
        return SupervisorShellScreenState(
            title = "Yep Anywhere Android",
            subtitle = "Android-owned cache/storage layer for Room, DataStore, and secure relay session persistence.",
            snapshot = SupervisorShellSnapshot(
                connectionStatus = RelayConnectionStatus.CONNECTED,
                projects = projectsState().projects,
                sessions = sessions,
                inboxItems = inboxState().items,
                timeline = timeline,
                pendingRequests = pendingRequests,
                ),
            selectedSection = selectedSection,
        )
    }

    private fun projectsState(): ProjectsScreenState {
        return ProjectsScreenState(
            title = "Projects",
            subtitle = "Project summary",
            projects = listOf(ProjectSummary(id = "project-1", name = "Yep Anywhere", isActive = true)),
        )
    }

    private fun sessionsState(
        sessions: List<SessionSummary> = listOf(
            SessionSummary(
                id = "session-1",
                projectId = "project-1",
                title = "Android shell",
                status = SessionStatus.RUNNING,
                updatedLabel = "now",
                hasUnread = false,
            ),
        ),
    ): SessionsScreenState {
        return SessionsScreenState(
            title = "Sessions",
            subtitle = "Session summary",
            sessions = sessions,
        )
    }

    private fun inboxState(): InboxScreenState {
        return InboxScreenState(
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
        )
    }

    private fun activeSessionState(
        pendingRequests: List<PendingInputRequest> = listOf(questionRequest()),
        timeline: SessionTimeline = timeline(),
    ): ActiveSessionScreenState {
        return ActiveSessionScreenState(
            title = "Active session",
            subtitle = "Foreground realtime shell for session detail and approvals.",
            timeline = timeline,
            pendingRequests = pendingRequests,
        )
    }

    private fun timeline(): SessionTimeline {
        return SessionTimeline(
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
        )
    }

    private fun disconnectedTimeline(): SessionTimeline {
        return timeline().copy(
            connectionStatus = RelayConnectionStatus.DISCONNECTED,
        )
    }

    private fun questionRequest(): PendingInputRequest {
        return PendingInputRequest(
            id = "request-question",
            sessionId = "session-1",
            title = "Choose sync strategy",
            body = "Use cache-first read-only snapshots for offline mode?",
            kind = InboxItemKind.QUESTION,
        )
    }

    private fun approvalRequest(): PendingInputRequest {
        return PendingInputRequest(
            id = "request-approval",
            sessionId = "session-1",
            title = "Allow relay reconnect",
            body = "Approve foreground reconnect for this session?",
            kind = InboxItemKind.APPROVAL,
        )
    }
}
