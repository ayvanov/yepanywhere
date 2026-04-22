package com.yepanywhere.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yepanywhere.android.core.usecase.ObserveInboxUseCase
import com.yepanywhere.android.core.usecase.ObserveProjectsUseCase
import com.yepanywhere.android.core.usecase.ObserveSessionsUseCase
import com.yepanywhere.android.ui.InboxScreenState
import com.yepanywhere.android.ui.ProjectsScreenState
import com.yepanywhere.android.ui.SessionsScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ProjectsScreenViewModel(
    private val observeProjectsUseCase: ObserveProjectsUseCase,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope

    val uiState: StateFlow<ProjectsScreenState> = observeProjectsUseCase().map { projects ->
        ProjectsScreenState(
            title = "Projects",
            subtitle = "Cached project summaries for the Supervisor MVP.",
            projects = projects,
        )
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ProjectsScreenState(
            title = "Projects",
            subtitle = "Cached project summaries for the Supervisor MVP.",
            projects = emptyList(),
        ),
    )

    companion object {
        fun factory(observeProjectsUseCase: ObserveProjectsUseCase): ViewModelProvider.Factory {
            return sectionFactory { ProjectsScreenViewModel(observeProjectsUseCase = observeProjectsUseCase) }
        }
    }
}

class SessionsScreenViewModel(
    private val observeSessionsUseCase: ObserveSessionsUseCase,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope

    val uiState: StateFlow<SessionsScreenState> = observeSessionsUseCase().map { sessions ->
        SessionsScreenState(
            title = "Sessions",
            subtitle = "Supervisor-ready session summaries with attention state.",
            sessions = sessions,
        )
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = SessionsScreenState(
            title = "Sessions",
            subtitle = "Supervisor-ready session summaries with attention state.",
            sessions = emptyList(),
        ),
    )

    companion object {
        fun factory(observeSessionsUseCase: ObserveSessionsUseCase): ViewModelProvider.Factory {
            return sectionFactory { SessionsScreenViewModel(observeSessionsUseCase = observeSessionsUseCase) }
        }
    }
}

class InboxScreenViewModel(
    private val observeInboxUseCase: ObserveInboxUseCase,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope

    val uiState: StateFlow<InboxScreenState> = observeInboxUseCase().map { inboxItems ->
        InboxScreenState(
            title = "Inbox",
            subtitle = "Minimal notification and approval feed for mobile supervision.",
            items = inboxItems,
        )
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = InboxScreenState(
            title = "Inbox",
            subtitle = "Minimal notification and approval feed for mobile supervision.",
            items = emptyList(),
        ),
    )

    companion object {
        fun factory(observeInboxUseCase: ObserveInboxUseCase): ViewModelProvider.Factory {
            return sectionFactory { InboxScreenViewModel(observeInboxUseCase = observeInboxUseCase) }
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
