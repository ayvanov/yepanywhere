package com.yepanywhere.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yepanywhere.android.core.model.AgentMapping
import com.yepanywhere.android.core.model.AgentSession
import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.InboxItemKind
import com.yepanywhere.android.core.model.GlobalSessionFilters
import com.yepanywhere.android.core.model.GlobalSessionStats
import com.yepanywhere.android.core.model.MessageContentBlock
import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.PendingSessionMessage
import com.yepanywhere.android.core.model.ProcessModelOption
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.SessionAttachment
import com.yepanywhere.android.core.model.SessionMessage
import com.yepanywhere.android.core.model.SessionMessageAuthor
import com.yepanywhere.android.core.model.SessionProcessInfo
import com.yepanywhere.android.core.model.SessionPaginationInfo
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import com.yepanywhere.android.core.model.SessionUploadProgress
import com.yepanywhere.android.core.model.SlashCommand
import com.yepanywhere.android.core.model.SupervisorShellSnapshot

enum class SupervisorShellSection(
    val label: String,
) {
    PROJECTS("Projects"),
    SESSIONS("Sessions"),
    AGENTS("Agents"),
    INBOX("Inbox"),
    SETTINGS("Settings"),
    ACTIVE("Session"),
    NEW_SESSION("New"),
    FILE("File"),
    GIT_STATUS("Git"),
    DEVICES("Devices"),
    ACTIVITY("Activity"),
    ;

    companion object {
        val topLevelEntries = listOf(PROJECTS, SESSIONS, AGENTS, INBOX, SETTINGS)
    }
}

data class SupervisorShellScreenState(
    val title: String,
    val subtitle: String,
    val snapshot: SupervisorShellSnapshot,
    val selectedSection: SupervisorShellSection,
    val selectedProjectId: String? = null,
    val selectedSessionId: String? = null,
    val selectedFilePath: String? = null,
    val selectedDeviceId: String? = null,
)

data class ProjectsScreenState(
    val title: String,
    val subtitle: String,
    val projects: List<ProjectSummary>,
)

data class SessionsScreenState(
    val title: String,
    val subtitle: String,
    val sessions: List<SessionSummary>,
    val filters: GlobalSessionFilters = GlobalSessionFilters(),
    val stats: GlobalSessionStats = GlobalSessionStats(),
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val selectedSessionIds: Set<String> = emptySet(),
)

data class InboxScreenState(
    val title: String,
    val subtitle: String,
    val items: List<InboxItem>,
)

data class AgentsScreenState(
    val title: String = "Agents",
    val subtitle: String = "Active, idle, and recently stopped agents across projects.",
    val activeAgents: List<SessionProcessInfo> = emptyList(),
    val idleAgents: List<SessionProcessInfo> = emptyList(),
    val terminatedAgents: List<SessionProcessInfo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class ActiveSessionScreenState(
    val title: String,
    val subtitle: String,
    val timeline: SessionTimeline,
    val pendingRequests: List<PendingInputRequest>,
    val session: SessionSummary? = null,
    val ownership: String? = null,
    val processId: String? = null,
    val processState: String? = null,
    val permissionMode: String? = null,
    val modeVersion: Int? = null,
    val model: String? = null,
    val slashCommands: List<SlashCommand> = emptyList(),
    val pagination: SessionPaginationInfo? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val draft: String = "",
    val attachments: List<SessionAttachment> = emptyList(),
    val uploadProgress: List<SessionUploadProgress> = emptyList(),
    val pendingMessages: List<PendingSessionMessage> = emptyList(),
    val deferredMessages: List<PendingSessionMessage> = emptyList(),
    val isHeld: Boolean = false,
    val isSubmittingInput: Boolean = false,
    val inputErrorMessage: String? = null,
    val processInfo: SessionProcessInfo? = null,
    val processModels: List<ProcessModelOption> = emptyList(),
    val isLoadingProcessInfo: Boolean = false,
    val isSwitchingModel: Boolean = false,
    val processControlErrorMessage: String? = null,
    val agentMappings: List<AgentMapping> = emptyList(),
    val selectedAgentId: String? = null,
    val selectedAgentSession: AgentSession? = null,
    val isLoadingAgentSession: Boolean = false,
    val agentErrorMessage: String? = null,
)

data class NewSessionScreenState(
    val title: String = "New session",
    val subtitle: String = "Start a session with provider, model, permission, thinking, and executor settings.",
    val projects: List<ProjectSummary> = emptyList(),
    val projectId: String? = null,
    val provider: String = "",
    val model: String = "",
    val permissionMode: String = "",
    val thinking: String = "",
    val executor: String = "",
    val prompt: String = "",
    val providerOptions: List<String> = listOf("claude", "codex", "gemini", "opencode"),
    val permissionModeOptions: List<String> = listOf("default", "acceptEdits", "bypassPermissions", "plan"),
    val thinkingOptions: List<String> = listOf("disabled", "enabled"),
    val executorOptions: List<String> = emptyList(),
    val isSubmitting: Boolean = false,
    val startedSessionId: String? = null,
    val errorMessage: String? = null,
)

data class NewSessionCallbacks(
    val onProjectChanged: (String) -> Unit = {},
    val onProviderChanged: (String) -> Unit = {},
    val onModelChanged: (String) -> Unit = {},
    val onPermissionModeChanged: (String) -> Unit = {},
    val onThinkingChanged: (String) -> Unit = {},
    val onExecutorChanged: (String) -> Unit = {},
    val onPromptChanged: (String) -> Unit = {},
    val onStartDirect: () -> Unit = {},
    val onStartTwoPhase: () -> Unit = {},
    val onSaveDefaults: () -> Unit = {},
)

data class ActiveSessionCallbacks(
    val onSendReply: (String) -> Unit,
    val onApproveRequest: (String) -> Unit,
    val onApproveAcceptEditsRequest: (String) -> Unit = {},
    val onDenyRequest: (requestId: String, feedback: String?) -> Unit,
    val onAnswerQuestion: (requestId: String, answer: String) -> Unit,
    val onRefresh: () -> Unit = {},
    val onRefreshMetadata: () -> Unit = {},
    val onDraftChanged: (String) -> Unit = {},
    val onQueueDeferredReply: (String) -> Unit = {},
    val onCancelDeferredMessage: (String) -> Unit = {},
    val onAttachClicked: () -> Unit = {},
    val onRemoveAttachment: (String) -> Unit = {},
    val onHoldChanged: (Boolean) -> Unit = {},
    val onStopSession: () -> Unit = {},
    val onLoadProcessInfo: () -> Unit = {},
    val onLoadProcessModels: () -> Unit = {},
    val onSwitchProcessModel: (String) -> Unit = {},
    val onLoadAgentMappings: () -> Unit = {},
    val onLoadAgentSession: (String) -> Unit = {},
)

@Composable
fun SupervisorShellScreen(
    state: SupervisorShellScreenState,
    projectsState: ProjectsScreenState,
    sessionsState: SessionsScreenState,
    agentsState: AgentsScreenState = AgentsScreenState(),
    inboxState: InboxScreenState,
    activeSessionState: ActiveSessionScreenState,
    newSessionState: NewSessionScreenState = NewSessionScreenState(),
    activeSessionCallbacks: ActiveSessionCallbacks,
    newSessionCallbacks: NewSessionCallbacks = NewSessionCallbacks(),
    onSectionSelected: (SupervisorShellSection) -> Unit,
    onProjectSelected: (String) -> Unit,
    onSessionSelected: (projectId: String, sessionId: String) -> Unit = { _, _ -> },
    onSessionFiltersApplied: (GlobalSessionFilters) -> Unit = {},
    onLoadMoreSessions: () -> Unit = {},
    onSessionSelectionToggled: (String) -> Unit = {},
    onBulkArchiveSessions: () -> Unit = {},
    onBulkStarSessions: () -> Unit = {},
    onBulkMarkSessionsRead: () -> Unit = {},
    onBulkMarkSessionsUnread: () -> Unit = {},
    onLogout: () -> Unit,
) {
    AndroidAppTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    SupervisorShellSection.topLevelEntries.forEach { section ->
                        NavigationBarItem(
                            selected = state.selectedSection == section,
                            onClick = { onSectionSelected(section) },
                            icon = {
                                Text(
                                    text = when (section) {
                                        SupervisorShellSection.PROJECTS -> "${state.snapshot.projects.size}"
                                        SupervisorShellSection.SESSIONS -> "${state.snapshot.sessions.size}"
                                        SupervisorShellSection.AGENTS -> "${agentsState.activeAgents.size}"
                                        SupervisorShellSection.INBOX -> "${state.snapshot.unreadInboxCount}"
                                        SupervisorShellSection.SETTINGS -> "0"
                                        else -> "0"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            label = { Text(section.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("supervisor-shell-scroll"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.selectedSection != SupervisorShellSection.SESSIONS) {
                    ShellHeader(
                        title = state.title,
                        subtitle = state.subtitle,
                        onLogout = onLogout,
                    )

                    SummaryStrip(snapshot = state.snapshot)
                }

                when (state.selectedSection) {
                    SupervisorShellSection.PROJECTS -> ProjectsSection(
                        state = projectsState,
                        connectionStatus = state.snapshot.connectionStatus,
                        onProjectSelected = onProjectSelected,
                    )
                    SupervisorShellSection.SESSIONS -> SessionsSection(
                        state = sessionsState,
                        connectionStatus = state.snapshot.connectionStatus,
                        selectedProjectId = state.selectedProjectId,
                        projects = projectsState.projects,
                        onProjectSelected = onProjectSelected,
                        onSessionSelected = onSessionSelected,
                        onSessionFiltersApplied = onSessionFiltersApplied,
                        onLoadMoreSessions = onLoadMoreSessions,
                        onSessionSelectionToggled = onSessionSelectionToggled,
                        onBulkArchiveSessions = onBulkArchiveSessions,
                        onBulkStarSessions = onBulkStarSessions,
                        onBulkMarkSessionsRead = onBulkMarkSessionsRead,
                        onBulkMarkSessionsUnread = onBulkMarkSessionsUnread,
                    )
                    SupervisorShellSection.AGENTS -> AgentsSection(
                        state = agentsState,
                        connectionStatus = state.snapshot.connectionStatus,
                        onSessionSelected = onSessionSelected,
                    )
                    SupervisorShellSection.INBOX -> InboxSection(
                        state = inboxState,
                        connectionStatus = state.snapshot.connectionStatus,
                    )
                    SupervisorShellSection.SETTINGS -> PlaceholderSection(
                        title = "Settings",
                        subtitle = "Android settings categories will mirror the web settings surface.",
                    )
                    SupervisorShellSection.ACTIVE -> ActiveSessionSection(
                        state = activeSessionState,
                        callbacks = activeSessionCallbacks,
                    )
                    SupervisorShellSection.NEW_SESSION -> NewSessionSection(
                        state = newSessionState,
                        callbacks = newSessionCallbacks,
                    )
                    SupervisorShellSection.FILE -> PlaceholderSection(
                        title = state.selectedFilePath ?: "File",
                        subtitle = "File content and syntax highlighting route.",
                    )
                    SupervisorShellSection.GIT_STATUS -> PlaceholderSection(
                        title = "Git status",
                        subtitle = "Changed files and diff viewer route.",
                    )
                    SupervisorShellSection.DEVICES -> PlaceholderSection(
                        title = "Devices",
                        subtitle = state.selectedDeviceId ?: "Device and emulator bridge route.",
                    )
                    SupervisorShellSection.ACTIVITY -> PlaceholderSection(
                        title = "Activity",
                        subtitle = "Recent sessions and cross-project activity route.",
                    )
                }
            }
        }
    }
}

@Composable
fun AndroidAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun ShellHeader(
    title: String,
    subtitle: String,
    onLogout: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Button(
                onClick = onLogout,
                modifier = Modifier.testTag("shell-logout"),
            ) {
                Text("Log out")
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryStrip(snapshot: SupervisorShellSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryChip(
            modifier = Modifier.weight(1f),
            label = "Connection",
            value = snapshot.connectionStatus.name.lowercase().replaceFirstChar(Char::titlecase),
        )
        SummaryChip(
            modifier = Modifier.weight(1f),
            label = "Attention",
            value = "${snapshot.sessionsNeedingAttentionCount}",
        )
        SummaryChip(
            modifier = Modifier.weight(1f),
            label = "Unread",
            value = "${snapshot.unreadInboxCount}",
        )
    }
}

@Composable
private fun SummaryChip(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ProjectsSection(
    state: ProjectsScreenState,
    connectionStatus: RelayConnectionStatus,
    onProjectSelected: (String) -> Unit,
) {
    val emptyState = listSectionEmptyState(
        connectionStatus = connectionStatus,
        singularName = "project",
    )
    SectionList(
        title = state.title,
        subtitle = state.subtitle,
        items = state.projects,
        emptyState = emptyState,
    ) { project ->
        ListCard(
            title = project.name,
            subtitle = project.projectStatusText(),
            onClick = { onProjectSelected(project.id) },
        )
    }
}

private fun ProjectSummary.projectStatusText(): String {
    val parts = buildList {
        if (activeCount > 0) add("Active $activeCount")
        if (thinkingCount > 0) add("Thinking $thinkingCount")
        if (needsAttentionCount > 0) add("Attention $needsAttentionCount")
    }
    if (parts.isNotEmpty()) {
        return parts.joinToString(" • ")
    }
    return if (isActive) "Active relay workspace" else "Available workspace"
}

@Composable
private fun AgentsSection(
    state: AgentsScreenState,
    connectionStatus: RelayConnectionStatus,
    onSessionSelected: (projectId: String, sessionId: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title = state.title, subtitle = state.subtitle)
        if (state.isLoading) {
            Text(
                text = "Loading agents...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.errorMessage?.let { errorMessage ->
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        AgentGroup(
            title = "Active agents",
            agents = state.activeAgents,
            emptyState = listSectionEmptyState(connectionStatus, "active agent"),
            onSessionSelected = onSessionSelected,
        )
        AgentGroup(
            title = "Idle agents",
            agents = state.idleAgents,
            emptyState = null,
            onSessionSelected = onSessionSelected,
        )
        AgentGroup(
            title = "Stopped agents",
            agents = state.terminatedAgents,
            emptyState = null,
            onSessionSelected = onSessionSelected,
        )
    }
}

@Composable
private fun AgentGroup(
    title: String,
    agents: List<SessionProcessInfo>,
    emptyState: SectionEmptyState?,
    onSessionSelected: (projectId: String, sessionId: String) -> Unit,
) {
    if (agents.isEmpty() && emptyState == null) {
        return
    }
    SectionList(
        title = title,
        subtitle = "${agents.size} agent${if (agents.size == 1) "" else "s"}",
        items = agents,
        emptyState = emptyState,
    ) { process ->
        ListCard(
            title = process.sessionTitle ?: process.id,
            subtitle = listOfNotNull(
                process.projectName,
                process.state.takeIf { it.isNotBlank() },
                process.provider,
                process.model,
            ).joinToString(" • "),
            trailing = process.queueDepth.takeIf { it > 0 }?.let { "Queue $it" },
            onClick = { onSessionSelected(process.projectId, process.sessionId) },
        )
    }
}

@Composable
private fun SessionsSection(
    state: SessionsScreenState,
    connectionStatus: RelayConnectionStatus,
    selectedProjectId: String?,
    projects: List<ProjectSummary>,
    onProjectSelected: (String) -> Unit,
    onSessionSelected: (projectId: String, sessionId: String) -> Unit,
    onSessionFiltersApplied: (GlobalSessionFilters) -> Unit,
    onLoadMoreSessions: () -> Unit,
    onSessionSelectionToggled: (String) -> Unit,
    onBulkArchiveSessions: () -> Unit,
    onBulkStarSessions: () -> Unit,
    onBulkMarkSessionsRead: () -> Unit,
    onBulkMarkSessionsUnread: () -> Unit,
) {
    val emptyState = listSectionEmptyState(
        connectionStatus = connectionStatus,
        singularName = "session",
    )
    val sessions = if (selectedProjectId == null) {
        state.sessions
    } else {
        state.sessions.filter { session -> session.projectId == selectedProjectId }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SessionsProjectSelector(
            projects = projects,
            selectedProjectId = selectedProjectId,
            onProjectSelected = { projectId ->
                onProjectSelected(projectId)
                onSessionFiltersApplied(state.filters.copy(project = projectId))
            },
        )
        SessionsFilterPanel(
            filters = state.filters,
            stats = state.stats,
            isLoading = state.isLoading,
            onApply = onSessionFiltersApplied,
        )
        if (state.selectedSessionIds.isNotEmpty()) {
            SessionsBulkActions(
                selectedCount = state.selectedSessionIds.size,
                onArchive = onBulkArchiveSessions,
                onStar = onBulkStarSessions,
                onMarkRead = onBulkMarkSessionsRead,
                onMarkUnread = onBulkMarkSessionsUnread,
            )
        }
        SectionList(
            title = state.title,
            subtitle = state.subtitle,
            items = sessions,
            emptyState = emptyState,
        ) { session ->
            ListCard(
                title = session.title,
                subtitle = session.sessionSubtitle(),
                trailing = session.sessionTrailing(state.selectedSessionIds),
                onClick = {
                    if (state.selectedSessionIds.isEmpty()) {
                        onSessionSelected(session.projectId, session.id)
                    } else {
                        onSessionSelectionToggled(session.id)
                    }
                },
                onLongClick = { onSessionSelectionToggled(session.id) },
            )
        }
        if (state.hasMore) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sessions-load-more"),
                enabled = !state.isLoading,
                onClick = onLoadMoreSessions,
            ) {
                Text(if (state.isLoading) "Loading..." else "Load more")
            }
        }
    }
}

private fun SessionSummary.sessionSubtitle(): String {
    val parts = buildList {
        add(status.name.lowercase().replaceFirstChar(Char::titlecase))
        provider?.let(::add)
        executor?.let(::add)
        model?.let(::add)
        add(updatedLabel)
    }
    return parts.joinToString(" • ")
}

private fun SessionSummary.sessionTrailing(selectedSessionIds: Set<String>): String? {
    return when {
        id in selectedSessionIds -> "Selected"
        isStarred && hasUnread -> "Starred unread"
        isStarred -> "Starred"
        hasUnread -> "Unread"
        isArchived -> "Archived"
        else -> null
    }
}

@Composable
private fun NewSessionSection(
    state: NewSessionScreenState,
    callbacks: NewSessionCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(
            title = state.title,
            subtitle = state.subtitle,
        )
        SelectorGroup(
            label = "Project",
            options = state.projects.map { it.id to it.name },
            selectedValue = state.projectId,
            onSelected = callbacks.onProjectChanged,
        )
        NewSessionInputCard(state = state, callbacks = callbacks)
        if (state.startedSessionId != null) {
            Text(
                text = "Started ${state.startedSessionId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.errorMessage?.let { errorMessage ->
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SelectorGroup(
    label: String,
    options: List<Pair<String, String>>,
    selectedValue: String?,
    onSelected: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (options.isEmpty()) {
                Text(
                    text = "No options",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                options.forEach { (value, labelText) ->
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelected(value) },
                    ) {
                        Text(if (value == selectedValue) "$labelText selected" else labelText)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewSessionInputCard(
    state: NewSessionScreenState,
    callbacks: NewSessionCallbacks,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("new-session-form"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = state.provider,
                onValueChange = callbacks.onProviderChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Provider") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.model,
                onValueChange = callbacks.onModelChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.permissionMode,
                    onValueChange = callbacks.onPermissionModeChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("Permission") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.thinking,
                    onValueChange = callbacks.onThinkingChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("Thinking") },
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = state.executor,
                onValueChange = callbacks.onExecutorChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Executor") },
                singleLine = true,
            )
            if (state.executorOptions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.executorOptions.take(2).forEach { executor ->
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { callbacks.onExecutorChanged(executor) },
                        ) {
                            Text(executor)
                        }
                    }
                }
            }
            OutlinedTextField(
                value = state.prompt,
                onValueChange = callbacks.onPromptChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new-session-prompt"),
                label = { Text("Prompt") },
                minLines = 4,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSubmitting,
                    onClick = callbacks.onSaveDefaults,
                ) {
                    Text("Save defaults")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSubmitting,
                    onClick = callbacks.onStartTwoPhase,
                ) {
                    Text("Create + queue")
                }
            }
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new-session-start"),
                enabled = !state.isSubmitting,
                onClick = callbacks.onStartDirect,
            ) {
                Text(if (state.isSubmitting) "Starting" else "Start")
            }
        }
    }
}

@Composable
private fun PlaceholderSection(
    title: String,
    subtitle: String,
) {
    SectionList(
        title = title,
        subtitle = subtitle,
        items = emptyList<Unit>(),
        emptyState = SectionEmptyState(
            title = "$title route",
            body = subtitle,
        ),
    ) {}
}

@Composable
private fun SessionsFilterPanel(
    filters: GlobalSessionFilters,
    stats: GlobalSessionStats,
    isLoading: Boolean,
    onApply: (GlobalSessionFilters) -> Unit,
) {
    var query by rememberSaveable(filters.query) { mutableStateOf(filters.query.orEmpty()) }
    var status by rememberSaveable(filters.status) { mutableStateOf(filters.status.orEmpty()) }
    var provider by rememberSaveable(filters.provider) { mutableStateOf(filters.provider.orEmpty()) }
    var executor by rememberSaveable(filters.executor) { mutableStateOf(filters.executor.orEmpty()) }
    var age by rememberSaveable(filters.age) { mutableStateOf(filters.age.orEmpty()) }
    var includeArchived by rememberSaveable(filters.includeArchived) { mutableStateOf(filters.includeArchived) }
    var starred by rememberSaveable(filters.starred) { mutableStateOf(filters.starred) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sessions-filter-panel"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Status") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = provider,
                    onValueChange = { provider = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Provider") },
                    singleLine = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = executor,
                    onValueChange = { executor = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Executor") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Age") },
                    singleLine = true,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { includeArchived = !includeArchived }) {
                    Text(if (includeArchived) "Archived on" else "Archived off")
                }
                Button(onClick = { starred = !starred }) {
                    Text(if (starred) "Starred on" else "Starred off")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Total ${stats.total} • Unread ${stats.unread} • Starred ${stats.starred}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    modifier = Modifier.testTag("sessions-filter-apply"),
                    enabled = !isLoading,
                    onClick = {
                        onApply(
                            filters.copy(
                                query = query.blankToNull(),
                                status = status.blankToNull(),
                                provider = provider.blankToNull(),
                                executor = executor.blankToNull(),
                                age = age.blankToNull(),
                                includeArchived = includeArchived,
                                starred = starred,
                            ),
                        )
                    },
                ) {
                    Text(if (isLoading) "Loading" else "Apply")
                }
            }
        }
    }
}

@Composable
private fun SessionsBulkActions(
    selectedCount: Int,
    onArchive: () -> Unit,
    onStar: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sessions-bulk-actions"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(modifier = Modifier.weight(1f), onClick = onArchive) { Text("Archive") }
                Button(modifier = Modifier.weight(1f), onClick = onStar) { Text("Star") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(modifier = Modifier.weight(1f), onClick = onMarkRead) { Text("Read") }
                Button(modifier = Modifier.weight(1f), onClick = onMarkUnread) { Text("Unread") }
            }
        }
    }
}

@Composable
private fun SessionsProjectSelector(
    projects: List<ProjectSummary>,
    selectedProjectId: String?,
    onProjectSelected: (String) -> Unit,
) {
    val selectedProject = projects.firstOrNull { project -> project.id == selectedProjectId }
    val alternateProjects = projects.filter { project -> project.id != selectedProjectId }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sessions-project-selector"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Project",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = selectedProject?.name ?: "All projects",
                modifier = Modifier.testTag("sessions-project-name"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )

            if (alternateProjects.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose another project",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    alternateProjects.forEach { project ->
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onProjectSelected(project.id) },
                        ) {
                            Text(project.name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxSection(
    state: InboxScreenState,
    connectionStatus: RelayConnectionStatus,
) {
    val emptyState = listSectionEmptyState(
        connectionStatus = connectionStatus,
        singularName = "inbox item",
    )
    SectionList(
        title = state.title,
        subtitle = state.subtitle,
        items = state.items,
        emptyState = emptyState,
    ) { item ->
        ListCard(
            title = item.title,
            subtitle = item.subtitle,
            trailing = if (item.isUnread) item.kind.name.lowercase().replaceFirstChar(Char::titlecase) else null,
        )
    }
}

@Composable
private fun ActiveSessionSection(
    state: ActiveSessionScreenState,
    callbacks: ActiveSessionCallbacks,
) {
    val actionsEnabled = state.timeline.connectionStatus == RelayConnectionStatus.CONNECTED

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle(
            title = state.title,
            subtitle = state.subtitle,
        )

        ActiveSessionMetadataCard(
            state = state,
            callbacks = callbacks,
        )

        if (!actionsEnabled) {
            ActiveSessionStatusBanner(connectionStatus = state.timeline.connectionStatus)
        }

        ActiveSessionInputControls(
            state = state,
            callbacks = callbacks,
            actionsEnabled = actionsEnabled,
        )

        ReplyComposer(
            state = state,
            callbacks = callbacks,
            actionsEnabled = actionsEnabled,
        )

        DeferredMessagesSection(
            messages = state.deferredMessages,
            onCancel = callbacks.onCancelDeferredMessage,
        )

        SectionList(
            title = "Pending actions",
            subtitle = "Approval and ask-user-question requests that need immediate handling.",
            items = state.pendingRequests,
            emptyState = SectionEmptyState(
                title = "No pending actions",
                body = "Requests that require approval or answers will appear here.",
            ),
        ) { request ->
            PendingRequestCard(
                request = request,
                callbacks = callbacks,
                actionsEnabled = actionsEnabled,
            )
        }

        SubagentSection(
            state = state,
            callbacks = callbacks,
        )

        SectionList(
            title = "Timeline",
            subtitle = "Current session transcript placeholder for Android UI iteration.",
            items = state.timeline.messages,
            emptyState = SectionEmptyState(
                title = "Timeline is empty",
                body = "New messages will appear here after reconnect and refresh.",
            ),
        ) { message ->
            MessageCard(message)
        }
    }
}

@Composable
private fun ActiveSessionMetadataCard(
    state: ActiveSessionScreenState,
    callbacks: ActiveSessionCallbacks,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active-session-metadata"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = state.session?.title ?: state.timeline.sessionId,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            val metadata = buildList {
                state.ownership?.let { add("Owner $it") }
                state.processState?.let { add("State $it") }
                state.permissionMode?.let { add("Mode $it") }
                state.model?.let { add("Model $it") }
            }.joinToString(" • ")
            if (metadata.isNotBlank()) {
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.pagination != null) {
                Text(
                    text = "Messages ${state.pagination.returnedMessageCount}/${state.pagination.totalMessageCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.slashCommands.isNotEmpty()) {
                Text(
                    text = state.slashCommands.joinToString(" ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !state.isRefreshing,
                    onClick = callbacks.onRefreshMetadata,
                ) {
                    Text("Metadata")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !state.isRefreshing,
                    onClick = callbacks.onRefresh,
                ) {
                    Text(if (state.isRefreshing) "Refreshing" else "Refresh")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("process-info-load"),
                    enabled = !state.isLoadingProcessInfo,
                    onClick = callbacks.onLoadProcessInfo,
                ) {
                    Text(if (state.isLoadingProcessInfo) "Loading" else "Process")
                }
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("process-models-load"),
                    enabled = state.processId != null && !state.isSwitchingModel,
                    onClick = callbacks.onLoadProcessModels,
                ) {
                    Text("Models")
                }
            }
            state.processControlErrorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            ProcessInfoSummary(state.processInfo)
            ProcessModelList(
                models = state.processModels,
                currentModel = state.model,
                switching = state.isSwitchingModel,
                onSwitchProcessModel = callbacks.onSwitchProcessModel,
            )
        }
    }
}

@Composable
private fun ProcessInfoSummary(processInfo: SessionProcessInfo?) {
    if (processInfo == null) {
        return
    }
    Column(
        modifier = Modifier.testTag("process-info-summary"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Process ${processInfo.id}", style = MaterialTheme.typography.labelLarge)
        Text(
            text = buildList {
                add("State ${processInfo.state}")
                processInfo.provider?.let { add("Provider $it") }
                processInfo.model?.let { add("Model $it") }
                processInfo.thinking?.let { add("Thinking $it") }
                processInfo.executor?.let { add("Executor $it") }
                processInfo.pid?.let { add("PID $it") }
            }.joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProcessModelList(
    models: List<ProcessModelOption>,
    currentModel: String?,
    switching: Boolean,
    onSwitchProcessModel: (String) -> Unit,
) {
    if (models.isEmpty()) {
        return
    }
    Column(
        modifier = Modifier.testTag("process-model-list"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        models.forEach { model ->
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("process-model-${model.id}"),
                enabled = !switching && model.id != currentModel,
                onClick = { onSwitchProcessModel(model.id) },
            ) {
                Text(
                    text = if (model.id == currentModel) "${model.name} (current)" else model.name,
                )
            }
        }
    }
}

@Composable
private fun SubagentSection(
    state: ActiveSessionScreenState,
    callbacks: ActiveSessionCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Subagents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Task tool sessions linked from this transcript.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                modifier = Modifier.testTag("agent-mappings-load"),
                enabled = !state.isLoadingAgentSession,
                onClick = callbacks.onLoadAgentMappings,
            ) {
                Text(if (state.isLoadingAgentSession) "Loading" else "Load")
            }
        }

        state.agentErrorMessage?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.agentMappings.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.agentMappings.forEach { mapping ->
                    ListCard(
                        title = mapping.agentId,
                        subtitle = "Tool use ${mapping.toolUseId}",
                        trailing = if (mapping.agentId == state.selectedAgentId) "Open" else null,
                        onClick = { callbacks.onLoadAgentSession(mapping.agentId) },
                    )
                    Button(
                        modifier = Modifier.testTag("agent-session-${mapping.agentId}"),
                        enabled = !state.isLoadingAgentSession,
                        onClick = { callbacks.onLoadAgentSession(mapping.agentId) },
                    ) {
                        Text("Open ${mapping.agentId}")
                    }
                }
            }
        }

        state.selectedAgentSession?.let { agentSession ->
            SectionList(
                title = "Agent ${state.selectedAgentId ?: ""}".trim(),
                subtitle = agentSession.status ?: "Status unavailable",
                items = agentSession.messages,
                emptyState = SectionEmptyState(
                    title = "Agent transcript is empty",
                    body = "This subagent has no messages available yet.",
                ),
            ) { message ->
                MessageCard(message)
            }
        }
    }
}

@Composable
private fun ActiveSessionStatusBanner(connectionStatus: RelayConnectionStatus) {
    val title = when (connectionStatus) {
        RelayConnectionStatus.DISCONNECTED -> "Offline snapshot"
        RelayConnectionStatus.CONNECTING -> "Reconnecting"
        RelayConnectionStatus.SYNCING -> "Sync in progress"
        RelayConnectionStatus.CONNECTED -> return
    }
    val body = when (connectionStatus) {
        RelayConnectionStatus.DISCONNECTED -> "Actions stay read-only until relay connectivity returns."
        RelayConnectionStatus.CONNECTING -> "Cached content stays visible while the relay session reconnects."
        RelayConnectionStatus.SYNCING -> "Cached content is visible while the active session catches up."
        RelayConnectionStatus.CONNECTED -> return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active-session-status-banner"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReplyComposer(
    state: ActiveSessionScreenState,
    callbacks: ActiveSessionCallbacks,
    actionsEnabled: Boolean,
) {
    var replyDraft by rememberSaveable(state.timeline.sessionId) { mutableStateOf(state.draft) }
    val queueAvailable = state.processState != null && state.processState != "idle"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Reply",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Send a follow-up message to the active session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = replyDraft,
                onValueChange = { draft ->
                    replyDraft = draft
                    callbacks.onDraftChanged(draft)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reply-input"),
                label = { Text("Message") },
                minLines = 3,
            )
            DraftAttachmentRows(
                attachments = state.attachments,
                uploadProgress = state.uploadProgress,
                onAttachClicked = callbacks.onAttachClicked,
                onRemoveAttachment = callbacks.onRemoveAttachment,
            )
            state.inputErrorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Button(
                    modifier = Modifier.testTag("reply-queue"),
                    enabled = actionsEnabled && queueAvailable && replyDraft.isNotBlank() && !state.isSubmittingInput,
                    onClick = {
                        callbacks.onQueueDeferredReply(replyDraft.trim())
                        replyDraft = ""
                    },
                ) {
                    Text("Queue")
                }
                Button(
                    modifier = Modifier.testTag("reply-send"),
                    enabled = actionsEnabled && replyDraft.isNotBlank() && !state.isSubmittingInput,
                    onClick = {
                        callbacks.onSendReply(replyDraft.trim())
                        replyDraft = ""
                    },
                ) {
                    Text("Send reply")
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionInputControls(
    state: ActiveSessionScreenState,
    callbacks: ActiveSessionCallbacks,
    actionsEnabled: Boolean,
) {
    val canControlProcess = state.ownership == "self" && state.processId != null
    if (!canControlProcess && state.processState == null) {
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            modifier = Modifier
                .weight(1f)
                .testTag("session-hold"),
            enabled = actionsEnabled,
            onClick = { callbacks.onHoldChanged(!state.isHeld) },
        ) {
            Text(if (state.isHeld) "Resume" else "Hold")
        }
        Button(
            modifier = Modifier
                .weight(1f)
                .testTag("session-stop"),
            enabled = actionsEnabled && canControlProcess,
            onClick = callbacks.onStopSession,
        ) {
            Text("Stop")
        }
    }
}

@Composable
private fun DraftAttachmentRows(
    attachments: List<SessionAttachment>,
    uploadProgress: List<SessionUploadProgress>,
    onAttachClicked: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                modifier = Modifier.testTag("reply-attach"),
                onClick = onAttachClicked,
            ) {
                Text("Attach")
            }
        }
        attachments.forEach { attachment ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = attachment.name,
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    modifier = Modifier.testTag("attachment-remove-${attachment.id}"),
                    onClick = { onRemoveAttachment(attachment.id) },
                ) {
                    Text("Remove")
                }
            }
        }
        uploadProgress.forEach { progress ->
            Text(
                text = "${progress.fileName} ${progress.percent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeferredMessagesSection(
    messages: List<PendingSessionMessage>,
    onCancel: (String) -> Unit,
) {
    if (messages.isEmpty()) {
        return
    }
    SectionList(
        title = "Queued messages",
        subtitle = "Deferred messages will send after the current turn completes.",
        items = messages,
    ) { message ->
        ListCard(
            title = message.text,
            subtitle = if (message.deferred) "Deferred" else "Pending",
            trailing = "Cancel",
            onClick = { onCancel(message.tempId) },
        )
        Button(
            modifier = Modifier.testTag("deferred-cancel-${message.tempId}"),
            onClick = { onCancel(message.tempId) },
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun PendingRequestCard(
    request: PendingInputRequest,
    callbacks: ActiveSessionCallbacks,
    actionsEnabled: Boolean,
) {
    var responseDraft by rememberSaveable(request.id) { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = request.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = request.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = request.kind.name.lowercase().replaceFirstChar(Char::titlecase),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            when (request.kind) {
                InboxItemKind.APPROVAL -> {
                    OutlinedTextField(
                        value = responseDraft,
                        onValueChange = { responseDraft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pending-request-input-${request.id}"),
                        label = { Text("Optional denial note") },
                        minLines = 2,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    ) {
                        Button(
                            modifier = Modifier.testTag("pending-request-deny-${request.id}"),
                            enabled = actionsEnabled,
                            onClick = {
                                callbacks.onDenyRequest(
                                    request.id,
                                    responseDraft.trim().takeIf { it.isNotEmpty() },
                                )
                                responseDraft = ""
                            },
                        ) {
                            Text("Deny")
                        }
                        Button(
                            modifier = Modifier.testTag("pending-request-approve-${request.id}"),
                            enabled = actionsEnabled,
                            onClick = {
                                callbacks.onApproveRequest(request.id)
                                responseDraft = ""
                            },
                        ) {
                            Text("Approve")
                        }
                        Button(
                            modifier = Modifier.testTag("pending-request-approve-accept-edits-${request.id}"),
                            enabled = actionsEnabled,
                            onClick = {
                                callbacks.onApproveAcceptEditsRequest(request.id)
                                responseDraft = ""
                            },
                        ) {
                            Text("Accept edits")
                        }
                    }
                }

                InboxItemKind.QUESTION -> {
                    OutlinedTextField(
                        value = responseDraft,
                        onValueChange = { responseDraft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pending-request-input-${request.id}"),
                        label = { Text("Answer") },
                        minLines = 2,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            modifier = Modifier.testTag("pending-request-answer-${request.id}"),
                            enabled = actionsEnabled && responseDraft.isNotBlank(),
                            onClick = {
                                callbacks.onAnswerQuestion(
                                    request.id,
                                    responseDraft.trim(),
                                )
                                responseDraft = ""
                            },
                        ) {
                            Text("Send answer")
                        }
                    }
                }

                InboxItemKind.NOTIFICATION -> {
                    Text(
                        text = "Notification-only item. No action is required yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: SessionMessage) {
    val accent = when (message.author) {
        SessionMessageAuthor.USER -> MaterialTheme.colorScheme.primaryContainer
        SessionMessageAuthor.ASSISTANT -> MaterialTheme.colorScheme.secondaryContainer
        SessionMessageAuthor.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(accent)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = message.author.name.lowercase().replaceFirstChar(Char::titlecase),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            val blocks = message.blocks.ifEmpty { listOf(MessageContentBlock.Text(message.body)) }
            blocks.forEachIndexed { index, block ->
                MessageContentBlockView(
                    blockId = "${message.id}-$index",
                    block = block,
                )
            }
            Text(
                text = message.timestampLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageContentBlockView(
    blockId: String,
    block: MessageContentBlock,
) {
    when (block) {
        is MessageContentBlock.Text -> PlainMessageBlock(
            blockId = blockId,
            text = block.text,
        )
        is MessageContentBlock.Thinking -> LabeledMessageBlock(
            blockId = blockId,
            label = "Thinking",
            text = block.text,
        )
        is MessageContentBlock.ToolUse -> LabeledMessageBlock(
            blockId = blockId,
            label = "Tool use: ${block.name}",
            text = block.input,
        )
        is MessageContentBlock.ToolResult -> CollapsibleMessageBlock(
            blockId = blockId,
            label = if (block.isError) "Tool result error" else "Tool result",
            text = block.content,
        )
        is MessageContentBlock.FileOperation -> CollapsibleMessageBlock(
            blockId = blockId,
            label = "${block.operation.replaceFirstChar(Char::titlecase)}: ${block.path}",
            text = block.content ?: block.path,
        )
        is MessageContentBlock.WebReference -> LabeledMessageBlock(
            blockId = blockId,
            label = block.operation.replace('_', ' ').replaceFirstChar(Char::titlecase),
            text = block.title ?: block.queryOrUrl,
        )
        is MessageContentBlock.Task -> CollapsibleMessageBlock(
            blockId = blockId,
            label = "Task: ${block.title}",
            text = block.content ?: block.title,
        )
        is MessageContentBlock.TodoUpdate -> CollapsibleMessageBlock(
            blockId = blockId,
            label = "Todo update",
            text = block.summary ?: block.items.joinToString("\n"),
        )
        is MessageContentBlock.Image -> LabeledMessageBlock(
            blockId = blockId,
            label = "Image",
            text = block.alt ?: block.source,
        )
        is MessageContentBlock.Document -> LabeledMessageBlock(
            blockId = blockId,
            label = "Document",
            text = block.name,
        )
        is MessageContentBlock.Fallback -> CollapsibleMessageBlock(
            blockId = blockId,
            label = block.type.replace('_', ' ').replaceFirstChar(Char::titlecase),
            text = block.text,
        )
    }
}

@Composable
private fun PlainMessageBlock(
    blockId: String,
    text: String,
) {
    if (text.isBlank()) {
        return
    }
    Text(
        modifier = Modifier.testTag("message-block-$blockId"),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun LabeledMessageBlock(
    blockId: String,
    label: String,
    text: String,
) {
    MessageBlockContainer(blockId = blockId) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        if (text.isNotBlank()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CollapsibleMessageBlock(
    blockId: String,
    label: String,
    text: String,
) {
    val shouldCollapse = text.length > 260 || text.lines().size > 8
    var expanded by rememberSaveable(blockId) { mutableStateOf(!shouldCollapse) }
    val displayText = if (expanded || !shouldCollapse) {
        text
    } else {
        text.lines()
            .take(8)
            .joinToString("\n")
            .take(260)
            .trimEnd() + "\n..."
    }

    MessageBlockContainer(blockId = blockId) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        if (displayText.isNotBlank()) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (shouldCollapse) {
            Button(
                modifier = Modifier.testTag("message-block-toggle-$blockId"),
                onClick = { expanded = !expanded },
            ) {
                Text(if (expanded) "Collapse output" else "Expand output")
            }
        }
    }
}

@Composable
private fun MessageBlockContainer(
    blockId: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(10.dp)
            .testTag("message-block-$blockId"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun <T> SectionList(
    title: String,
    subtitle: String,
    items: List<T>,
    emptyState: SectionEmptyState? = null,
    itemContent: @Composable (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title = title, subtitle = subtitle)

        if (items.isEmpty() && emptyState != null) {
            EmptyStateCard(
                title = emptyState.title,
                body = emptyState.body,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items.forEach { item ->
                    itemContent(item)
                }
            }
        }
    }
}

private data class SectionEmptyState(
    val title: String,
    val body: String,
)

private fun listSectionEmptyState(
    connectionStatus: RelayConnectionStatus,
    singularName: String,
): SectionEmptyState {
    return when (connectionStatus) {
        RelayConnectionStatus.CONNECTING,
        RelayConnectionStatus.SYNCING,
        -> SectionEmptyState(
            title = "Loading ${singularName}s",
            body = "Waiting for relay sync to complete.",
        )

        RelayConnectionStatus.DISCONNECTED -> SectionEmptyState(
            title = "Offline snapshot",
            body = "No cached ${singularName}s are available yet.",
        )

        RelayConnectionStatus.CONNECTED -> SectionEmptyState(
            title = "No ${singularName}s yet",
            body = "New ${singularName}s will appear here when available.",
        )
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    body: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ListCard(
    title: String,
    subtitle: String,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (trailing != null) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
    if (onClick == null && onLongClick == null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    } else if (onLongClick != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick,
                ),
            colors = CardDefaults.cardColors(),
        ) {
            content()
        }
    } else {
        Card(
            onClick = onClick ?: {},
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(),
        ) {
            content()
        }
    }
}

private fun String.blankToNull(): String? = trim().takeIf { it.isNotEmpty() }
