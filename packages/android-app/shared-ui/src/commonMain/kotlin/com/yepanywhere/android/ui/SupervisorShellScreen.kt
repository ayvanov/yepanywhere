package com.yepanywhere.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.InboxItemKind
import com.yepanywhere.android.core.model.GlobalSessionFilters
import com.yepanywhere.android.core.model.GlobalSessionStats
import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.SessionMessage
import com.yepanywhere.android.core.model.SessionMessageAuthor
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
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

data class ActiveSessionScreenState(
    val title: String,
    val subtitle: String,
    val timeline: SessionTimeline,
    val pendingRequests: List<PendingInputRequest>,
)

data class ActiveSessionCallbacks(
    val onSendReply: (String) -> Unit,
    val onApproveRequest: (String) -> Unit,
    val onDenyRequest: (requestId: String, feedback: String?) -> Unit,
    val onAnswerQuestion: (requestId: String, answer: String) -> Unit,
)

@Composable
fun SupervisorShellScreen(
    state: SupervisorShellScreenState,
    projectsState: ProjectsScreenState,
    sessionsState: SessionsScreenState,
    inboxState: InboxScreenState,
    activeSessionState: ActiveSessionScreenState,
    activeSessionCallbacks: ActiveSessionCallbacks,
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
                                        SupervisorShellSection.AGENTS -> "0"
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
                    SupervisorShellSection.AGENTS -> PlaceholderSection(
                        title = "Agents",
                        subtitle = "Global active agents and subagent drill-down will land here.",
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
                    SupervisorShellSection.NEW_SESSION -> PlaceholderSection(
                        title = "New session",
                        subtitle = "Project, provider, model, permission, executor, and prompt controls.",
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

        if (!actionsEnabled) {
            ActiveSessionStatusBanner(connectionStatus = state.timeline.connectionStatus)
        }

        ReplyComposer(
            onSendReply = callbacks.onSendReply,
            actionsEnabled = actionsEnabled,
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
    onSendReply: (String) -> Unit,
    actionsEnabled: Boolean,
) {
    var replyDraft by rememberSaveable { mutableStateOf("") }

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
                onValueChange = { replyDraft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reply-input"),
                label = { Text("Message") },
                minLines = 3,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    modifier = Modifier.testTag("reply-send"),
                    enabled = actionsEnabled && replyDraft.isNotBlank(),
                    onClick = {
                        onSendReply(replyDraft.trim())
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
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = message.timestampLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
