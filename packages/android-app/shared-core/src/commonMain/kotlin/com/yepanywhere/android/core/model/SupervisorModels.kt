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
    val path: String? = null,
    val activeOwnedCount: Int = 0,
    val activeExternalCount: Int = 0,
    val thinkingCount: Int = 0,
    val needsAttentionCount: Int = 0,
    val latestActivityAt: String? = null,
    val isActive: Boolean = false,
) {
    val activeCount: Int
        get() = activeOwnedCount + activeExternalCount
}

data class SessionSummary(
    val id: String,
    val projectId: String,
    val title: String,
    val status: SessionStatus,
    val updatedLabel: String,
    val hasUnread: Boolean,
    val provider: String? = null,
    val model: String? = null,
    val ownership: String? = null,
    val activity: String? = null,
    val isArchived: Boolean = false,
    val isStarred: Boolean = false,
    val executor: String? = null,
)

data class GlobalSessionFilters(
    val project: String? = null,
    val query: String? = null,
    val status: String? = null,
    val provider: String? = null,
    val executor: String? = null,
    val age: String? = null,
    val includeArchived: Boolean = false,
    val starred: Boolean = false,
    val includeStats: Boolean = true,
)

data class GlobalSessionStats(
    val total: Int = 0,
    val unread: Int = 0,
    val starred: Int = 0,
    val archived: Int = 0,
)

data class GlobalSessionsPage(
    val sessions: List<SessionSummary>,
    val hasMore: Boolean,
    val nextAfter: String? = sessions.lastOrNull()?.id,
    val stats: GlobalSessionStats = GlobalSessionStats(total = sessions.size),
)

data class SessionMetadataUpdate(
    val title: String? = null,
    val archived: Boolean? = null,
    val starred: Boolean? = null,
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
