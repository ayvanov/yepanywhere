package com.yepanywhere.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yepanywhere.android.core.usecase.AnswerQuestionUseCase
import com.yepanywhere.android.core.usecase.ApproveRequestUseCase
import com.yepanywhere.android.core.usecase.DenyRequestUseCase
import com.yepanywhere.android.core.usecase.ObserveActiveSessionUseCase
import com.yepanywhere.android.core.usecase.SendSessionReplyUseCase
import com.yepanywhere.android.core.model.PendingSessionMessage
import com.yepanywhere.android.core.model.SessionAttachment
import com.yepanywhere.android.core.model.SessionDetail
import com.yepanywhere.android.core.model.SessionDetailQuery
import com.yepanywhere.android.core.model.SessionInputRequest
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

    fun approveAcceptEdits(requestId: String) = Unit

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

    fun updateDraft(text: String) = Unit

    fun queueDeferredMessage(text: String? = null) = Unit

    fun cancelDeferredMessage(tempId: String) = Unit

    fun addAttachment(attachment: SessionAttachment) = Unit

    fun removeAttachment(attachmentId: String) = Unit

    fun setHold(hold: Boolean) = Unit

    fun stopSession() = Unit

    fun loadProcessInfo() = Unit

    fun loadProcessModels() = Unit

    fun switchProcessModel(modelId: String) = Unit
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
    private var pendingInputCounter = 0

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
        val tempId = nextTempId()
        val pendingMessage = PendingSessionMessage(
            tempId = tempId,
            text = text.trim(),
            status = "Sending",
            deferred = false,
            attachments = mutableUiState.value.attachments,
        )
        mutableUiState.update {
            it.copy(
                draft = "",
                attachments = emptyList(),
                pendingMessages = it.pendingMessages + pendingMessage,
                inputErrorMessage = null,
            )
        }
        coroutineScope.launch {
            runCatching {
                sendSessionReplyUseCase(
                    sessionId = selectedSessionId ?: activeSessionId,
                    text = text,
                )
            }.onSuccess {
                mutableUiState.update { state ->
                    state.copy(pendingMessages = state.pendingMessages.filterNot { it.tempId == tempId })
                }
            }.onFailure { error ->
                mutableUiState.update { state ->
                    state.copy(
                        pendingMessages = state.pendingMessages.filterNot { it.tempId == tempId },
                        draft = text,
                        attachments = pendingMessage.attachments,
                        inputErrorMessage = error.message ?: "Failed to send message.",
                    )
                }
            }
        }
    }

    override fun updateDraft(text: String) {
        mutableUiState.update { it.copy(draft = text) }
    }

    override fun addAttachment(attachment: SessionAttachment) {
        mutableUiState.update { state ->
            state.copy(attachments = (state.attachments.filterNot { it.id == attachment.id } + attachment))
        }
    }

    override fun removeAttachment(attachmentId: String) {
        mutableUiState.update { state ->
            state.copy(attachments = state.attachments.filterNot { it.id == attachmentId })
        }
    }

    override fun queueDeferredMessage(text: String?) {
        val message = (text ?: mutableUiState.value.draft).trim()
        val attachments = mutableUiState.value.attachments
        if (message.isBlank() && attachments.isEmpty()) {
            return
        }
        val sessionId = selectedSessionId ?: activeSessionId
        val tempId = nextTempId()
        val pending = PendingSessionMessage(
            tempId = tempId,
            text = message,
            status = "Queued",
            deferred = true,
            attachments = attachments,
        )
        mutableUiState.update { state ->
            state.copy(
                draft = "",
                attachments = emptyList(),
                deferredMessages = state.deferredMessages + pending,
                isSubmittingInput = true,
                inputErrorMessage = null,
            )
        }
        coroutineScope.launch {
            runCatching {
                requireNotNull(sessionsRepository) { "sessions_repository_required" }
                    .queueSessionInput(
                        sessionId = sessionId,
                        request = SessionInputRequest(
                            message = message,
                            attachments = attachments,
                            tempId = tempId,
                            deferred = true,
                        ),
                    )
            }.onSuccess {
                mutableUiState.update { it.copy(isSubmittingInput = false) }
            }.onFailure { error ->
                mutableUiState.update { state ->
                    state.copy(
                        deferredMessages = state.deferredMessages.filterNot { it.tempId == tempId },
                        draft = message,
                        attachments = attachments,
                        isSubmittingInput = false,
                        inputErrorMessage = error.message ?: "Failed to queue message.",
                    )
                }
            }
        }
    }

    override fun cancelDeferredMessage(tempId: String) {
        val sessionId = selectedSessionId ?: activeSessionId
        coroutineScope.launch {
            runCatching {
                requireNotNull(sessionsRepository) { "sessions_repository_required" }
                    .cancelDeferredMessage(sessionId, tempId)
            }.onSuccess {
                mutableUiState.update { state ->
                    state.copy(deferredMessages = state.deferredMessages.filterNot { it.tempId == tempId })
                }
            }.onFailure { error ->
                mutableUiState.update {
                    it.copy(inputErrorMessage = error.message ?: "Failed to cancel queued message.")
                }
            }
        }
    }

    override fun setHold(hold: Boolean) {
        val sessionId = selectedSessionId ?: activeSessionId
        mutableUiState.update { it.copy(isHeld = hold) }
        coroutineScope.launch {
            runCatching {
                requireNotNull(sessionsRepository) { "sessions_repository_required" }
                    .setSessionHold(sessionId, hold)
            }.onSuccess { isHeld ->
                mutableUiState.update { it.copy(isHeld = isHeld) }
            }.onFailure { error ->
                mutableUiState.update {
                    it.copy(
                        isHeld = !hold,
                        inputErrorMessage = error.message ?: "Failed to update hold state.",
                    )
                }
            }
        }
    }

    override fun stopSession() {
        val processId = mutableUiState.value.processId ?: return
        coroutineScope.launch {
            runCatching {
                val repository = requireNotNull(sessionsRepository) { "sessions_repository_required" }
                val interrupted = repository.interruptProcess(processId)
                if (!interrupted.success || !interrupted.supported) {
                    repository.abortProcess(processId)
                }
            }.onFailure { error ->
                mutableUiState.update {
                    it.copy(inputErrorMessage = error.message ?: "Failed to stop session.")
                }
            }
        }
    }

    override fun loadProcessInfo() {
        val sessionId = selectedSessionId ?: activeSessionId
        mutableUiState.update {
            it.copy(isLoadingProcessInfo = true, processControlErrorMessage = null)
        }
        coroutineScope.launch {
            runCatching {
                requireNotNull(sessionsRepository) { "sessions_repository_required" }
                    .getProcessInfo(sessionId)
            }.onSuccess { processInfo ->
                mutableUiState.update {
                    it.copy(
                        processInfo = processInfo,
                        isLoadingProcessInfo = false,
                        processControlErrorMessage = null,
                    )
                }
            }.onFailure { error ->
                mutableUiState.update {
                    it.copy(
                        isLoadingProcessInfo = false,
                        processControlErrorMessage = error.message ?: "Failed to load process info.",
                    )
                }
            }
        }
    }

    override fun loadProcessModels() {
        val processId = mutableUiState.value.processId ?: return
        mutableUiState.update { it.copy(processControlErrorMessage = null) }
        coroutineScope.launch {
            runCatching {
                requireNotNull(sessionsRepository) { "sessions_repository_required" }
                    .getProcessModels(processId)
            }.onSuccess { models ->
                mutableUiState.update {
                    it.copy(processModels = models, processControlErrorMessage = null)
                }
            }.onFailure { error ->
                mutableUiState.update {
                    it.copy(processControlErrorMessage = error.message ?: "Failed to load process models.")
                }
            }
        }
    }

    override fun switchProcessModel(modelId: String) {
        val processId = mutableUiState.value.processId ?: return
        mutableUiState.update { it.copy(isSwitchingModel = true, processControlErrorMessage = null) }
        coroutineScope.launch {
            runCatching {
                requireNotNull(sessionsRepository) { "sessions_repository_required" }
                    .setProcessModel(processId = processId, model = modelId)
            }.onSuccess { result ->
                mutableUiState.update {
                    it.copy(
                        model = result.model ?: modelId,
                        isSwitchingModel = false,
                        processControlErrorMessage = null,
                    )
                }
            }.onFailure { error ->
                mutableUiState.update {
                    it.copy(
                        isSwitchingModel = false,
                        processControlErrorMessage = error.message ?: "Failed to switch model.",
                    )
                }
            }
        }
    }

    override fun approve(requestId: String) {
        coroutineScope.launch {
            approveRequestUseCase(requestId)
        }
    }

    override fun approveAcceptEdits(requestId: String) {
        coroutineScope.launch {
            approveRequestUseCase(requestId = requestId, acceptEdits = true)
            mutableUiState.update { it.copy(permissionMode = "acceptEdits") }
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
                isHeld = detail.processState == "held",
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

    private fun nextTempId(): String {
        pendingInputCounter += 1
        return "android-${pendingInputCounter}"
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
