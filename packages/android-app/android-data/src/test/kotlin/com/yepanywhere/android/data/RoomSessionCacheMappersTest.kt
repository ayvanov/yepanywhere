package com.yepanywhere.android.data

import com.yepanywhere.android.core.model.InboxItemKind
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.SessionMessage
import com.yepanywhere.android.core.model.SessionMessageAuthor
import com.yepanywhere.android.core.model.SessionStatus
import com.yepanywhere.android.core.model.SessionSummary
import com.yepanywhere.android.core.model.SessionTimeline
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomSessionCacheMappersTest {
    @Test
    fun sessionMappingRoundTripPreservesFields() {
        val session = SessionSummary(
            id = "session-1",
            projectId = "project-1",
            title = "Session title",
            status = SessionStatus.NEEDS_ATTENTION,
            updatedLabel = "2026-04-23T12:00:00Z",
            hasUnread = true,
        )

        val mapped = session.toEntity(sortIndex = 3).toModel()

        assertEquals(session, mapped)
    }

    @Test
    fun timelineMappingRoundTripPreservesStatusAndMessages() {
        val timeline = SessionTimeline(
            sessionId = "session-1",
            connectionStatus = RelayConnectionStatus.SYNCING,
            messages = listOf(
                SessionMessage(
                    id = "msg-1",
                    author = SessionMessageAuthor.USER,
                    body = "hello",
                    timestampLabel = "10:00",
                ),
                SessionMessage(
                    id = "msg-2",
                    author = SessionMessageAuthor.ASSISTANT,
                    body = "world",
                    timestampLabel = "10:01",
                ),
            ),
        )

        val timelineEntity = timeline.toEntity()
        val messageEntities = timeline.messages.mapIndexed { index, message ->
            message.toEntity(sessionId = timeline.sessionId, sortIndex = index)
        }
        val mapped = timelineEntity.toModel(messageEntities.map(TimelineMessageEntity::toModel))

        assertEquals(timeline, mapped)
    }

    @Test
    fun unknownEnumValuesFallbackToSafeDefaults() {
        val sessionEntity = SessionEntity(
            id = "session-unknown",
            projectId = "project-1",
            title = "Unknown status",
            status = "BROKEN_STATUS",
            updatedLabel = "now",
            hasUnread = false,
            sortIndex = 0,
        )
        val pendingRequestEntity = PendingRequestEntity(
            id = "request-1",
            sessionId = "session-1",
            title = "Need answer",
            body = "Approve?",
            kind = "BROKEN_KIND",
            sortIndex = 0,
        )

        assertEquals(SessionStatus.IDLE, sessionEntity.toModel().status)
        assertEquals(InboxItemKind.QUESTION, pendingRequestEntity.toModel().kind)
    }
}
