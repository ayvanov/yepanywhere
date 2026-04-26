package com.yepanywhere.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yepanywhere.android.core.model.GlobalSessionFilters
import com.yepanywhere.android.core.repository.SessionsRepository
import com.yepanywhere.android.core.usecase.ObserveInboxUseCase
import com.yepanywhere.android.core.usecase.ObserveProjectsUseCase
import com.yepanywhere.android.ui.InboxScreenState
import com.yepanywhere.android.ui.ProjectsScreenState
import com.yepanywhere.android.ui.SessionsScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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
    private val sessionsRepository: SessionsRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope
    private var nextAfter: String? = null

    private val mutableUiState = MutableStateFlow(
        SessionsScreenState(
            title = "Sessions",
            subtitle = "Global sessions with filters, pagination, and bulk metadata actions.",
            sessions = emptyList(),
        ),
    )
    val uiState: StateFlow<SessionsScreenState> = mutableUiState.asStateFlow()

    init {
        coroutineScope.launch {
            sessionsRepository.observeSessions().collect { sessions ->
                mutableUiState.update { current ->
                    if (current.isLoading) {
                        current
                    } else {
                        current.copy(sessions = sessions)
                    }
                }
            }
        }
    }

    fun applyFilters(
        project: String? = null,
        query: String? = null,
        status: String? = null,
        provider: String? = null,
        executor: String? = null,
        age: String? = null,
        includeArchived: Boolean = false,
        starred: Boolean = false,
    ) {
        val filters = GlobalSessionFilters(
            project = project.blankToNull(),
            query = query.blankToNull(),
            status = status.blankToNull(),
            provider = provider.blankToNull(),
            executor = executor.blankToNull(),
            age = age.blankToNull(),
            includeArchived = includeArchived,
            starred = starred,
        )
        coroutineScope.launch {
            loadPage(filters = filters, after = null, append = false)
        }
    }

    fun loadMore() {
        val state = mutableUiState.value
        if (!state.hasMore || state.isLoading) {
            return
        }
        coroutineScope.launch {
            loadPage(filters = state.filters, after = nextAfter, append = true)
        }
    }

    fun toggleSelection(sessionId: String) {
        mutableUiState.update { state ->
            val selected = state.selectedSessionIds
            state.copy(
                selectedSessionIds = if (sessionId in selected) selected - sessionId else selected + sessionId,
            )
        }
    }

    fun clearSelection() {
        mutableUiState.update { it.copy(selectedSessionIds = emptySet()) }
    }

    fun bulkArchiveSelected() {
        val selected = mutableUiState.value.selectedSessionIds
        if (selected.isEmpty()) return
        coroutineScope.launch {
            sessionsRepository.bulkArchive(selected, archived = true)
            mutableUiState.update { state ->
                state.copy(
                    sessions = state.sessions.filterNot { it.id in selected },
                    selectedSessionIds = emptySet(),
                )
            }
        }
    }

    fun bulkStarSelected() {
        val selected = mutableUiState.value.selectedSessionIds
        if (selected.isEmpty()) return
        coroutineScope.launch {
            sessionsRepository.bulkStar(selected, starred = true)
            mutableUiState.update { state ->
                state.copy(
                    sessions = state.sessions.map { session ->
                        if (session.id in selected) session.copy(isStarred = true) else session
                    },
                    selectedSessionIds = emptySet(),
                )
            }
        }
    }

    fun bulkMarkReadSelected() {
        val selected = mutableUiState.value.selectedSessionIds
        if (selected.isEmpty()) return
        coroutineScope.launch {
            sessionsRepository.bulkMarkSeen(selected)
            mutableUiState.update { state ->
                state.copy(
                    sessions = state.sessions.map { session ->
                        if (session.id in selected) session.copy(hasUnread = false) else session
                    },
                    selectedSessionIds = emptySet(),
                )
            }
        }
    }

    fun bulkMarkUnreadSelected() {
        val selected = mutableUiState.value.selectedSessionIds
        if (selected.isEmpty()) return
        coroutineScope.launch {
            sessionsRepository.bulkMarkUnread(selected)
            mutableUiState.update { state ->
                state.copy(
                    sessions = state.sessions.map { session ->
                        if (session.id in selected) session.copy(hasUnread = true) else session
                    },
                    selectedSessionIds = emptySet(),
                )
            }
        }
    }

    private suspend fun loadPage(
        filters: GlobalSessionFilters,
        after: String?,
        append: Boolean,
    ) {
        mutableUiState.update { it.copy(isLoading = true, filters = filters) }
        val page = sessionsRepository.loadGlobalSessions(
            filters = filters,
            after = after,
        )
        nextAfter = page.nextAfter
        mutableUiState.update { state ->
            state.copy(
                sessions = if (append) {
                    (state.sessions + page.sessions).distinctBy { it.id }
                } else {
                    page.sessions
                },
                filters = filters,
                hasMore = page.hasMore,
                isLoading = false,
                stats = page.stats,
            )
        }
    }

    companion object {
        fun factory(sessionsRepository: SessionsRepository): ViewModelProvider.Factory {
            return sectionFactory { SessionsScreenViewModel(sessionsRepository = sessionsRepository) }
        }
    }
}

private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

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
