package com.yepanywhere.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yepanywhere.android.data.SupervisorShellDataSource
import com.yepanywhere.android.ui.ActiveSessionScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ActiveSessionViewModel(
    private val dataSource: SupervisorShellDataSource,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope

    val uiState: StateFlow<ActiveSessionScreenState> = dataSource.shellState.map { snapshot ->
        ActiveSessionScreenState(
            title = "Active session",
            subtitle = "Foreground realtime shell for session detail and approvals.",
            timeline = snapshot.timeline,
            pendingRequests = snapshot.pendingRequests,
        )
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ActiveSessionScreenState(
            title = "Active session",
            subtitle = "Foreground realtime shell for session detail and approvals.",
            timeline = dataSource.shellState.value.timeline,
            pendingRequests = dataSource.shellState.value.pendingRequests,
        ),
    )

    companion object {
        fun factory(dataSource: SupervisorShellDataSource): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == ActiveSessionViewModel::class.java)
                    @Suppress("UNCHECKED_CAST")
                    return ActiveSessionViewModel(dataSource = dataSource) as T
                }
            }
        }
    }
}
