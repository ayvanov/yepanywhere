package com.yepanywhere.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
internal data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isActive: Boolean,
    val sortIndex: Int,
)

@Entity(tableName = "sessions")
internal data class SessionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val status: String,
    val updatedLabel: String,
    val hasUnread: Boolean,
    val sortIndex: Int,
)

@Entity(tableName = "timelines")
internal data class TimelineEntity(
    @PrimaryKey val sessionId: String,
    val connectionStatus: String,
)

@Entity(
    tableName = "timeline_messages",
    primaryKeys = ["sessionId", "messageId"],
)
internal data class TimelineMessageEntity(
    val sessionId: String,
    val messageId: String,
    val author: String,
    val body: String,
    val timestampLabel: String,
    val sortIndex: Int,
)

@Entity(tableName = "inbox_items")
internal data class InboxItemEntity(
    @PrimaryKey val id: String,
    val projectId: String?,
    val sessionId: String?,
    val title: String,
    val subtitle: String,
    val kind: String,
    val tier: String,
    val isUnread: Boolean,
    val sortIndex: Int,
)

@Entity(tableName = "pending_requests")
internal data class PendingRequestEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val title: String,
    val body: String,
    val kind: String,
    val sortIndex: Int,
)
