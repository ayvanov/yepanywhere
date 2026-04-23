package com.yepanywhere.android.ui

import androidx.compose.foundation.background
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
    INBOX("Inbox"),
    ACTIVE("Active"),
}

data class SupervisorShellScreenState(
    val title: String,
    val subtitle: String,
    val snapshot: SupervisorShellSnapshot,
    val selectedSection: SupervisorShellSection,
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
    onLogout: () -> Unit,
) {
    AndroidAppTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    SupervisorShellSection.entries.forEach { section ->
                        NavigationBarItem(
                            selected = state.selectedSection == section,
                            onClick = { onSectionSelected(section) },
                            icon = {
                                Text(
                                    text = when (section) {
                                        SupervisorShellSection.PROJECTS -> "${state.snapshot.projects.size}"
                                        SupervisorShellSection.SESSIONS -> "${state.snapshot.sessions.size}"
                                        SupervisorShellSection.INBOX -> "${state.snapshot.unreadInboxCount}"
                                        SupervisorShellSection.ACTIVE -> "${state.snapshot.pendingRequests.size}"
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
                ShellHeader(
                    title = state.title,
                    subtitle = state.subtitle,
                    onLogout = onLogout,
                )

                SummaryStrip(snapshot = state.snapshot)

                when (state.selectedSection) {
                    SupervisorShellSection.PROJECTS -> ProjectsSection(
                        state = projectsState,
                        connectionStatus = state.snapshot.connectionStatus,
                    )
                    SupervisorShellSection.SESSIONS -> SessionsSection(
                        state = sessionsState,
                        connectionStatus = state.snapshot.connectionStatus,
                    )
                    SupervisorShellSection.INBOX -> InboxSection(
                        state = inboxState,
                        connectionStatus = state.snapshot.connectionStatus,
                    )
                    SupervisorShellSection.ACTIVE -> ActiveSessionSection(
                        state = activeSessionState,
                        callbacks = activeSessionCallbacks,
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
            subtitle = if (project.isActive) "Active relay workspace" else "Available workspace",
        )
    }
}

@Composable
private fun SessionsSection(
    state: SessionsScreenState,
    connectionStatus: RelayConnectionStatus,
) {
    val emptyState = listSectionEmptyState(
        connectionStatus = connectionStatus,
        singularName = "session",
    )
    SectionList(
        title = state.title,
        subtitle = state.subtitle,
        items = state.sessions,
        emptyState = emptyState,
    ) { session ->
        ListCard(
            title = session.title,
            subtitle = "${session.status.name.lowercase().replaceFirstChar(Char::titlecase)} • ${session.updatedLabel}",
            trailing = if (session.hasUnread) "Unread" else null,
        )
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
private fun ListCard(
    title: String,
    subtitle: String,
    trailing: String? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
}
