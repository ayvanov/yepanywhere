package com.yepanywhere.android

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yepanywhere.android.core.model.AgentMapping
import com.yepanywhere.android.core.model.AgentSession
import com.yepanywhere.android.core.model.FileContent
import com.yepanywhere.android.core.model.FileMetadata
import com.yepanywhere.android.core.model.GitDiffResult
import com.yepanywhere.android.core.model.GitFileChange
import com.yepanywhere.android.core.model.GitStatusInfo
import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.InboxItemKind
import com.yepanywhere.android.core.model.InboxTier
import com.yepanywhere.android.core.model.MessageContentBlock
import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.PendingSessionMessage
import com.yepanywhere.android.core.model.PatchHunk
import com.yepanywhere.android.core.model.ProcessModelOption
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.SessionAttachment
import com.yepanywhere.android.core.model.SessionMessage
import com.yepanywhere.android.core.model.SessionMessageAuthor
import com.yepanywhere.android.core.model.SessionProcessInfo
import com.yepanywhere.android.core.model.SessionStatus
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import com.yepanywhere.android.core.model.SessionUploadProgress
import com.yepanywhere.android.core.model.SupervisorShellSnapshot
import com.yepanywhere.android.ui.ActiveSessionCallbacks
import com.yepanywhere.android.ui.ActiveSessionScreenState
import com.yepanywhere.android.ui.AgentsScreenState
import com.yepanywhere.android.ui.FileScreenState
import com.yepanywhere.android.ui.GitStatusCallbacks
import com.yepanywhere.android.ui.GitStatusScreenState
import com.yepanywhere.android.ui.InboxScreenState
import com.yepanywhere.android.ui.ProjectsScreenState
import com.yepanywhere.android.ui.SessionsScreenState
import com.yepanywhere.android.ui.SupervisorShellScreen
import com.yepanywhere.android.ui.SupervisorShellScreenState
import com.yepanywhere.android.ui.SupervisorShellSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupervisorShellScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setPortraitBaseline() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
    }

    @Test
    fun activeSessionShellUsesScrollableContentContainer() {
        renderShell()

        composeRule.onNodeWithTag("supervisor-shell-scroll")
            .assert(hasScrollAction())
    }

    @Test
    fun wideShellShowsSidebarAndWorkspace() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.resources.configuration.screenWidthDp >= 840
        }

        renderShell(
            shellState = shellState(
                selectedSection = SupervisorShellSection.PROJECTS,
                selectedProjectId = "project-1",
            ),
        )

        composeRule.onNodeWithTag("supervisor-shell-wide")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("supervisor-sidebar")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("supervisor-workspace")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("supervisor-wide-prompt")
            .assertIsDisplayed()
        composeRule.onNodeWithText("What should we build in Yep Anywhere?")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("supervisor-wide-composer")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Ask Yep Anywhere anything")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("supervisor-wide-context", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Yep Anywhere Android")
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("Review my recent commits for correctness risks and maintainability concerns")
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("Settings")
            .assertCountEquals(0)
    }

    @Test
    fun compactShellKeepsScrollableContentContainer() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.resources.configuration.screenWidthDp < 840
        }

        renderShell()

        composeRule.onNodeWithTag("supervisor-shell-compact")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("supervisor-shell-scroll")
            .assert(hasScrollAction())
        composeRule.onAllNodesWithText("Settings")
            .assertCountEquals(0)
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

        scrollShellTo("reply-input")
        composeRule.onNodeWithTag("reply-input")
            .performTextInput("  Need update from Android shell  ")
        scrollShellTo("reply-send")
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
    fun approvalRequestApproveAcceptEditsDispatchesRequestId() {
        var approvedAcceptEditsRequestId: String? = null

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
                onApproveAcceptEditsRequest = { approvedAcceptEditsRequestId = it },
                onDenyRequest = { _, _ -> },
                onAnswerQuestion = { _, _ -> },
            ),
        )

        scrollShellTo("pending-request-approve-accept-edits-request-approval")
        composeRule.onNodeWithTag("pending-request-approve-accept-edits-request-approval")
            .performClick()

        composeRule.runOnIdle {
            assertEquals("request-approval", approvedAcceptEditsRequestId)
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
        composeRule.onNodeWithTag("pending-request-approve-accept-edits-request-approval")
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
    fun activeSessionRendersTypedBlocksAndExpandsLongToolOutput() {
        val longOutput = (1..20).joinToString("\n") { index -> "output-line-$index" }
        val timeline = typedBlockTimeline(longOutput)

        renderShell(
            activeSessionState = activeSessionState(
                pendingRequests = emptyList(),
                timeline = timeline,
            ),
            shellState = shellState(
                pendingRequests = emptyList(),
                timeline = timeline,
            ),
        )

        scrollShellTo("message-block-msg-typed-1")
        composeRule.onNodeWithText("Preparing patch")
            .assertIsDisplayed()
        scrollShellToText("Thinking")
        composeRule.onNodeWithText("Thinking")
            .assertIsDisplayed()
        scrollShellToText("Tool use: Bash")
        composeRule.onNodeWithText("Tool use: Bash")
            .assertIsDisplayed()
        scrollShellToText("npm test")
        composeRule.onNodeWithText("npm test")
            .assertIsDisplayed()
        scrollShellToText("Tool result")
        composeRule.onNodeWithText("Tool result")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("output-line-20")
            .assertCountEquals(0)

        scrollShellToText("Expand output")
        composeRule.onNodeWithText("Expand output")
            .assertIsDisplayed()
    }

    @Test
    fun activeSessionInputRendersDeferredAttachmentsHoldAndStopControls() {
        var queuedText: String? = null
        var cancelledTempId: String? = null
        var held: Boolean? = null
        var stopped = false

        renderShell(
            activeSessionState = activeSessionState(
                pendingRequests = emptyList(),
                timeline = timeline(),
            ).copy(
                ownership = "self",
                processId = "process-1",
                processState = "in-turn",
                isHeld = false,
                attachments = listOf(
                    SessionAttachment(
                        id = "upload-1",
                        name = "trace.log",
                        sizeBytes = 1_024,
                        mimeType = "text/plain",
                    ),
                ),
                uploadProgress = listOf(
                    SessionUploadProgress(
                        fileId = "upload-2",
                        fileName = "screenshot.png",
                        bytesUploaded = 25,
                        totalBytes = 100,
                    ),
                ),
                deferredMessages = listOf(
                    PendingSessionMessage(
                        tempId = "deferred-1",
                        text = "Queued after current turn",
                        deferred = true,
                    ),
                ),
            ),
            callbacks = ActiveSessionCallbacks(
                onSendReply = {},
                onApproveRequest = {},
                onDenyRequest = { _, _ -> },
                onAnswerQuestion = { _, _ -> },
                onQueueDeferredReply = { queuedText = it },
                onCancelDeferredMessage = { cancelledTempId = it },
                onHoldChanged = { held = it },
                onStopSession = { stopped = true },
            ),
        )

        scrollShellTo("reply-input")
        composeRule.onNodeWithTag("reply-input")
            .performTextInput("  Queue after current turn  ")
        scrollShellToText("trace.log")
        composeRule.onNodeWithText("trace.log")
            .assertIsDisplayed()
        scrollShellToText("screenshot.png 25%")
        composeRule.onNodeWithText("screenshot.png 25%")
            .assertIsDisplayed()
        scrollShellToText("Queued after current turn")
        composeRule.onNodeWithText("Queued after current turn")
            .assertIsDisplayed()

        scrollShellTo("reply-queue")
        composeRule.onNodeWithTag("reply-queue")
            .performClick()
        scrollShellTo("deferred-cancel-deferred-1")
        composeRule.onNodeWithTag("deferred-cancel-deferred-1")
            .performClick()
        scrollShellTo("session-hold")
        composeRule.onNodeWithTag("session-hold")
            .performClick()
        scrollShellTo("session-stop")
        composeRule.onNodeWithTag("session-stop")
            .performClick()

        composeRule.runOnIdle {
            assertEquals("Queue after current turn", queuedText)
            assertEquals("deferred-1", cancelledTempId)
            assertEquals(true, held)
            assertEquals(true, stopped)
        }
    }

    @Test
    fun activeSessionProcessControlsLoadAndSwitchModel() {
        var processInfoLoads = 0
        var processModelLoads = 0
        var switchedModel: String? = null

        renderShell(
            activeSessionState = activeSessionState(
                processInfo = SessionProcessInfo(
                    id = "process-1",
                    sessionId = "session-android-shell",
                    projectId = "project-yep",
                    projectName = "Yep Anywhere",
                    state = "in-turn",
                    provider = "claude",
                    model = "sonnet",
                    thinking = "enabled",
                    pid = 1234,
                ),
                processModels = listOf(
                    ProcessModelOption(id = "sonnet", name = "Sonnet"),
                    ProcessModelOption(id = "opus", name = "Opus"),
                ),
            ),
            callbacks = ActiveSessionCallbacks(
                onSendReply = {},
                onApproveRequest = {},
                onDenyRequest = { _, _ -> },
                onAnswerQuestion = { _, _ -> },
                onLoadProcessInfo = { processInfoLoads += 1 },
                onLoadProcessModels = { processModelLoads += 1 },
                onSwitchProcessModel = { switchedModel = it },
            ),
        )

        scrollShellTo("process-info-load")
        composeRule.onNodeWithTag("process-info-load")
            .performClick()
        composeRule.onNodeWithTag("process-models-load")
            .performClick()
        composeRule.onNodeWithTag("process-info-summary")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Process process-1")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("process-model-opus")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, processInfoLoads)
            assertEquals(1, processModelLoads)
            assertEquals("opus", switchedModel)
        }
    }

    @Test
    fun agentsSectionRendersActiveAgentsAndDispatchesSessionSelection() {
        var selectedSession: Pair<String, String>? = null

        renderShell(
            shellState = shellState(selectedSection = SupervisorShellSection.AGENTS),
            agentsState = AgentsScreenState(
                activeAgents = listOf(
                    SessionProcessInfo(
                        id = "process-1",
                        sessionId = "session-android",
                        projectId = "project-1",
                        projectName = "Yep Anywhere",
                        sessionTitle = "Android supervisor",
                        state = "in-turn",
                        provider = "claude",
                        model = "sonnet",
                    ),
                ),
                idleAgents = listOf(
                    SessionProcessInfo(
                        id = "process-2",
                        sessionId = "session-idle",
                        projectId = "project-1",
                        projectName = "Yep Anywhere",
                        sessionTitle = "Idle supervisor",
                        state = "idle",
                    ),
                ),
                terminatedAgents = listOf(
                    SessionProcessInfo(
                        id = "process-3",
                        sessionId = "session-stopped",
                        projectId = "project-1",
                        projectName = "Yep Anywhere",
                        sessionTitle = "Stopped supervisor",
                        state = "stopped",
                    ),
                ),
            ),
            onSessionSelected = { projectId, sessionId ->
                selectedSession = projectId to sessionId
            },
        )

        composeRule.onNodeWithText("Active agents")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Android supervisor")
            .performClick()

        composeRule.runOnIdle {
            assertEquals("project-1" to "session-android", selectedSession)
        }
    }

    @Test
    fun activeSessionSubagentMappingsLoadAndRenderAgentSession() {
        var mappingsLoaded = 0
        var loadedAgentId: String? = null

        renderShell(
            activeSessionState = activeSessionState(
                pendingRequests = emptyList(),
                timeline = timeline(),
            ).copy(
                agentMappings = listOf(AgentMapping(toolUseId = "tool-1", agentId = "agent-1")),
                selectedAgentId = "agent-1",
                selectedAgentSession = AgentSession(
                    status = "completed",
                    messages = listOf(
                        SessionMessage(
                            id = "agent-msg-1",
                            author = SessionMessageAuthor.ASSISTANT,
                            body = "Subagent completed renderer work",
                            timestampLabel = "12:30",
                            blocks = listOf(MessageContentBlock.Task("Renderer task", "Subagent completed renderer work")),
                        ),
                    ),
                ),
            ),
            callbacks = ActiveSessionCallbacks(
                onSendReply = {},
                onApproveRequest = {},
                onDenyRequest = { _, _ -> },
                onAnswerQuestion = { _, _ -> },
                onLoadAgentMappings = { mappingsLoaded += 1 },
                onLoadAgentSession = { loadedAgentId = it },
            ),
        )

        scrollShellTo("agent-mappings-load")
        composeRule.onNodeWithTag("agent-mappings-load")
            .performClick()
        scrollShellTo("agent-session-agent-1")
        composeRule.onNodeWithTag("agent-session-agent-1")
            .performClick()
        scrollShellTo("message-block-agent-msg-1-0")
        composeRule.onNodeWithTag("message-block-agent-msg-1-0")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(1, mappingsLoaded)
            assertEquals("agent-1", loadedAgentId)
        }
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
    fun projectsSectionDispatchesProjectSelection() {
        var selectedProjectId: String? = null

        renderShell(
            shellState = shellState(selectedSection = SupervisorShellSection.PROJECTS),
            onProjectSelected = { selectedProjectId = it },
        )

        composeRule.onNodeWithText("Yep Anywhere")
            .performClick()

        composeRule.runOnIdle {
            assertEquals("project-1", selectedProjectId)
        }
    }

    @Test
    fun sessionsSectionRendersUnreadIndicators() {
        renderShell(
            shellState = shellState(
                selectedSection = SupervisorShellSection.SESSIONS,
                selectedProjectId = "project-1",
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
        composeRule.onNodeWithText("Unread")
            .assertIsDisplayed()
    }

    @Test
    fun sessionsSectionHidesShellHeaderAndSummary() {
        renderShell(
            shellState = shellState(
                selectedSection = SupervisorShellSection.SESSIONS,
                selectedProjectId = "project-1",
            ),
        )

        composeRule.onAllNodesWithText("Yep Anywhere Android")
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("Android-owned cache/storage layer for Room, DataStore, and secure relay session persistence.")
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("Connection")
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("Attention")
            .assertCountEquals(0)
    }

    @Test
    fun sessionsSectionShowsProjectContextAndCanSelectAnotherProject() {
        var selectedProjectId: String? = null

        renderShell(
            shellState = shellState(
                selectedSection = SupervisorShellSection.SESSIONS,
                selectedProjectId = "project-1",
            ),
            projectsState = projectsState(
                projects = listOf(
                    ProjectSummary(id = "project-1", name = "Yep Anywhere", isActive = true),
                    ProjectSummary(id = "project-2", name = "Relay Backend", isActive = false),
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
                        hasUnread = false,
                    ),
                    SessionSummary(
                        id = "session-2",
                        projectId = "project-2",
                        title = "Relay session",
                        status = SessionStatus.IDLE,
                        updatedLabel = "yesterday",
                        hasUnread = false,
                    ),
                ),
            ),
            onProjectSelected = { selectedProjectId = it },
        )

        composeRule.onNodeWithTag("sessions-project-name")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Yep Anywhere")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Android shell")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Relay session")
            .assertCountEquals(0)

        composeRule.onNodeWithText("Relay Backend")
            .performClick()

        composeRule.runOnIdle {
            assertEquals("project-2", selectedProjectId)
        }
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

    @Test
    fun inboxSectionRendersTiersProjectFilterAndReadActions() {
        var selectedProjectId: String? = null
        var markedReadSessionId: String? = null
        var markedUnreadSessionId: String? = null

        renderShell(
            shellState = shellState(selectedSection = SupervisorShellSection.INBOX),
            projectsState = projectsState(
                projects = listOf(
                    ProjectSummary(id = "project-1", name = "Yep Anywhere"),
                    ProjectSummary(id = "project-2", name = "Relay Backend"),
                ),
            ),
            inboxState = InboxScreenState(
                title = "Inbox",
                subtitle = "Inbox tiers",
                selectedProjectId = "project-1",
                projects = listOf(
                    ProjectSummary(id = "project-1", name = "Yep Anywhere"),
                    ProjectSummary(id = "project-2", name = "Relay Backend"),
                ),
                items = listOf(
                    InboxItem(
                        id = "needs-attention-1",
                        projectId = "project-1",
                        sessionId = "session-1",
                        title = "Approval required",
                        subtitle = "Review command",
                        kind = InboxItemKind.APPROVAL,
                        tier = InboxTier.NEEDS_ATTENTION,
                        isUnread = true,
                    ),
                    InboxItem(
                        id = "active-1",
                        projectId = "project-1",
                        sessionId = "session-2",
                        title = "Session update",
                        subtitle = "In turn",
                        kind = InboxItemKind.NOTIFICATION,
                        tier = InboxTier.ACTIVE,
                        isUnread = false,
                    ),
                    InboxItem(
                        id = "recent-1",
                        projectId = "project-1",
                        sessionId = "session-3",
                        title = "Session update",
                        subtitle = "Recently changed session",
                        kind = InboxItemKind.NOTIFICATION,
                        tier = InboxTier.RECENT_ACTIVITY,
                        isUnread = false,
                    ),
                    InboxItem(
                        id = "unread-8h-1",
                        projectId = "project-1",
                        sessionId = "session-4",
                        title = "Session update",
                        subtitle = "Unread within 8h",
                        kind = InboxItemKind.NOTIFICATION,
                        tier = InboxTier.UNREAD_8H,
                        isUnread = true,
                    ),
                    InboxItem(
                        id = "unread-24h-other",
                        projectId = "project-2",
                        sessionId = "session-5",
                        title = "Session update",
                        subtitle = "Hidden by project filter",
                        kind = InboxItemKind.NOTIFICATION,
                        tier = InboxTier.UNREAD_24H,
                        isUnread = true,
                    ),
                ),
            ),
            onInboxProjectSelected = { selectedProjectId = it },
            onInboxMarkRead = { markedReadSessionId = it },
            onInboxMarkUnread = { markedUnreadSessionId = it },
        )

        composeRule.onNodeWithText("Needs attention")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Active")
            .assertIsDisplayed()
        scrollShellToText("Recent activity")
        composeRule.onNodeWithText("Recent activity")
            .assertIsDisplayed()
        scrollShellToText("Unread 8h")
        composeRule.onNodeWithText("Unread 8h")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Hidden by project filter")
            .assertCountEquals(0)

        composeRule.onNodeWithTag("inbox-project-project-2")
            .performClick()
        scrollShellTo("inbox-mark-read-session-4")
        composeRule.onNodeWithTag("inbox-mark-read-session-4")
            .performClick()
        composeRule.onNodeWithTag("inbox-mark-unread-session-2")
            .performClick()

        composeRule.runOnIdle {
            assertEquals("project-2", selectedProjectId)
            assertEquals("session-4", markedReadSessionId)
            assertEquals("session-2", markedUnreadSessionId)
        }
    }

    @Test
    fun fileSectionRendersHighlightedContent() {
        renderShell(
            shellState = shellState(selectedSection = SupervisorShellSection.FILE),
            fileState = FileScreenState(
                projectId = "project-1",
                path = "src/Main.kt",
                file = FileContent(
                    metadata = FileMetadata(
                        path = "src/Main.kt",
                        size = 24,
                        mimeType = "text/kotlin",
                        isText = true,
                    ),
                    rawUrl = "/api/projects/project-1/files/raw?path=src%2FMain.kt",
                    content = "val version = 2",
                    highlightedLanguage = "kotlin",
                ),
            ),
        )

        composeRule.onNodeWithTag("file-viewer")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("file-highlight-language")
            .assertIsDisplayed()
    }

    @Test
    fun gitSectionRendersStatusDiffAndFullContextAction() {
        var openedDiffPath: String? = null
        var fullContextLoaded = false

        renderShell(
            shellState = shellState(selectedSection = SupervisorShellSection.GIT_STATUS),
            gitStatusState = GitStatusScreenState(
                projectId = "project-1",
                status = GitStatusInfo(
                    isGitRepo = true,
                    branch = "feature/android",
                    upstream = "origin/main",
                    isClean = false,
                    files = listOf(
                        GitFileChange(
                            path = "src/Main.kt",
                            status = "M",
                            staged = false,
                            linesAdded = 2,
                            linesDeleted = 1,
                        ),
                    ),
                ),
                selectedFile = GitFileChange(path = "src/Main.kt", status = "M", staged = false),
                diff = GitDiffResult(
                    structuredPatch = listOf(
                        PatchHunk(
                            oldStart = 1,
                            oldLines = 1,
                            newStart = 1,
                            newLines = 1,
                            lines = listOf("-val version = 1", "+val version = 2"),
                        ),
                    ),
                ),
            ),
            gitStatusCallbacks = GitStatusCallbacks(
                onOpenDiff = { openedDiffPath = it.path },
                onLoadFullContext = { fullContextLoaded = true },
            ),
        )

        composeRule.onNodeWithTag("git-status-summary")
            .assertIsDisplayed()
        composeRule.onNodeWithText("feature/android")
            .assertIsDisplayed()
        composeRule.onNodeWithText("M • unstaged • +2 • -1")
            .performClick()
        composeRule.onNodeWithTag("git-diff-full-context")
            .performClick()
        composeRule.runOnIdle {
            assertEquals("src/Main.kt", openedDiffPath)
            assertEquals(true, fullContextLoaded)
        }
    }

    @Test
    fun messageFileOperationDispatchesFileRoute() {
        var openedFilePath: String? = null

        renderShell(
            activeSessionState = activeSessionState(
                pendingRequests = emptyList(),
                timeline = timeline().copy(
                    messages = listOf(
                        SessionMessage(
                            id = "msg-file",
                            author = SessionMessageAuthor.ASSISTANT,
                            body = "Read file",
                            timestampLabel = "now",
                            blocks = listOf(
                                MessageContentBlock.FileOperation(
                                    operation = "read",
                                    path = "src/Main.kt",
                                    content = "val version = 2",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            onFileSelected = { openedFilePath = it },
        )

        scrollShellTo("message-file-link-msg-file-0")
        composeRule.onNodeWithTag("message-file-link-msg-file-0")
            .performClick()
        composeRule.runOnIdle {
            assertEquals("src/Main.kt", openedFilePath)
        }
    }

    private fun renderShell(
        shellState: SupervisorShellScreenState = shellState(),
        projectsState: ProjectsScreenState = projectsState(),
        sessionsState: SessionsScreenState = sessionsState(),
        inboxState: InboxScreenState = inboxState(),
        activeSessionState: ActiveSessionScreenState = activeSessionState(),
        agentsState: AgentsScreenState = AgentsScreenState(),
        fileState: FileScreenState = FileScreenState(),
        gitStatusState: GitStatusScreenState = GitStatusScreenState(),
        gitStatusCallbacks: GitStatusCallbacks = GitStatusCallbacks(),
        callbacks: ActiveSessionCallbacks = ActiveSessionCallbacks(
            onSendReply = {},
            onApproveRequest = {},
            onDenyRequest = { _, _ -> },
            onAnswerQuestion = { _, _ -> },
        ),
        onSectionSelected: (SupervisorShellSection) -> Unit = {},
        onProjectSelected: (String) -> Unit = {},
        onSessionSelected: (projectId: String, sessionId: String) -> Unit = { _, _ -> },
        onFileSelected: (String) -> Unit = {},
        onInboxProjectSelected: (String?) -> Unit = {},
        onInboxMarkRead: (String) -> Unit = {},
        onInboxMarkUnread: (String) -> Unit = {},
        onLogout: () -> Unit = {},
    ) {
        composeRule.setContent {
            SupervisorShellScreen(
                state = shellState,
                projectsState = projectsState,
                sessionsState = sessionsState,
                agentsState = agentsState,
                inboxState = inboxState,
                activeSessionState = activeSessionState,
                fileState = fileState,
                gitStatusState = gitStatusState,
                activeSessionCallbacks = callbacks,
                gitStatusCallbacks = gitStatusCallbacks,
                onSectionSelected = onSectionSelected,
                onProjectSelected = onProjectSelected,
                onSessionSelected = onSessionSelected,
                onFileSelected = onFileSelected,
                onInboxProjectSelected = onInboxProjectSelected,
                onInboxMarkRead = onInboxMarkRead,
                onInboxMarkUnread = onInboxMarkUnread,
                onLogout = onLogout,
            )
        }
    }

    private fun scrollShellTo(tag: String) {
        composeRule.onNodeWithTag("supervisor-shell-scroll")
            .performScrollToNode(hasTestTag(tag))
    }

    private fun scrollShellToText(text: String) {
        composeRule.onNodeWithTag("supervisor-shell-scroll")
            .performScrollToNode(hasText(text))
    }

    private fun shellState(
        selectedSection: SupervisorShellSection = SupervisorShellSection.ACTIVE,
        selectedProjectId: String? = null,
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
            selectedProjectId = selectedProjectId,
        )
    }

    private fun projectsState(
        projects: List<ProjectSummary> = listOf(ProjectSummary(id = "project-1", name = "Yep Anywhere", isActive = true)),
    ): ProjectsScreenState {
        return ProjectsScreenState(
            title = "Projects",
            subtitle = "Project summary",
            projects = projects,
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
        processInfo: SessionProcessInfo? = null,
        processModels: List<ProcessModelOption> = emptyList(),
    ): ActiveSessionScreenState {
        return ActiveSessionScreenState(
            title = "Active session",
            subtitle = "Foreground realtime shell for session detail and approvals.",
            timeline = timeline,
            pendingRequests = pendingRequests,
            ownership = "self",
            processId = "process-1",
            processState = "in-turn",
            model = "sonnet",
            processInfo = processInfo,
            processModels = processModels,
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

    private fun typedBlockTimeline(longOutput: String): SessionTimeline {
        return SessionTimeline(
            sessionId = "session-1",
            connectionStatus = RelayConnectionStatus.CONNECTED,
            messages = listOf(
                SessionMessage(
                    id = "msg-typed",
                    author = SessionMessageAuthor.ASSISTANT,
                    body = "Preparing patch\nchecking project state\nnpm test\n$longOutput",
                    timestampLabel = "10:15",
                    blocks = listOf(
                        MessageContentBlock.Text("Preparing patch"),
                        MessageContentBlock.Thinking("checking project state"),
                        MessageContentBlock.ToolUse(
                            name = "Bash",
                            input = "npm test",
                            callId = "tool-1",
                        ),
                        MessageContentBlock.ToolResult(
                            content = longOutput,
                            toolUseId = "tool-1",
                        ),
                    ),
                ),
            ),
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
