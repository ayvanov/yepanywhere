package com.yepanywhere.android.data

import android.content.Context
import androidx.room.withTransaction
import com.yepanywhere.android.core.cache.SessionCacheStore
import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomSessionCacheStore private constructor(
    private val database: SessionCacheDatabase,
    private val dao: SessionCacheDao,
) : SessionCacheStore {
    override fun observeProjects(): Flow<List<ProjectSummary>> {
        return dao.observeProjects().map { entries -> entries.map(ProjectEntity::toModel) }
    }

    override suspend fun storeProjects(projects: List<ProjectSummary>) {
        database.withTransaction {
            dao.clearProjects()
            dao.upsertProjects(projects.mapIndexed { index, project -> project.toEntity(index) })
        }
    }

    override fun observeSessions(projectId: String?): Flow<List<SessionSummary>> {
        return dao.observeSessions(projectId).map { entries -> entries.map(SessionEntity::toModel) }
    }

    override suspend fun storeSessions(sessions: List<SessionSummary>) {
        database.withTransaction {
            dao.clearSessions()
            dao.upsertSessions(sessions.mapIndexed { index, session -> session.toEntity(index) })
        }
    }

    override fun observeTimeline(sessionId: String): Flow<SessionTimeline?> {
        return combine(
            dao.observeTimeline(sessionId),
            dao.observeTimelineMessages(sessionId),
        ) { timeline, messages ->
            timeline?.toModel(messages.map(TimelineMessageEntity::toModel))
        }
    }

    override suspend fun storeTimeline(timeline: SessionTimeline) {
        database.withTransaction {
            dao.upsertTimeline(timeline.toEntity())
            dao.clearTimelineMessages(timeline.sessionId)
            dao.upsertTimelineMessages(
                timeline.messages.mapIndexed { index, message ->
                    message.toEntity(
                        sessionId = timeline.sessionId,
                        sortIndex = index,
                    )
                },
            )
        }
    }

    override fun observeInboxItems(): Flow<List<InboxItem>> {
        return dao.observeInboxItems().map { entries -> entries.map(InboxItemEntity::toModel) }
    }

    override suspend fun storeInboxItems(items: List<InboxItem>) {
        database.withTransaction {
            dao.clearInboxItems()
            dao.upsertInboxItems(items.mapIndexed { index, item -> item.toEntity(index) })
        }
    }

    override fun observePendingRequests(): Flow<List<PendingInputRequest>> {
        return dao.observePendingRequests().map { entries -> entries.map(PendingRequestEntity::toModel) }
    }

    override suspend fun storePendingRequests(items: List<PendingInputRequest>) {
        database.withTransaction {
            dao.clearPendingRequests()
            dao.upsertPendingRequests(items.mapIndexed { index, item -> item.toEntity(index) })
        }
    }

    companion object {
        fun create(context: Context): RoomSessionCacheStore {
            val database = SessionCacheDatabase.open(context)
            return RoomSessionCacheStore(
                database = database,
                dao = database.sessionCacheDao(),
            )
        }

        internal fun fromDatabase(database: SessionCacheDatabase): RoomSessionCacheStore {
            return RoomSessionCacheStore(
                database = database,
                dao = database.sessionCacheDao(),
            )
        }
    }
}
