package com.yepanywhere.android.core.cache

import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import kotlinx.coroutines.flow.Flow

interface SessionCacheStore {
    fun observeProjects(): Flow<List<ProjectSummary>>

    suspend fun storeProjects(projects: List<ProjectSummary>)

    fun observeSessions(projectId: String? = null): Flow<List<SessionSummary>>

    suspend fun storeSessions(sessions: List<SessionSummary>)

    fun observeTimeline(sessionId: String): Flow<SessionTimeline?>

    suspend fun storeTimeline(timeline: SessionTimeline)

    fun observeInboxItems(): Flow<List<InboxItem>>

    suspend fun storeInboxItems(items: List<InboxItem>)

    fun observePendingRequests(): Flow<List<PendingInputRequest>>

    suspend fun storePendingRequests(items: List<PendingInputRequest>)
}
