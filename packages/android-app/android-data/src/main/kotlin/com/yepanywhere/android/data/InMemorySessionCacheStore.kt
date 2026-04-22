package com.yepanywhere.android.data

import com.yepanywhere.android.core.cache.SessionCacheStore
import com.yepanywhere.android.core.model.InboxItem
import com.yepanywhere.android.core.model.PendingInputRequest
import com.yepanywhere.android.core.model.ProjectSummary
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import com.yepanywhere.android.core.model.SupervisorShellSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemorySessionCacheStore(
    initialSnapshot: SupervisorShellSnapshot,
) : SessionCacheStore {
    private val projects = MutableStateFlow(initialSnapshot.projects)
    private val sessions = MutableStateFlow(initialSnapshot.sessions)
    private val inboxItems = MutableStateFlow(initialSnapshot.inboxItems)
    private val pendingRequests = MutableStateFlow(initialSnapshot.pendingRequests)
    private val timelines = MutableStateFlow(
        mapOf(initialSnapshot.timeline.sessionId to initialSnapshot.timeline),
    )

    override fun observeProjects(): Flow<List<ProjectSummary>> = projects

    override suspend fun storeProjects(projects: List<ProjectSummary>) {
        this.projects.value = projects
    }

    override fun observeSessions(projectId: String?): Flow<List<SessionSummary>> {
        return sessions.map { entries ->
            if (projectId == null) {
                entries
            } else {
                entries.filter { it.projectId == projectId }
            }
        }
    }

    override suspend fun storeSessions(sessions: List<SessionSummary>) {
        this.sessions.value = sessions
    }

    override fun observeTimeline(sessionId: String): Flow<SessionTimeline?> {
        return timelines.map { it[sessionId] }
    }

    override suspend fun storeTimeline(timeline: SessionTimeline) {
        timelines.update { current ->
            current + (timeline.sessionId to timeline)
        }
    }

    override fun observeInboxItems(): Flow<List<InboxItem>> = inboxItems

    override suspend fun storeInboxItems(items: List<InboxItem>) {
        inboxItems.value = items
    }

    override fun observePendingRequests(): Flow<List<PendingInputRequest>> = pendingRequests

    override suspend fun storePendingRequests(items: List<PendingInputRequest>) {
        pendingRequests.value = items
    }
}
