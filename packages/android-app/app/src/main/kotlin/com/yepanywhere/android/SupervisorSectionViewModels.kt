package com.yepanywhere.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yepanywhere.android.core.model.GlobalSessionFilters
import com.yepanywhere.android.core.model.GitFileChange
import com.yepanywhere.android.core.model.NewSessionDefaults
import com.yepanywhere.android.core.model.NewSessionOptions
import com.yepanywhere.android.core.repository.ProjectsRepository
import com.yepanywhere.android.core.repository.FilesRepository
import com.yepanywhere.android.core.repository.GitRepository
import com.yepanywhere.android.core.repository.SessionsRepository
import com.yepanywhere.android.core.usecase.ObserveInboxUseCase
import com.yepanywhere.android.core.usecase.ObserveProjectsUseCase
import com.yepanywhere.android.ui.InboxScreenState
import com.yepanywhere.android.ui.AgentsScreenState
import com.yepanywhere.android.ui.FileScreenState
import com.yepanywhere.android.ui.GitStatusScreenState
import com.yepanywhere.android.ui.NewSessionScreenState
import com.yepanywhere.android.ui.ProjectsScreenState
import com.yepanywhere.android.ui.SessionsScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

class AgentsScreenViewModel(
    private val sessionsRepository: SessionsRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope
    private val mutableUiState = MutableStateFlow(AgentsScreenState())
    val uiState: StateFlow<AgentsScreenState> = mutableUiState.asStateFlow()

    fun refresh() {
        coroutineScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                sessionsRepository.loadAgentProcesses(includeTerminated = true)
            }.onSuccess { page ->
                mutableUiState.update {
                    it.copy(
                        activeAgents = page.processes.filter { process -> process.state in activeAgentStates },
                        idleAgents = page.processes.filterNot { process -> process.state in activeAgentStates },
                        terminatedAgents = page.terminatedProcesses,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load agents.",
                    )
                }
            }
        }
    }

    companion object {
        private val activeAgentStates = setOf("in-turn", "waiting-input", "running", "active")

        fun factory(sessionsRepository: SessionsRepository): ViewModelProvider.Factory {
            return sectionFactory { AgentsScreenViewModel(sessionsRepository = sessionsRepository) }
        }
    }
}

private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

class InboxScreenViewModel(
    private val observeInboxUseCase: ObserveInboxUseCase,
    private val projectsRepository: ProjectsRepository? = null,
    private val sessionsRepository: SessionsRepository? = null,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope
    private val selectedProjectId = MutableStateFlow<String?>(null)
    private val isUpdatingReadState = MutableStateFlow(false)

    private val mutableUiState = MutableStateFlow(
        InboxScreenState(
            title = "Inbox",
            subtitle = "Priority inbox with project filtering and read-state actions.",
            items = emptyList(),
        ),
    )
    val uiState: StateFlow<InboxScreenState> = mutableUiState.asStateFlow()

    init {
        coroutineScope.launch {
            observeInboxUseCase().collect { inboxItems ->
                mutableUiState.update { state ->
                    state.copy(items = inboxItems)
                }
            }
        }
        coroutineScope.launch {
            projectsRepository?.observeProjects()?.collect { projectList ->
                mutableUiState.update { state ->
                    state.copy(projects = projectList)
                }
            }
        }
        coroutineScope.launch {
            selectedProjectId.collect { projectId ->
                mutableUiState.update { it.copy(selectedProjectId = projectId) }
            }
        }
        coroutineScope.launch {
            isUpdatingReadState.collect { updating ->
                mutableUiState.update { it.copy(isUpdatingReadState = updating) }
            }
        }
    }

    fun selectProject(projectId: String?) {
        selectedProjectId.value = projectId.blankToNull()
    }

    fun markSessionRead(sessionId: String) {
        coroutineScope.launch {
            updateReadState { repository ->
                repository.markSessionSeen(sessionId)
            }
        }
    }

    fun markSessionUnread(sessionId: String) {
        coroutineScope.launch {
            updateReadState { repository ->
                repository.markSessionUnread(sessionId)
            }
        }
    }

    private suspend fun updateReadState(action: suspend (SessionsRepository) -> Boolean) {
        val repository = sessionsRepository ?: return
        isUpdatingReadState.value = true
        try {
            action(repository)
        } finally {
            isUpdatingReadState.value = false
        }
    }

    companion object {
        fun factory(
            observeInboxUseCase: ObserveInboxUseCase,
            projectsRepository: ProjectsRepository,
            sessionsRepository: SessionsRepository,
        ): ViewModelProvider.Factory {
            return sectionFactory {
                InboxScreenViewModel(
                    observeInboxUseCase = observeInboxUseCase,
                    projectsRepository = projectsRepository,
                    sessionsRepository = sessionsRepository,
                )
            }
        }
    }
}

class NewSessionViewModel(
    private val projectsRepository: ProjectsRepository,
    private val sessionsRepository: SessionsRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope
    private val mutableUiState = MutableStateFlow(NewSessionScreenState())
    val uiState: StateFlow<NewSessionScreenState> = mutableUiState.asStateFlow()

    fun initialize() {
        coroutineScope.launch {
            val projects = projectsRepository.observeProjects().first()
            val settings = sessionsRepository.getNewSessionSettings()
            val defaults = settings.defaults
            mutableUiState.update { state ->
                state.copy(
                    projects = projects,
                    projectId = state.projectId ?: projects.firstOrNull()?.id,
                    provider = defaults.provider.orEmpty(),
                    model = defaults.model.orEmpty(),
                    permissionMode = defaults.permissionMode.orEmpty(),
                    thinking = defaults.thinking.orEmpty(),
                    executor = defaults.executor.orEmpty(),
                    executorOptions = settings.remoteExecutors,
                )
            }
        }
    }

    fun updateProject(projectId: String) = update { it.copy(projectId = projectId) }
    fun updateProvider(provider: String) = update { it.copy(provider = provider) }
    fun updateModel(model: String) = update { it.copy(model = model) }
    fun updatePermissionMode(permissionMode: String) = update { it.copy(permissionMode = permissionMode) }
    fun updateThinking(thinking: String) = update { it.copy(thinking = thinking) }
    fun updateExecutor(executor: String) = update { it.copy(executor = executor) }
    fun updatePrompt(prompt: String) = update { it.copy(prompt = prompt) }

    fun startDirect() {
        coroutineScope.launch {
            submit(twoPhase = false)
        }
    }

    fun startTwoPhase() {
        coroutineScope.launch {
            submit(twoPhase = true)
        }
    }

    fun saveDefaults() {
        coroutineScope.launch {
            val state = mutableUiState.value
            sessionsRepository.saveNewSessionDefaults(
                NewSessionDefaults(
                    provider = state.provider.blankToNull(),
                    model = state.model.blankToNull(),
                    permissionMode = state.permissionMode.blankToNull(),
                    thinking = state.thinking.blankToNull(),
                    executor = state.executor.blankToNull(),
                ),
            )
        }
    }

    private suspend fun submit(twoPhase: Boolean) {
        val state = mutableUiState.value
        val projectId = state.projectId
        val prompt = state.prompt.trim()
        if (projectId.isNullOrBlank() || prompt.isBlank()) {
            mutableUiState.update {
                it.copy(errorMessage = "Choose a project and enter a prompt.")
            }
            return
        }
        mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        val options = state.toNewSessionOptions()
        runCatching {
            if (twoPhase) {
                val created = sessionsRepository.createSession(projectId, options)
                sessionsRepository.queueMessage(created.sessionId, prompt, options)
                created
            } else {
                sessionsRepository.startSession(projectId, prompt, options)
            }
        }.onSuccess { result ->
            mutableUiState.update {
                it.copy(
                    isSubmitting = false,
                    startedSessionId = result.sessionId,
                    errorMessage = null,
                )
            }
        }.onFailure { error ->
            mutableUiState.update {
                it.copy(
                    isSubmitting = false,
                    errorMessage = error.message ?: "Failed to start session.",
                )
            }
        }
    }

    private fun update(transform: (NewSessionScreenState) -> NewSessionScreenState) {
        mutableUiState.update(transform)
    }

    companion object {
        fun factory(
            projectsRepository: ProjectsRepository,
            sessionsRepository: SessionsRepository,
        ): ViewModelProvider.Factory {
            return sectionFactory {
                NewSessionViewModel(
                    projectsRepository = projectsRepository,
                    sessionsRepository = sessionsRepository,
                )
            }
        }
    }
}

class FileScreenViewModel(
    private val filesRepository: FilesRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope
    private val mutableUiState = MutableStateFlow(FileScreenState())
    val uiState: StateFlow<FileScreenState> = mutableUiState.asStateFlow()

    fun openFile(
        projectId: String,
        path: String,
        highlight: Boolean = true,
    ) {
        coroutineScope.launch {
            mutableUiState.update {
                it.copy(
                    projectId = projectId,
                    path = path,
                    isLoading = true,
                    errorMessage = null,
                )
            }
            runCatching {
                filesRepository.loadFile(projectId = projectId, path = path, highlight = highlight)
            }.onSuccess { file ->
                mutableUiState.update {
                    it.copy(
                        title = file.metadata.path,
                        file = file,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load file.",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(filesRepository: FilesRepository): ViewModelProvider.Factory {
            return sectionFactory { FileScreenViewModel(filesRepository = filesRepository) }
        }
    }
}

class GitStatusScreenViewModel(
    private val gitRepository: GitRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope
    private val mutableUiState = MutableStateFlow(GitStatusScreenState())
    val uiState: StateFlow<GitStatusScreenState> = mutableUiState.asStateFlow()

    fun openProject(projectId: String) {
        coroutineScope.launch {
            mutableUiState.update {
                it.copy(
                    projectId = projectId,
                    isLoading = true,
                    errorMessage = null,
                    selectedFile = null,
                    diff = null,
                    showFullContext = false,
                )
            }
            runCatching {
                gitRepository.loadGitStatus(projectId)
            }.onSuccess { status ->
                mutableUiState.update {
                    it.copy(
                        status = status,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load git status.",
                    )
                }
            }
        }
    }

    fun openDiff(file: GitFileChange) {
        openDiff(
            path = file.path,
            staged = file.staged,
            status = file.status,
        )
    }

    fun openDiff(
        path: String,
        staged: Boolean,
        status: String,
    ) {
        val projectId = mutableUiState.value.projectId ?: return
        val file = mutableUiState.value.status?.files?.firstOrNull {
            it.path == path && it.staged == staged && it.status == status
        } ?: GitFileChange(path = path, status = status, staged = staged)
        coroutineScope.launch {
            loadDiff(projectId = projectId, file = file, fullContext = false)
        }
    }

    fun loadFullContext() {
        val state = mutableUiState.value
        val projectId = state.projectId ?: return
        val file = state.selectedFile ?: return
        coroutineScope.launch {
            loadDiff(projectId = projectId, file = file, fullContext = true)
        }
    }

    private suspend fun loadDiff(
        projectId: String,
        file: GitFileChange,
        fullContext: Boolean,
    ) {
        mutableUiState.update {
            it.copy(
                selectedFile = file,
                isLoadingDiff = true,
                errorMessage = null,
            )
        }
        runCatching {
            gitRepository.loadGitDiff(
                projectId = projectId,
                path = file.path,
                staged = file.staged,
                status = file.status,
                fullContext = fullContext,
            )
        }.onSuccess { diff ->
            mutableUiState.update {
                it.copy(
                    diff = diff,
                    showFullContext = fullContext,
                    isLoadingDiff = false,
                    errorMessage = null,
                )
            }
        }.onFailure { error ->
            mutableUiState.update {
                it.copy(
                    isLoadingDiff = false,
                    errorMessage = error.message ?: "Failed to load diff.",
                )
            }
        }
    }

    companion object {
        fun factory(gitRepository: GitRepository): ViewModelProvider.Factory {
            return sectionFactory { GitStatusScreenViewModel(gitRepository = gitRepository) }
        }
    }
}

private fun NewSessionScreenState.toNewSessionOptions(): NewSessionOptions {
    return NewSessionOptions(
        provider = provider.blankToNull(),
        model = model.blankToNull(),
        permissionMode = permissionMode.blankToNull(),
        thinking = thinking.blankToNull(),
        executor = executor.blankToNull(),
    )
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
