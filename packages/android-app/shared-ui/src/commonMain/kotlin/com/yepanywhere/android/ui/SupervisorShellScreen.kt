package com.yepanywhere.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.ProjectSummary
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

@Composable
fun SupervisorShellScreen(
    state: SupervisorShellScreenState,
    projectsState: ProjectsScreenState,
    sessionsState: SessionsScreenState,
    inboxState: InboxScreenState,
    onSectionSelected: (SupervisorShellSection) -> Unit,
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ShellHeader(
                    title = state.title,
                    subtitle = state.subtitle,
                )

                SummaryStrip(snapshot = state.snapshot)

                when (state.selectedSection) {
                    SupervisorShellSection.PROJECTS -> ProjectsSection(projectsState)
                    SupervisorShellSection.SESSIONS -> SessionsSection(sessionsState)
                    SupervisorShellSection.INBOX -> InboxSection(inboxState)
                    SupervisorShellSection.ACTIVE -> ActiveSessionSection(
                        timeline = state.snapshot.timeline,
                        pendingRequests = state.snapshot.pendingRequests,
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
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
private fun ProjectsSection(state: ProjectsScreenState) {
    SectionList(
        title = state.title,
        subtitle = state.subtitle,
        items = state.projects,
    ) { project ->
        ListCard(
            title = project.name,
            subtitle = if (project.isActive) "Active relay workspace" else "Available workspace",
        )
    }
}

@Composable
private fun SessionsSection(state: SessionsScreenState) {
    SectionList(
        title = state.title,
        subtitle = state.subtitle,
        items = state.sessions,
    ) { session ->
        ListCard(
            title = session.title,
            subtitle = "${session.status.name.lowercase().replaceFirstChar(Char::titlecase)} • ${session.updatedLabel}",
            trailing = if (session.hasUnread) "Unread" else null,
        )
    }
}

@Composable
private fun InboxSection(state: InboxScreenState) {
    SectionList(
        title = state.title,
        subtitle = state.subtitle,
        items = state.items,
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
    timeline: SessionTimeline,
    pendingRequests: List<PendingInputRequest>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle(
            title = "Active session",
            subtitle = "Foreground realtime shell for session detail and approvals.",
        )

        SectionList(
            title = "Pending actions",
            subtitle = "Approval and ask-user-question requests that need immediate handling.",
            items = pendingRequests,
        ) { request ->
            ListCard(
                title = request.title,
                subtitle = request.body,
                trailing = request.kind.name.lowercase().replaceFirstChar(Char::titlecase),
            )
        }

        SectionList(
            title = "Timeline",
            subtitle = "Current session transcript placeholder for Android UI iteration.",
            items = timeline.messages,
        ) { message ->
            MessageCard(message)
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
    itemContent: @Composable (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title = title, subtitle = subtitle)

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
