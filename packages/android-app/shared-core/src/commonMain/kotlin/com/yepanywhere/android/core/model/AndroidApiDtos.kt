package com.yepanywhere.android.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ProjectsResponseDto(
    val projects: List<ProjectDto> = emptyList(),
)

@Serializable
data class ProjectResponseDto(
    val project: ProjectDto,
)

@Serializable
data class ProjectDto(
    val id: String,
    val name: String,
    val path: String? = null,
    val activeOwnedCount: Int = 0,
    val activeExternalCount: Int = 0,
    val thinkingCount: Int = 0,
    val needsAttentionCount: Int = 0,
    val latestActivityAt: String? = null,
    val updatedAt: String? = null,
) {
    val activeCount: Int
        get() = activeOwnedCount + activeExternalCount
}

@Serializable
data class GlobalSessionsResponseDto(
    val sessions: List<GlobalSessionItemDto> = emptyList(),
    val hasMore: Boolean = false,
    val stats: GlobalSessionStatsDto = GlobalSessionStatsDto(),
    val projects: List<ProjectOptionDto> = emptyList(),
)

@Serializable
data class GlobalSessionItemDto(
    val id: String,
    val title: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val messageCount: Int = 0,
    val provider: String,
    val projectId: String,
    val projectName: String,
    val ownership: String,
    val pendingInputType: String? = null,
    val activity: String? = null,
    val hasUnread: Boolean = false,
    val customTitle: String? = null,
    val isArchived: Boolean = false,
    val isStarred: Boolean = false,
    val executor: String? = null,
)

@Serializable
data class GlobalSessionStatsDto(
    val totalCount: Int = 0,
    val unreadCount: Int = 0,
    val starredCount: Int = 0,
    val archivedCount: Int = 0,
    val providerCounts: Map<String, Int> = emptyMap(),
    val executorCounts: Map<String, Int> = emptyMap(),
)

@Serializable
data class ProjectOptionDto(
    val id: String,
    val name: String,
)

@Serializable
data class SessionDetailResponseDto(
    val session: SessionDto,
    val messages: List<MessageDto> = emptyList(),
    val ownership: String,
    val pendingInputRequest: InputRequestDto? = null,
    val slashCommands: List<SlashCommandDto>? = null,
    val pagination: PaginationInfoDto? = null,
)

@Serializable
data class SessionMetadataResponseDto(
    val session: SessionDto,
    val ownership: String,
    val pendingInputRequest: InputRequestDto? = null,
    val slashCommands: List<SlashCommandDto>? = null,
)

@Serializable
data class SessionDto(
    val id: String,
    val projectId: String? = null,
    val title: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val ownership: String? = null,
    val processState: String? = null,
    val permissionMode: String? = null,
    val pendingInputType: String? = null,
    val activity: String? = null,
    val hasUnread: Boolean = false,
    val customTitle: String? = null,
    val isArchived: Boolean = false,
    val isStarred: Boolean = false,
    val executor: String? = null,
    val contextUsage: ContextUsageDto? = null,
)

@Serializable
data class MessageDto(
    val id: String? = null,
    val uuid: String? = null,
    val type: String,
    val timestamp: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val content: List<MessageContentBlockDto> = emptyList(),
)

@Serializable
data class MessageContentBlockDto(
    val type: String,
    val text: String? = null,
    val thinking: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: JsonElement? = null,
    val content: JsonElement? = null,
    val result: JsonElement? = null,
    val isError: Boolean? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val url: String? = null,
)

@Serializable
data class ContextUsageDto(
    val usedTokens: Int? = null,
    val maxTokens: Int? = null,
    val percentage: Double? = null,
)

@Serializable
data class InputRequestDto(
    val id: String,
    val type: String,
    val prompt: String? = null,
    val toolUseId: String? = null,
    val questions: List<InputQuestionDto> = emptyList(),
)

@Serializable
data class InputQuestionDto(
    val id: String,
    val label: String? = null,
    val type: String? = null,
    val required: Boolean = false,
)

@Serializable
data class SlashCommandDto(
    val name: String,
    val description: String? = null,
)

@Serializable
data class PaginationInfoDto(
    val hasOlderMessages: Boolean = false,
    val totalMessageCount: Int = 0,
    val returnedMessageCount: Int = 0,
    val truncatedBeforeMessageId: String? = null,
    val totalCompactions: Int = 0,
)

@Serializable
data class InboxResponseDto(
    val needsAttention: List<InboxItemDto> = emptyList(),
    val active: List<InboxItemDto> = emptyList(),
    val recentActivity: List<InboxItemDto> = emptyList(),
    val unread8h: List<InboxItemDto> = emptyList(),
    val unread24h: List<InboxItemDto> = emptyList(),
)

@Serializable
data class InboxItemDto(
    val sessionId: String,
    val projectId: String,
    val projectName: String,
    val sessionTitle: String? = null,
    val updatedAt: String,
    val pendingInputType: String? = null,
    val activity: String? = null,
    val hasUnread: Boolean = false,
)

@Serializable
data class ServerSettingsResponseDto(
    val settings: ServerSettingsDto,
)

@Serializable
data class ServerSettingsDto(
    val serviceWorkerEnabled: Boolean = false,
    val persistRemoteSessionsToDisk: Boolean = false,
    val remoteExecutors: List<String> = emptyList(),
    val chromeOsHosts: List<String> = emptyList(),
    val allowedHosts: String? = null,
    val globalInstructions: String? = null,
    val ollamaUrl: String? = null,
    val ollamaSystemPrompt: String? = null,
    val ollamaUseFullSystemPrompt: Boolean? = null,
    val deviceBridgeEnabled: Boolean? = null,
    val newSessionDefaults: NewSessionDefaultsDto? = null,
    val lifecycleWebhooksEnabled: Boolean? = null,
    val lifecycleWebhookUrl: String? = null,
    val lifecycleWebhookToken: String? = null,
    val lifecycleWebhookDryRun: Boolean? = null,
)

@Serializable
data class NewSessionDefaultsDto(
    val provider: String? = null,
    val model: String? = null,
    val permissionMode: String? = null,
    val thinking: ThinkingOptionDto? = null,
    val executor: String? = null,
)

@Serializable
data class ThinkingOptionDto(
    val type: String,
)
