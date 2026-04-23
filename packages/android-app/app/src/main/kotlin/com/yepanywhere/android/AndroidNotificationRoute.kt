package com.yepanywhere.android

import android.content.Intent
import com.yepanywhere.android.ui.SupervisorShellSection
import java.net.URI

data class AndroidNotificationRoute(
    val section: SupervisorShellSection,
    val projectId: String? = null,
    val sessionId: String? = null,
    val inboxItemId: String? = null,
) {
    companion object {
        const val EXTRA_TARGET = "com.yepanywhere.android.extra.TARGET"
        const val EXTRA_PROJECT_ID = "com.yepanywhere.android.extra.PROJECT_ID"
        const val EXTRA_SESSION_ID = "com.yepanywhere.android.extra.SESSION_ID"
        const val EXTRA_INBOX_ITEM_ID = "com.yepanywhere.android.extra.INBOX_ITEM_ID"

        fun fromIntent(intent: Intent?): AndroidNotificationRoute? {
            if (intent == null) {
                return null
            }

            val routeFromExtras = fromPayload(
                target = intent.getStringExtra(EXTRA_TARGET),
                projectId = intent.getStringExtra(EXTRA_PROJECT_ID),
                sessionId = intent.getStringExtra(EXTRA_SESSION_ID),
                inboxItemId = intent.getStringExtra(EXTRA_INBOX_ITEM_ID),
            )

            return routeFromExtras ?: fromDeepLink(intent.dataString)
        }

        fun fromPayload(
            target: String?,
            projectId: String? = null,
            sessionId: String? = null,
            inboxItemId: String? = null,
        ): AndroidNotificationRoute? {
            val section = target?.let(::sectionForTarget)
                ?: sectionForMetadata(
                    projectId = projectId,
                    sessionId = sessionId,
                    inboxItemId = inboxItemId,
                )
                ?: return null

            return AndroidNotificationRoute(
                section = section,
                projectId = projectId,
                sessionId = sessionId,
                inboxItemId = inboxItemId,
            )
        }

        fun fromData(data: Map<String, String>): AndroidNotificationRoute? {
            return fromPayload(
                target = data["target"],
                projectId = data["projectId"],
                sessionId = data["sessionId"],
                inboxItemId = data["inboxItemId"] ?: data["itemId"],
            )
        }

        fun fromDeepLink(rawUri: String?): AndroidNotificationRoute? {
            if (rawUri.isNullOrBlank()) {
                return null
            }

            val uri = runCatching { URI(rawUri) }.getOrNull() ?: return null
            if (uri.scheme != "yepanywhere") {
                return null
            }
            if (uri.host != "open") {
                return null
            }

            val pathSegments = uri.path
                ?.trim('/')
                ?.split('/')
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val target = pathSegments.firstOrNull()
            val params = parseQuery(uri.rawQuery)
            val sessionId = params["sessionId"] ?: target.takeIf { it == "session" }?.let { pathSegments.getOrNull(1) }

            return fromPayload(
                target = target,
                projectId = params["projectId"],
                sessionId = sessionId,
                inboxItemId = params["inboxItemId"] ?: params["itemId"],
            )
        }

        private fun sectionForTarget(target: String): SupervisorShellSection? {
            return when (target.lowercase()) {
                "project", "projects" -> SupervisorShellSection.PROJECTS
                "sessions" -> SupervisorShellSection.SESSIONS
                "session", "active" -> SupervisorShellSection.ACTIVE
                "inbox", "approval", "question" -> SupervisorShellSection.INBOX
                else -> null
            }
        }

        private fun sectionForMetadata(
            projectId: String?,
            sessionId: String?,
            inboxItemId: String?,
        ): SupervisorShellSection? {
            return when {
                !inboxItemId.isNullOrBlank() -> SupervisorShellSection.INBOX
                !sessionId.isNullOrBlank() -> SupervisorShellSection.ACTIVE
                !projectId.isNullOrBlank() -> SupervisorShellSection.PROJECTS
                else -> null
            }
        }

        private fun parseQuery(rawQuery: String?): Map<String, String> {
            if (rawQuery.isNullOrBlank()) {
                return emptyMap()
            }

            return rawQuery
                .split('&')
                .mapNotNull { pair ->
                    val parts = pair.split('=', limit = 2)
                    val key = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val value = parts.getOrNull(1).orEmpty()
                    key to value
                }
                .toMap()
        }
    }
}
