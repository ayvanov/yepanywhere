package com.yepanywhere.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface SessionCacheDao {
    @Query("SELECT * FROM projects ORDER BY sortIndex ASC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("DELETE FROM projects")
    suspend fun clearProjects()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProjects(projects: List<ProjectEntity>)

    @Query("SELECT * FROM sessions WHERE (:projectId IS NULL OR projectId = :projectId) ORDER BY sortIndex ASC")
    fun observeSessions(projectId: String?): Flow<List<SessionEntity>>

    @Query("DELETE FROM sessions")
    suspend fun clearSessions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<SessionEntity>)

    @Query("SELECT * FROM timelines WHERE sessionId = :sessionId LIMIT 1")
    fun observeTimeline(sessionId: String): Flow<TimelineEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimeline(timeline: TimelineEntity)

    @Query("SELECT * FROM timeline_messages WHERE sessionId = :sessionId ORDER BY sortIndex ASC")
    fun observeTimelineMessages(sessionId: String): Flow<List<TimelineMessageEntity>>

    @Query("DELETE FROM timeline_messages WHERE sessionId = :sessionId")
    suspend fun clearTimelineMessages(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimelineMessages(messages: List<TimelineMessageEntity>)

    @Query("SELECT * FROM inbox_items ORDER BY sortIndex ASC")
    fun observeInboxItems(): Flow<List<InboxItemEntity>>

    @Query("DELETE FROM inbox_items")
    suspend fun clearInboxItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInboxItems(items: List<InboxItemEntity>)

    @Query("SELECT * FROM pending_requests ORDER BY sortIndex ASC")
    fun observePendingRequests(): Flow<List<PendingRequestEntity>>

    @Query("DELETE FROM pending_requests")
    suspend fun clearPendingRequests()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingRequests(items: List<PendingRequestEntity>)
}
