package com.yepanywhere.android.data

import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.InboxItemKind
import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.SessionMessage
import com.yepanywhere.android.core.model.SessionMessageAuthor
import com.yepanywhere.android.core.model.SessionStatus
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline

internal fun ProjectSummary.toEntity(sortIndex: Int): ProjectEntity {
    return ProjectEntity(
        id = id,
        name = name,
        isActive = isActive,
        sortIndex = sortIndex,
    )
}

internal fun ProjectEntity.toModel(): ProjectSummary {
    return ProjectSummary(
        id = id,
        name = name,
        isActive = isActive,
    )
}

internal fun SessionSummary.toEntity(sortIndex: Int): SessionEntity {
    return SessionEntity(
        id = id,
        projectId = projectId,
        title = title,
        status = status.name,
        updatedLabel = updatedLabel,
        hasUnread = hasUnread,
        sortIndex = sortIndex,
    )
}

internal fun SessionEntity.toModel(): SessionSummary {
    return SessionSummary(
        id = id,
        projectId = projectId,
        title = title,
        status = enumOrDefault(status, SessionStatus.IDLE),
        updatedLabel = updatedLabel,
        hasUnread = hasUnread,
    )
}

internal fun SessionTimeline.toEntity(): TimelineEntity {
    return TimelineEntity(
        sessionId = sessionId,
        connectionStatus = connectionStatus.name,
    )
}

internal fun SessionMessage.toEntity(
    sessionId: String,
    sortIndex: Int,
): TimelineMessageEntity {
    return TimelineMessageEntity(
        sessionId = sessionId,
        messageId = id,
        author = author.name,
        body = body,
        timestampLabel = timestampLabel,
        sortIndex = sortIndex,
    )
}

internal fun TimelineMessageEntity.toModel(): SessionMessage {
    return SessionMessage(
        id = messageId,
        author = enumOrDefault(author, SessionMessageAuthor.SYSTEM),
        body = body,
        timestampLabel = timestampLabel,
    )
}

internal fun TimelineEntity.toModel(messages: List<SessionMessage>): SessionTimeline {
    return SessionTimeline(
        sessionId = sessionId,
        connectionStatus = enumOrDefault(connectionStatus, RelayConnectionStatus.DISCONNECTED),
        messages = messages,
    )
}

internal fun InboxItem.toEntity(sortIndex: Int): InboxItemEntity {
    return InboxItemEntity(
        id = id,
        projectId = projectId,
        sessionId = sessionId,
        title = title,
        subtitle = subtitle,
        kind = kind.name,
        isUnread = isUnread,
        sortIndex = sortIndex,
    )
}

internal fun InboxItemEntity.toModel(): InboxItem {
    return InboxItem(
        id = id,
        projectId = projectId,
        sessionId = sessionId,
        title = title,
        subtitle = subtitle,
        kind = enumOrDefault(kind, InboxItemKind.NOTIFICATION),
        isUnread = isUnread,
    )
}

internal fun PendingInputRequest.toEntity(sortIndex: Int): PendingRequestEntity {
    return PendingRequestEntity(
        id = id,
        sessionId = sessionId,
        title = title,
        body = body,
        kind = kind.name,
        sortIndex = sortIndex,
    )
}

internal fun PendingRequestEntity.toModel(): PendingInputRequest {
    return PendingInputRequest(
        id = id,
        sessionId = sessionId,
        title = title,
        body = body,
        kind = enumOrDefault(kind, InboxItemKind.QUESTION),
    )
}

private inline fun <reified T : Enum<T>> enumOrDefault(
    rawValue: String,
    fallback: T,
): T {
    return runCatching { enumValueOf<T>(rawValue) }.getOrDefault(fallback)
}
