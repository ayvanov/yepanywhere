package com.yepanywhere.android.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidApiDtosTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun decodesProjectSessionInboxMessageAndSettingsResponses() {
        val projects = json.decodeFromString<ProjectsResponseDto>(
            """
            {
              "projects": [
                {
                  "id": "project-yep",
                  "name": "Yep Anywhere",
                  "path": "/repo/yepanywhere",
                  "activeOwnedCount": 2,
                  "activeExternalCount": 1,
                  "thinkingCount": 1,
                  "needsAttentionCount": 3,
                  "latestActivityAt": "2026-04-26T10:00:00.000Z"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(3, projects.projects.single().activeCount)
        assertEquals(3, projects.projects.single().needsAttentionCount)

        val sessions = json.decodeFromString<GlobalSessionsResponseDto>(
            """
            {
              "sessions": [
                {
                  "id": "session-1",
                  "title": "Implement Android parity",
                  "createdAt": "2026-04-25T10:00:00.000Z",
                  "updatedAt": "2026-04-26T10:00:00.000Z",
                  "messageCount": 42,
                  "provider": "claude",
                  "projectId": "project-yep",
                  "projectName": "Yep Anywhere",
                  "ownership": "owned",
                  "pendingInputType": "tool-approval",
                  "activity": "waiting-input",
                  "hasUnread": true,
                  "customTitle": "Android parity",
                  "isArchived": false,
                  "isStarred": true,
                  "executor": "local"
                }
              ],
              "hasMore": false,
              "stats": {
                "totalCount": 1,
                "unreadCount": 1,
                "starredCount": 1,
                "archivedCount": 0,
                "providerCounts": { "claude": 1 },
                "executorCounts": { "local": 1 }
              },
              "projects": [{ "id": "project-yep", "name": "Yep Anywhere" }]
            }
            """.trimIndent(),
        )

        assertEquals("claude", sessions.sessions.single().provider)
        assertTrue(sessions.sessions.single().isStarred)
        assertFalse(sessions.hasMore)

        val sessionDetail = json.decodeFromString<SessionDetailResponseDto>(
            """
            {
              "session": {
                "id": "session-1",
                "projectId": "project-yep",
                "title": "Android parity",
                "createdAt": "2026-04-25T10:00:00.000Z",
                "updatedAt": "2026-04-26T10:00:00.000Z",
                "provider": "claude",
                "model": "sonnet",
                "permissionMode": "acceptEdits",
                "isArchived": false,
                "isStarred": true,
                "hasUnread": true
              },
              "messages": [
                {
                  "id": "msg-1",
                  "type": "assistant",
                  "timestamp": "2026-04-26T10:00:00.000Z",
                  "content": [
                    { "type": "text", "text": "Ready" },
                    {
                      "type": "tool_use",
                      "id": "tool-1",
                      "name": "Read",
                      "input": { "file_path": "README.md" }
                    }
                  ]
                }
              ],
              "ownership": "owned",
              "pendingInputRequest": {
                "id": "input-1",
                "type": "tool-approval",
                "prompt": "Allow Read?"
              },
              "slashCommands": [{ "name": "compact", "description": "Compact context" }],
              "pagination": {
                "hasOlderMessages": true,
                "totalMessageCount": 100,
                "returnedMessageCount": 25,
                "truncatedBeforeMessageId": "msg-old",
                "totalCompactions": 2
              }
            }
            """.trimIndent(),
        )

        assertEquals("tool_use", sessionDetail.messages.single().content[1].type)
        assertEquals("input-1", sessionDetail.pendingInputRequest?.id)
        assertEquals(2, sessionDetail.pagination?.totalCompactions)

        val inbox = json.decodeFromString<InboxResponseDto>(
            """
            {
              "needsAttention": [
                {
                  "sessionId": "session-1",
                  "projectId": "project-yep",
                  "projectName": "Yep Anywhere",
                  "sessionTitle": "Android parity",
                  "updatedAt": "2026-04-26T10:00:00.000Z",
                  "pendingInputType": "tool-approval",
                  "activity": "waiting-input",
                  "hasUnread": true
                }
              ],
              "active": [],
              "recentActivity": [],
              "unread8h": [],
              "unread24h": []
            }
            """.trimIndent(),
        )

        assertEquals("session-1", inbox.needsAttention.single().sessionId)

        val settings = json.decodeFromString<ServerSettingsResponseDto>(
            """
            {
              "settings": {
                "serviceWorkerEnabled": true,
                "persistRemoteSessionsToDisk": true,
                "remoteExecutors": ["local"],
                "allowedHosts": "*",
                "globalInstructions": "Be concise",
                "deviceBridgeEnabled": true,
                "newSessionDefaults": {
                  "provider": "claude",
                  "model": "sonnet",
                  "permissionMode": "acceptEdits",
                  "thinking": { "type": "enabled" },
                  "executor": "local"
                },
                "lifecycleWebhooksEnabled": false
              }
            }
            """.trimIndent(),
        )

        assertEquals("sonnet", settings.settings.newSessionDefaults?.model)
        assertEquals("enabled", settings.settings.newSessionDefaults?.thinking?.type)
    }
}
