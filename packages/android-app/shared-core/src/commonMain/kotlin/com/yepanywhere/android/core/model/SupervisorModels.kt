package com.yepanywhere.android.core.model

enum class RelayConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SYNCING,
}

enum class SessionStatus {
    IDLE,
    RUNNING,
    NEEDS_ATTENTION,
}

enum class SessionMessageAuthor {
    USER,
    ASSISTANT,
    SYSTEM,
}

enum class InboxItemKind {
    APPROVAL,
    QUESTION,
    NOTIFICATION,
}

data class RelaySession(
    val username: String,
    val relayUrl: String,
    val sessionId: String? = null,
)

data class ProjectSummary(
    val id: String,
    val name: String,
    val isActive: Boolean = false,
)

data class SessionSummary(
    val id: String,
    val projectId: String,
    val title: String,
    val status: SessionStatus,
    val updatedLabel: String,
    val hasUnread: Boolean,
)

data class SessionMessage(
    val id: String,
    val author: SessionMessageAuthor,
    val body: String,
    val timestampLabel: String,
)

data class SessionTimeline(
    val sessionId: String,
    val connectionStatus: RelayConnectionStatus,
    val messages: List<SessionMessage>,
)

data class PendingInputRequest(
    val id: String,
    val sessionId: String,
    val title: String,
    val body: String,
    val kind: InboxItemKind,
)

data class InboxItem(
    val id: String,
    val projectId: String?,
    val sessionId: String?,
    val title: String,
    val subtitle: String,
    val kind: InboxItemKind,
    val isUnread: Boolean,
)

data class SupervisorShellSnapshot(
    val connectionStatus: RelayConnectionStatus,
    val projects: List<ProjectSummary>,
    val sessions: List<SessionSummary>,
    val inboxItems: List<InboxItem>,
    val timeline: SessionTimeline,
    val pendingRequests: List<PendingInputRequest>,
) {
    val unreadInboxCount: Int
        get() = inboxItems.count { it.isUnread }

    val sessionsNeedingAttentionCount: Int
        get() = sessions.count { it.status == SessionStatus.NEEDS_ATTENTION }
}
