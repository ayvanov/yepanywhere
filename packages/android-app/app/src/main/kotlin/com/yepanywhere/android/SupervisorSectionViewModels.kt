package com.yepanywhere.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yepanywhere.android.data.SupervisorShellDataSource
import com.yepanywhere.android.ui.InboxScreenState
import com.yepanywhere.android.ui.ProjectsScreenState
import com.yepanywhere.android.ui.SessionsScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ProjectsScreenViewModel(
    private val dataSource: SupervisorShellDataSource,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope

    val uiState: StateFlow<ProjectsScreenState> = dataSource.shellState.map { snapshot ->
        ProjectsScreenState(
            title = "Projects",
            subtitle = "Cached project summaries for the Supervisor MVP.",
            projects = snapshot.projects,
        )
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ProjectsScreenState(
            title = "Projects",
            subtitle = "Cached project summaries for the Supervisor MVP.",
            projects = dataSource.shellState.value.projects,
        ),
    )

    companion object {
        fun factory(dataSource: SupervisorShellDataSource): ViewModelProvider.Factory {
            return sectionFactory { ProjectsScreenViewModel(dataSource = dataSource) }
        }
    }
}

class SessionsScreenViewModel(
    private val dataSource: SupervisorShellDataSource,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope

    val uiState: StateFlow<SessionsScreenState> = dataSource.shellState.map { snapshot ->
        SessionsScreenState(
            title = "Sessions",
            subtitle = "Supervisor-ready session summaries with attention state.",
            sessions = snapshot.sessions,
        )
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = SessionsScreenState(
            title = "Sessions",
            subtitle = "Supervisor-ready session summaries with attention state.",
            sessions = dataSource.shellState.value.sessions,
        ),
    )

    companion object {
        fun factory(dataSource: SupervisorShellDataSource): ViewModelProvider.Factory {
            return sectionFactory { SessionsScreenViewModel(dataSource = dataSource) }
        }
    }
}

class InboxScreenViewModel(
    private val dataSource: SupervisorShellDataSource,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope

    val uiState: StateFlow<InboxScreenState> = dataSource.shellState.map { snapshot ->
        InboxScreenState(
            title = "Inbox",
            subtitle = "Minimal notification and approval feed for mobile supervision.",
            items = snapshot.inboxItems,
        )
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = InboxScreenState(
            title = "Inbox",
            subtitle = "Minimal notification and approval feed for mobile supervision.",
            items = dataSource.shellState.value.inboxItems,
        ),
    )

    companion object {
        fun factory(dataSource: SupervisorShellDataSource): ViewModelProvider.Factory {
            return sectionFactory { InboxScreenViewModel(dataSource = dataSource) }
        }
    }
}

private inline fun <reified T : ViewModel> sectionFactory(
    crossinline create: () -> T,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
            require(modelClass == T::class.java)
            @Suppress("UNCHECKED_CAST")
            return create() as VM
        }
    }
}
