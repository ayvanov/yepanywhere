package com.yepanywhere.android

import com.yepanywhere.android.core.model.InboxItemKind
import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.ProcessControlResult
import com.yepanywhere.android.core.model.SessionAttachment
import com.yepanywhere.android.core.model.SessionInputRequest
import com.yepanywhere.android.core.model.SessionMessage
import com.yepanywhere.android.core.model.SessionMessageAuthor
import com.yepanywhere.android.core.model.SessionDetail
import com.yepanywhere.android.core.model.SessionDetailQuery
import com.yepanywhere.android.core.model.SessionPaginationInfo
import com.yepanywhere.android.core.model.SessionStatus
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import com.yepanywhere.android.core.model.SlashCommand
import com.yepanywhere.android.core.repository.ApprovalsRepository
import com.yepanywhere.android.core.repository.SessionsRepository
import com.yepanywhere.android.core.usecase.AnswerQuestionUseCase
import com.yepanywhere.android.core.usecase.ApproveRequestUseCase
import com.yepanywhere.android.core.usecase.DenyRequestUseCase
import com.yepanywhere.android.core.usecase.ObserveActiveSessionUseCase
import com.yepanywhere.android.core.usecase.SendSessionReplyUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionViewModelTest {
    @Test
    fun mapsTimelineAndPendingRequestsIntoDedicatedActiveSessionState() = runTest {
        val timeline = MutableStateFlow(
            SessionTimeline(
                sessionId = "session-android-shell",
                connectionStatus = com.yepanywhere.android.core.model.RelayConnectionStatus.CONNECTED,
                messages = listOf(
                    SessionMessage(
                        id = "msg-1",
                        author = SessionMessageAuthor.ASSISTANT,
                        body = "Session detail is ready for extraction.",
                        timestampLabel = "09:45",
                    ),
                ),
            ),
        )
        val pendingRequests = MutableStateFlow(
            listOf(
                PendingInputRequest(
                    id = "request-active",
                    sessionId = "session-android-shell",
                    title = "Choose sync strategy",
                    body = "Use cache-first read-only snapshots for offline mode?",
                    kind = InboxItemKind.QUESTION,
                ),
                PendingInputRequest(
                    id = "request-other",
                    sessionId = "session-other",
                    title = "Unrelated approval",
                    body = "This should not appear in active session.",
                    kind = InboxItemKind.APPROVAL,
                ),
            ),
        )
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val sessionsRepository = FakeSessionsRepository(timeline)
        val approvalsRepository = FakeApprovalsRepository(pendingRequests)
        val viewModel = ActiveSessionViewModel(
            observeActiveSessionUseCase = ObserveActiveSessionUseCase(
                sessionsRepository = sessionsRepository,
                approvalsRepository = approvalsRepository,
            ),
            sendSessionReplyUseCase = SendSessionReplyUseCase(sessionsRepository),
            approveRequestUseCase = ApproveRequestUseCase(approvalsRepository),
            denyRequestUseCase = DenyRequestUseCase(approvalsRepository),
            answerQuestionUseCase = AnswerQuestionUseCase(approvalsRepository),
            activeSessionId = "session-android-shell",
            scope = externalScope,
        )
        val collectionJob = externalScope.launch {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()

        assertEquals("Active session", viewModel.uiState.value.title)
        assertEquals(timeline.value, viewModel.uiState.value.timeline)
        assertEquals(1, viewModel.uiState.value.pendingRequests.size)
        assertEquals("request-active", viewModel.uiState.value.pendingRequests.single().id)

        timeline.value = timeline.value.copy(
            messages = timeline.value.messages + SessionMessage(
                id = "msg-2",
                author = SessionMessageAuthor.USER,
                body = "Keep moving toward real repositories.",
                timestampLabel = "09:46",
            ),
        )
        pendingRequests.value = pendingRequests.value.filter { it.sessionId == "session-android-shell" }

        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.timeline.messages.size)
        assertEquals(1, viewModel.uiState.value.pendingRequests.size)

        collectionJob.cancel()
        externalScope.cancel()
    }

    @Test
    fun opensRoutedSessionAndRefreshesDetailAndMetadata() = runTest {
        val timeline = MutableStateFlow(emptyTimeline())
        val pendingRequests = MutableStateFlow(emptyList<PendingInputRequest>())
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val sessionsRepository = FakeSessionsRepository(
            timeline = timeline,
            detail = sessionDetail(
                title = "Loaded detail",
                ownership = "self",
                model = "opus",
                processState = "waiting-input",
                permissionMode = "acceptEdits",
                slashCommands = listOf(SlashCommand(name = "/model", description = "Switch model")),
            ),
            metadata = sessionDetail(
                title = "Metadata title",
                ownership = "external",
                model = "sonnet",
                processState = "idle",
                permissionMode = "default",
                slashCommands = listOf(SlashCommand(name = "/help", description = "Help")),
            ),
        )
        val viewModel = ActiveSessionViewModel(
            observeActiveSessionUseCase = ObserveActiveSessionUseCase(
                sessionsRepository = sessionsRepository,
                approvalsRepository = FakeApprovalsRepository(pendingRequests),
            ),
            sendSessionReplyUseCase = SendSessionReplyUseCase(sessionsRepository),
            approveRequestUseCase = ApproveRequestUseCase(FakeApprovalsRepository(pendingRequests)),
            denyRequestUseCase = DenyRequestUseCase(FakeApprovalsRepository(pendingRequests)),
            answerQuestionUseCase = AnswerQuestionUseCase(FakeApprovalsRepository(pendingRequests)),
            activeSessionId = "session-android-shell",
            sessionsRepository = sessionsRepository,
            scope = externalScope,
        )
        val collectionJob = externalScope.launch {
            viewModel.uiState.collect {}
        }

        viewModel.openSession(projectId = "project-yep", sessionId = "session-detail")
        advanceUntilIdle()

        assertEquals("session-detail", viewModel.uiState.value.timeline.sessionId)
        assertEquals("Loaded detail", viewModel.uiState.value.session?.title)
        assertEquals("self", viewModel.uiState.value.ownership)
        assertEquals("opus", viewModel.uiState.value.model)
        assertEquals("waiting-input", viewModel.uiState.value.processState)
        assertEquals("acceptEdits", viewModel.uiState.value.permissionMode)
        assertEquals(listOf("/model"), viewModel.uiState.value.slashCommands.map { it.name })
        assertEquals(true, viewModel.uiState.value.pagination?.hasOlderMessages)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(
            listOf("project-yep|session-detail|SessionDetailQuery(afterMessageId=null, beforeMessageId=null, tailCompactions=null)"),
            sessionsRepository.detailLoads,
        )

        viewModel.refreshMetadata()
        advanceUntilIdle()

        assertEquals("Metadata title", viewModel.uiState.value.session?.title)
        assertEquals("external", viewModel.uiState.value.ownership)
        assertEquals("sonnet", viewModel.uiState.value.model)
        assertEquals(listOf("/help"), viewModel.uiState.value.slashCommands.map { it.name })
        assertEquals(listOf("project-yep|session-detail"), sessionsRepository.metadataLoads)

        collectionJob.cancel()
        externalScope.cancel()
    }

    @Test
    fun forwardsReplyAndApprovalCommandsToUseCases() = runTest {
        val timeline = MutableStateFlow(emptyTimeline())
        val pendingRequests = MutableStateFlow(emptyList<PendingInputRequest>())
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val sessionsRepository = FakeSessionsRepository(timeline)
        val approvalsRepository = FakeApprovalsRepository(pendingRequests)
        val viewModel = ActiveSessionViewModel(
            observeActiveSessionUseCase = ObserveActiveSessionUseCase(
                sessionsRepository = sessionsRepository,
                approvalsRepository = approvalsRepository,
            ),
            sendSessionReplyUseCase = SendSessionReplyUseCase(sessionsRepository),
            approveRequestUseCase = ApproveRequestUseCase(approvalsRepository),
            denyRequestUseCase = DenyRequestUseCase(approvalsRepository),
            answerQuestionUseCase = AnswerQuestionUseCase(approvalsRepository),
            activeSessionId = "session-android-shell",
            scope = externalScope,
        )

        viewModel.sendReply("Reply from Android")
        viewModel.approve("request-1")
        viewModel.approveAcceptEdits("request-accept-edits")
        viewModel.deny(
            requestId = "request-2",
            feedback = "Need another option",
        )
        viewModel.answerQuestion(
            requestId = "request-3",
            answer = "Use cache-first.",
        )

        advanceUntilIdle()

        assertEquals(listOf("session-android-shell|Reply from Android"), sessionsRepository.sentReplies)
        assertEquals(listOf("request-1"), approvalsRepository.approvedRequestIds)
        assertEquals(listOf("request-accept-edits"), approvalsRepository.approvedAcceptEditsRequestIds)
        assertEquals(listOf("request-2|Need another option"), approvalsRepository.deniedRequests)
        assertEquals(listOf("request-3|Use cache-first."), approvalsRepository.answeredRequests)
        assertEquals("acceptEdits", viewModel.uiState.value.permissionMode)

        externalScope.cancel()
    }

    @Test
    fun managesDraftDeferredMessagesAttachmentsHoldAndStopControls() = runTest {
        val timeline = MutableStateFlow(emptyTimeline())
        val pendingRequests = MutableStateFlow(emptyList<PendingInputRequest>())
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val sessionsRepository = FakeSessionsRepository(
            timeline = timeline,
            detail = sessionDetail(
                ownership = "self",
                processState = "in-turn",
            ),
        )
        val viewModel = ActiveSessionViewModel(
            observeActiveSessionUseCase = ObserveActiveSessionUseCase(
                sessionsRepository = sessionsRepository,
                approvalsRepository = FakeApprovalsRepository(pendingRequests),
            ),
            sendSessionReplyUseCase = SendSessionReplyUseCase(sessionsRepository),
            approveRequestUseCase = ApproveRequestUseCase(FakeApprovalsRepository(pendingRequests)),
            denyRequestUseCase = DenyRequestUseCase(FakeApprovalsRepository(pendingRequests)),
            answerQuestionUseCase = AnswerQuestionUseCase(FakeApprovalsRepository(pendingRequests)),
            activeSessionId = "session-android-shell",
            sessionsRepository = sessionsRepository,
            scope = externalScope,
        )

        viewModel.openSession(projectId = "project-yep", sessionId = "session-detail")
        advanceUntilIdle()

        viewModel.updateDraft("  Run the Android checks  ")
        viewModel.addAttachment(
            SessionAttachment(
                id = "upload-1",
                name = "trace.log",
                sizeBytes = 1_024,
                mimeType = "text/plain",
            ),
        )
        viewModel.queueDeferredMessage()
        advanceUntilIdle()

        val deferred = viewModel.uiState.value.deferredMessages.single()
        assertEquals("Run the Android checks", deferred.text)
        assertEquals(true, deferred.deferred)
        assertEquals(listOf("trace.log"), deferred.attachments.map { it.name })
        assertEquals("", viewModel.uiState.value.draft)
        assertEquals(emptyList(), viewModel.uiState.value.attachments)
        assertEquals(
            listOf(
                SessionInputRequest(
                    message = "Run the Android checks",
                    attachments = listOf(
                        SessionAttachment(
                            id = "upload-1",
                            name = "trace.log",
                            sizeBytes = 1_024,
                            mimeType = "text/plain",
                        ),
                    ),
                    tempId = deferred.tempId,
                    deferred = true,
                ),
            ),
            sessionsRepository.queuedInputs,
        )

        viewModel.cancelDeferredMessage(deferred.tempId)
        viewModel.setHold(true)
        viewModel.stopSession()
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.uiState.value.deferredMessages)
        assertEquals(true, viewModel.uiState.value.isHeld)
        assertEquals(listOf("session-detail|${deferred.tempId}"), sessionsRepository.cancelledDeferred)
        assertEquals(listOf("session-detail|true"), sessionsRepository.holdChanges)
        assertEquals(listOf("process-1"), sessionsRepository.interruptedProcesses)
        assertEquals(emptyList(), sessionsRepository.abortedProcesses)

        externalScope.cancel()
    }

    private class FakeSessionsRepository(
        private val timeline: MutableStateFlow<SessionTimeline>,
        private val detail: SessionDetail? = null,
        private val metadata: SessionDetail? = null,
    ) : SessionsRepository {
        val sentReplies = mutableListOf<String>()
        val detailLoads = mutableListOf<String>()
        val metadataLoads = mutableListOf<String>()
        val queuedInputs = mutableListOf<SessionInputRequest>()
        val cancelledDeferred = mutableListOf<String>()
        val holdChanges = mutableListOf<String>()
        val interruptedProcesses = mutableListOf<String>()
        val abortedProcesses = mutableListOf<String>()

        override fun observeSessions(projectId: String?): Flow<List<SessionSummary>> = MutableStateFlow(emptyList())

        override suspend fun refreshSessions(projectId: String?) = Unit

        override suspend fun loadSessionDetail(
            projectId: String,
            sessionId: String,
            query: SessionDetailQuery,
        ): SessionDetail {
            detailLoads += "$projectId|$sessionId|$query"
            return detail ?: error("missing_detail")
        }

        override suspend fun loadSessionMetadata(
            projectId: String,
            sessionId: String,
        ): SessionDetail {
            metadataLoads += "$projectId|$sessionId"
            return metadata ?: error("missing_metadata")
        }

        override fun observeSessionTimeline(sessionId: String): Flow<SessionTimeline> = timeline

        override suspend fun sendReply(sessionId: String, text: String) {
            sentReplies += "$sessionId|$text"
        }

        override suspend fun queueSessionInput(
            sessionId: String,
            request: SessionInputRequest,
        ): Boolean {
            queuedInputs += request
            return true
        }

        override suspend fun cancelDeferredMessage(sessionId: String, tempId: String): Boolean {
            cancelledDeferred += "$sessionId|$tempId"
            return true
        }

        override suspend fun setSessionHold(sessionId: String, hold: Boolean): Boolean {
            holdChanges += "$sessionId|$hold"
            return hold
        }

        override suspend fun interruptProcess(processId: String): ProcessControlResult {
            interruptedProcesses += processId
            return ProcessControlResult(success = true, supported = true)
        }

        override suspend fun abortProcess(processId: String): Boolean {
            abortedProcesses += processId
            return true
        }
    }

    private class FakeApprovalsRepository(
        private val pendingRequests: MutableStateFlow<List<PendingInputRequest>>,
    ) : ApprovalsRepository {
        val approvedRequestIds = mutableListOf<String>()
        val approvedAcceptEditsRequestIds = mutableListOf<String>()
        val deniedRequests = mutableListOf<String>()
        val answeredRequests = mutableListOf<String>()

        override fun observePendingApprovals(): Flow<List<PendingInputRequest>> = pendingRequests

        override suspend fun approve(requestId: String) {
            approvedRequestIds += requestId
        }

        override suspend fun approveAcceptEdits(requestId: String) {
            approvedAcceptEditsRequestIds += requestId
        }

        override suspend fun deny(requestId: String, feedback: String?) {
            deniedRequests += "$requestId|$feedback"
        }

        override suspend fun answerQuestion(requestId: String, answer: String) {
            answeredRequests += "$requestId|$answer"
        }
    }

    private fun emptyTimeline(): SessionTimeline {
        return SessionTimeline(
            sessionId = "session-android-shell",
            connectionStatus = com.yepanywhere.android.core.model.RelayConnectionStatus.CONNECTED,
            messages = emptyList(),
        )
    }

    private fun sessionDetail(
        title: String = "Session detail",
        ownership: String = "none",
        model: String = "sonnet",
        processState: String = "idle",
        permissionMode: String = "default",
        slashCommands: List<SlashCommand> = emptyList(),
    ): SessionDetail {
        val session = SessionSummary(
            id = "session-detail",
            projectId = "project-yep",
            title = title,
            status = SessionStatus.RUNNING,
            updatedLabel = "now",
            hasUnread = false,
            provider = "claude",
            model = model,
        )
        return SessionDetail(
            session = session,
            timeline = SessionTimeline(
                sessionId = "session-detail",
                connectionStatus = com.yepanywhere.android.core.model.RelayConnectionStatus.CONNECTED,
                messages = listOf(
                    SessionMessage(
                        id = "msg-1",
                        author = SessionMessageAuthor.ASSISTANT,
                        body = "Loaded detail message",
                        timestampLabel = "now",
                    ),
                ),
            ),
            ownership = ownership,
            processId = "process-1",
            processState = processState,
            permissionMode = permissionMode,
            modeVersion = 2,
            model = model,
            slashCommands = slashCommands,
            pendingInputRequest = PendingInputRequest(
                id = "request-detail",
                sessionId = "session-detail",
                title = "Approval required",
                body = "Approve command",
                kind = InboxItemKind.APPROVAL,
            ),
            pagination = SessionPaginationInfo(
                hasOlderMessages = true,
                totalMessageCount = 42,
                returnedMessageCount = 1,
                truncatedBeforeMessageId = "msg-0",
                totalCompactions = 2,
            ),
        )
    }
}
