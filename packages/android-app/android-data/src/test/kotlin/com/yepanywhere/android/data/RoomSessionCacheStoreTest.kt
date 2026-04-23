package com.yepanywhere.android.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class RoomSessionCacheStoreTest {
    private lateinit var database: SessionCacheDatabase
    private lateinit var store: RoomSessionCacheStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        database = Room.inMemoryDatabaseBuilder(context, SessionCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomSessionCacheStore.fromDatabase(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun storeAndObserveRoundTripWorksAcrossAllCollections() = runTest {
        val projects = listOf(
            ProjectSummary(id = "project-1", name = "Yep Anywhere", isActive = true),
        )
        val sessions = listOf(
            SessionSummary(
                id = "session-1",
                projectId = "project-1",
                title = "Android",
                status = SessionStatus.RUNNING,
                updatedLabel = "now",
                hasUnread = true,
            ),
        )
        val timeline = SessionTimeline(
            sessionId = "session-1",
            connectionStatus = RelayConnectionStatus.CONNECTED,
            messages = listOf(
                SessionMessage(
                    id = "msg-1",
                    author = SessionMessageAuthor.ASSISTANT,
                    body = "from room",
                    timestampLabel = "10:00",
                ),
            ),
        )
        val inboxItems = listOf(
            InboxItem(
                id = "inbox-1",
                projectId = "project-1",
                sessionId = "session-1",
                title = "Approval required",
                subtitle = "Need action",
                kind = InboxItemKind.APPROVAL,
                isUnread = true,
            ),
        )
        val pendingRequests = listOf(
            PendingInputRequest(
                id = "request-1",
                sessionId = "session-1",
                title = "Approve command",
                body = "Run tool?",
                kind = InboxItemKind.APPROVAL,
            ),
        )

        store.storeProjects(projects)
        store.storeSessions(sessions)
        store.storeTimeline(timeline)
        store.storeInboxItems(inboxItems)
        store.storePendingRequests(pendingRequests)

        assertEquals(projects, store.observeProjects().first())
        assertEquals(sessions, store.observeSessions().first())
        assertEquals(timeline, store.observeTimeline("session-1").first())
        assertEquals(inboxItems, store.observeInboxItems().first())
        assertEquals(pendingRequests, store.observePendingRequests().first())
    }
}
