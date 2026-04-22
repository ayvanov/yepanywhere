package com.yepanywhere.android

import com.yepanywhere.android.core.model.InboxItemKind
import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.SessionMessage
import com.yepanywhere.android.core.model.SessionMessageAuthor
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import com.yepanywhere.android.core.repository.ApprovalsRepository
import com.yepanywhere.android.core.repository.SessionsRepository
import com.yepanywhere.android.core.usecase.ObserveActiveSessionUseCase
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
        val viewModel = ActiveSessionViewModel(
            observeActiveSessionUseCase = ObserveActiveSessionUseCase(
                sessionsRepository = FakeSessionsRepository(timeline),
                approvalsRepository = FakeApprovalsRepository(pendingRequests),
            ),
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

    private class FakeSessionsRepository(
        private val timeline: MutableStateFlow<SessionTimeline>,
    ) : SessionsRepository {
        override fun observeSessions(projectId: String?): Flow<List<SessionSummary>> = MutableStateFlow(emptyList())

        override suspend fun refreshSessions(projectId: String?) = Unit

        override fun observeSessionTimeline(sessionId: String): Flow<SessionTimeline> = timeline

        override suspend fun sendReply(sessionId: String, text: String) = Unit
    }

    private class FakeApprovalsRepository(
        private val pendingRequests: MutableStateFlow<List<PendingInputRequest>>,
    ) : ApprovalsRepository {
        override fun observePendingApprovals(): Flow<List<PendingInputRequest>> = pendingRequests

        override suspend fun approve(requestId: String) = Unit

        override suspend fun deny(requestId: String, feedback: String?) = Unit

        override suspend fun answerQuestion(requestId: String, answer: String) = Unit
    }
}
