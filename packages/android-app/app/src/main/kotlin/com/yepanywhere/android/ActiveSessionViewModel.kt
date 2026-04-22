package com.yepanywhere.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yepanywhere.android.core.usecase.ObserveActiveSessionUseCase
import com.yepanywhere.android.ui.ActiveSessionScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ActiveSessionViewModel(
    private val observeActiveSessionUseCase: ObserveActiveSessionUseCase,
    private val activeSessionId: String,
    scope: CoroutineScope? = null,
) : ViewModel() {
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

    companion object {
        fun factory(
            observeActiveSessionUseCase: ObserveActiveSessionUseCase,
            activeSessionId: String,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == ActiveSessionViewModel::class.java)
                    @Suppress("UNCHECKED_CAST")
                    return ActiveSessionViewModel(
                        observeActiveSessionUseCase = observeActiveSessionUseCase,
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
