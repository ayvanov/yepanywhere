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

data class NewSessionOptions(
    val provider: String? = null,
    val model: String? = null,
    val permissionMode: String? = null,
    val thinking: String? = null,
    val executor: String? = null,
)

data class NewSessionDefaults(
    val provider: String? = null,
    val model: String? = null,
    val permissionMode: String? = null,
    val thinking: String? = null,
    val executor: String? = null,
)

data class NewSessionSettings(
    val defaults: NewSessionDefaults = NewSessionDefaults(),
    val remoteExecutors: List<String> = emptyList(),
)

data class NewSessionStartResult(
    val sessionId: String,
    val processId: String? = null,
    val permissionMode: String? = null,
    val modeVersion: Int = 0,
)

sealed interface MessageContentBlock {
    val text: String

    data class Text(
        override val text: String,
    ) : MessageContentBlock

    data class Thinking(
        override val text: String,
    ) : MessageContentBlock

    data class ToolUse(
        val name: String,
        val input: String,
        val callId: String? = null,
    ) : MessageContentBlock {
        override val text: String
            get() = input
    }

    data class ToolResult(
        val content: String,
        val toolUseId: String? = null,
        val isError: Boolean = false,
    ) : MessageContentBlock {
        override val text: String
            get() = content
    }

    data class FileOperation(
        val operation: String,
        val path: String,
        val content: String? = null,
    ) : MessageContentBlock {
        override val text: String
            get() = content ?: path
    }

    data class WebReference(
        val operation: String,
        val queryOrUrl: String,
        val title: String? = null,
    ) : MessageContentBlock {
        override val text: String
            get() = title ?: queryOrUrl
    }

    data class Task(
        val title: String,
        val content: String? = null,
    ) : MessageContentBlock {
        override val text: String
            get() = content ?: title
    }

    data class TodoUpdate(
        val items: List<String>,
        val summary: String? = null,
    ) : MessageContentBlock {
        override val text: String
            get() = summary ?: items.joinToString("\n")
    }

    data class Image(
        val source: String,
        val alt: String? = null,
    ) : MessageContentBlock {
        override val text: String
            get() = alt ?: source
    }

    data class Document(
        val name: String,
        val mimeType: String? = null,
        val url: String? = null,
    ) : MessageContentBlock {
        override val text: String
            get() = name
    }

    data class Fallback(
        val type: String,
        override val text: String,
    ) : MessageContentBlock
}

data class SessionMessage(
    val id: String,
    val author: SessionMessageAuthor,
    val body: String,
    val timestampLabel: String,
    val blocks: List<MessageContentBlock> = listOf(MessageContentBlock.Text(body)),
)

data class SessionTimeline(
    val sessionId: String,
    val connectionStatus: RelayConnectionStatus,
    val messages: List<SessionMessage>,
)

data class SlashCommand(
    val name: String,
    val description: String? = null,
)

data class SessionPaginationInfo(
    val hasOlderMessages: Boolean = false,
    val totalMessageCount: Int = 0,
    val returnedMessageCount: Int = 0,
    val truncatedBeforeMessageId: String? = null,
    val totalCompactions: Int = 0,
)

data class SessionDetailQuery(
    val afterMessageId: String? = null,
    val beforeMessageId: String? = null,
    val tailCompactions: Int? = null,
)

data class SessionDetail(
    val session: SessionSummary,
    val timeline: SessionTimeline,
    val ownership: String? = null,
    val processId: String? = null,
    val processState: String? = null,
    val permissionMode: String? = null,
    val modeVersion: Int? = null,
    val model: String? = session.model,
    val slashCommands: List<SlashCommand> = emptyList(),
    val pendingInputRequest: PendingInputRequest? = null,
    val pagination: SessionPaginationInfo? = null,
)

data class SessionAttachment(
    val id: String,
    val name: String,
    val sizeBytes: Long = 0,
    val mimeType: String? = null,
    val url: String? = null,
)

data class SessionUploadProgress(
    val fileId: String,
    val fileName: String,
    val bytesUploaded: Long,
    val totalBytes: Long,
) {
    val percent: Int
        get() = if (totalBytes <= 0) 0 else ((bytesUploaded * 100) / totalBytes).toInt().coerceIn(0, 100)
}

data class PendingSessionMessage(
    val tempId: String,
    val text: String,
    val status: String? = null,
    val deferred: Boolean = false,
    val attachments: List<SessionAttachment> = emptyList(),
)

data class SessionInputRequest(
    val message: String,
    val mode: String? = null,
    val thinking: String? = null,
    val attachments: List<SessionAttachment> = emptyList(),
    val tempId: String? = null,
    val deferred: Boolean = false,
)

data class ProcessControlResult(
    val success: Boolean,
    val supported: Boolean = true,
)

data class SessionProcessInfo(
    val id: String,
    val sessionId: String,
    val projectId: String,
    val projectName: String,
    val projectPath: String? = null,
    val sessionTitle: String? = null,
    val state: String,
    val startedAt: String? = null,
    val queueDepth: Int = 0,
    val provider: String? = null,
    val model: String? = null,
    val thinking: String? = null,
    val effort: String? = null,
    val executor: String? = null,
    val pid: Int? = null,
)

data class ProcessModelOption(
    val id: String,
    val name: String,
    val description: String? = null,
)

data class ProcessModelSwitchResult(
    val success: Boolean,
    val model: String? = null,
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
