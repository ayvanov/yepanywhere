package com.yepanywhere.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yepanywhere.android.core.usecase.AnswerQuestionUseCase
import com.yepanywhere.android.core.usecase.ApproveRequestUseCase
import com.yepanywhere.android.core.usecase.DenyRequestUseCase
import com.yepanywhere.android.core.usecase.ObserveActiveSessionUseCase
import com.yepanywhere.android.core.usecase.SendSessionReplyUseCase
import com.yepanywhere.android.core.model.SessionDetail
import com.yepanywhere.android.core.model.SessionDetailQuery
import com.yepanywhere.android.core.repository.SessionsRepository
import com.yepanywhere.android.ui.ActiveSessionScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface ActiveSessionCommandHandler {
    fun sendReply(text: String)

    fun approve(requestId: String)

    fun deny(
        requestId: String,
        feedback: String? = null,
    )

    fun answerQuestion(
        requestId: String,
        answer: String,
    )

    fun refreshSessionDetail(query: SessionDetailQuery = SessionDetailQuery()) = Unit

    fun refreshMetadata() = Unit
}

class ActiveSessionViewModel(
    private val observeActiveSessionUseCase: ObserveActiveSessionUseCase,
    private val sendSessionReplyUseCase: SendSessionReplyUseCase,
    private val approveRequestUseCase: ApproveRequestUseCase,
    private val denyRequestUseCase: DenyRequestUseCase,
    private val answerQuestionUseCase: AnswerQuestionUseCase,
    private val activeSessionId: String,
    private val sessionsRepository: SessionsRepository? = null,
    scope: CoroutineScope? = null,
) : ViewModel(), ActiveSessionCommandHandler {
    private val coroutineScope = scope ?: viewModelScope
    private var selectedProjectId: String? = null
    private var selectedSessionId: String? = null

    private val mutableUiState = MutableStateFlow(
        ActiveSessionScreenState(
            title = "Active session",
            subtitle = "Foreground realtime shell for session detail and approvals.",
            timeline = emptyTimeline(activeSessionId),
            pendingRequests = emptyList(),
        )
    )
    val uiState: StateFlow<ActiveSessionScreenState> = mutableUiState.asStateFlow()

    init {
        coroutineScope.launch {
            observeActiveSessionUseCase(sessionId = activeSessionId).collect { activeSession ->
                val selected = selectedSessionId
                if (selected == null || selected == activeSessionId) {
                    mutableUiState.update { current ->
                        current.copy(
                            timeline = activeSession.timeline,
                            pendingRequests = activeSession.pendingRequests,
                        )
                    }
                }
            }
        }
    }

    fun openSession(
        projectId: String,
        sessionId: String,
    ) {
        selectedProjectId = projectId
        selectedSessionId = sessionId
        coroutineScope.launch {
            refreshSessionDetail()
        }
    }

    override fun refreshSessionDetail(query: SessionDetailQuery) {
        val projectId = selectedProjectId
        val sessionId = selectedSessionId
        if (projectId.isNullOrBlank() || sessionId.isNullOrBlank()) {
            return
        }
        coroutineScope.launch {
            mutableUiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            runCatching {
                requireNotNull(sessionsRepository) { "sessions_repository_required" }
                    .loadSessionDetail(
                        projectId = projectId,
                        sessionId = sessionId,
                        query = query,
                    )
            }.onSuccess(::applySessionDetail)
                .onFailure { error ->
                    mutableUiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = error.message ?: "Failed to refresh session.",
                        )
                    }
                }
        }
    }

    override fun refreshMetadata() {
        val projectId = selectedProjectId
        val sessionId = selectedSessionId
        if (projectId.isNullOrBlank() || sessionId.isNullOrBlank()) {
            return
        }
        coroutineScope.launch {
            mutableUiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            runCatching {
                requireNotNull(sessionsRepository) { "sessions_repository_required" }
                    .loadSessionMetadata(
                        projectId = projectId,
                        sessionId = sessionId,
                    )
            }.onSuccess(::applySessionDetail)
                .onFailure { error ->
                    mutableUiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = error.message ?: "Failed to refresh session metadata.",
                        )
                    }
                }
        }
    }

    override fun sendReply(text: String) {
        coroutineScope.launch {
            sendSessionReplyUseCase(
                sessionId = selectedSessionId ?: activeSessionId,
                text = text,
            )
        }
    }

    override fun approve(requestId: String) {
        coroutineScope.launch {
            approveRequestUseCase(requestId)
        }
    }

    private fun applySessionDetail(detail: SessionDetail) {
        mutableUiState.update { current ->
            current.copy(
                title = detail.session.title,
                subtitle = detail.session.projectId,
                timeline = detail.timeline,
                pendingRequests = detail.pendingInputRequest?.let(::listOf) ?: emptyList(),
                session = detail.session,
                ownership = detail.ownership,
                processId = detail.processId,
                processState = detail.processState,
                permissionMode = detail.permissionMode,
                modeVersion = detail.modeVersion,
                model = detail.model,
                slashCommands = detail.slashCommands,
                pagination = detail.pagination,
                isRefreshing = false,
                errorMessage = null,
            )
        }
    }

    override fun deny(
        requestId: String,
        feedback: String?,
    ) {
        coroutineScope.launch {
            denyRequestUseCase(
                requestId = requestId,
                feedback = feedback,
            )
        }
    }

    override fun answerQuestion(
        requestId: String,
        answer: String,
    ) {
        coroutineScope.launch {
            answerQuestionUseCase(
                requestId = requestId,
                answer = answer,
            )
        }
    }

    companion object {
        fun factory(
            observeActiveSessionUseCase: ObserveActiveSessionUseCase,
            sendSessionReplyUseCase: SendSessionReplyUseCase,
            approveRequestUseCase: ApproveRequestUseCase,
            denyRequestUseCase: DenyRequestUseCase,
            answerQuestionUseCase: AnswerQuestionUseCase,
            activeSessionId: String,
            sessionsRepository: SessionsRepository? = null,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == ActiveSessionViewModel::class.java)
                    @Suppress("UNCHECKED_CAST")
                    return ActiveSessionViewModel(
                        observeActiveSessionUseCase = observeActiveSessionUseCase,
                        sendSessionReplyUseCase = sendSessionReplyUseCase,
                        approveRequestUseCase = approveRequestUseCase,
                        denyRequestUseCase = denyRequestUseCase,
                        answerQuestionUseCase = answerQuestionUseCase,
                        activeSessionId = activeSessionId,
                        sessionsRepository = sessionsRepository,
                    ) as T
                }
            }
        }
    }
}

private fun emptyTimeline(sessionId: String) =
    com.yepanywhere.android.core.model.SessionTimeline(
        sessionId = sessionId,
        connectionStatus = com.yepanywhere.android.core.model.RelayConnectionStatus.DISCONNECTED,
        messages = emptyList(),
    )
