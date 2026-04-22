package com.yepanywhere.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yepanywhere.android.core.usecase.AnswerQuestionUseCase
import com.yepanywhere.android.core.usecase.ApproveRequestUseCase
import com.yepanywhere.android.core.usecase.DenyRequestUseCase
import com.yepanywhere.android.core.usecase.ObserveActiveSessionUseCase
import com.yepanywhere.android.core.usecase.SendSessionReplyUseCase
import com.yepanywhere.android.ui.ActiveSessionScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
}

class ActiveSessionViewModel(
    private val observeActiveSessionUseCase: ObserveActiveSessionUseCase,
    private val sendSessionReplyUseCase: SendSessionReplyUseCase,
    private val approveRequestUseCase: ApproveRequestUseCase,
    private val denyRequestUseCase: DenyRequestUseCase,
    private val answerQuestionUseCase: AnswerQuestionUseCase,
    private val activeSessionId: String,
    scope: CoroutineScope? = null,
) : ViewModel(), ActiveSessionCommandHandler {
    private val coroutineScope = scope ?: viewModelScope

    val uiState: StateFlow<ActiveSessionScreenState> = observeActiveSessionUseCase(sessionId = activeSessionId).map { activeSession ->
        ActiveSessionScreenState(
            title = "Active session",
            subtitle = "Foreground realtime shell for session detail and approvals.",
            timeline = activeSession.timeline,
            pendingRequests = activeSession.pendingRequests,
        )
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ActiveSessionScreenState(
            title = "Active session",
            subtitle = "Foreground realtime shell for session detail and approvals.",
            timeline = emptyTimeline(activeSessionId),
            pendingRequests = emptyList(),
        ),
    )

    override fun sendReply(text: String) {
        coroutineScope.launch {
            sendSessionReplyUseCase(
                sessionId = activeSessionId,
                text = text,
            )
        }
    }

    override fun approve(requestId: String) {
        coroutineScope.launch {
            approveRequestUseCase(requestId)
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
