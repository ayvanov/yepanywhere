package com.yepanywhere.android

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.yepanywhere.android.ui.SupervisorShellSection

class AndroidNotificationIntentFactory(
    private val context: Context,
) {
    val pendingIntentFlags: Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun createOpenIntent(route: AndroidNotificationRoute): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = route.toDeepLinkUri()
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AndroidNotificationRoute.EXTRA_TARGET, route.section.toTarget())
            route.projectId?.let { putExtra(AndroidNotificationRoute.EXTRA_PROJECT_ID, it) }
            route.sessionId?.let { putExtra(AndroidNotificationRoute.EXTRA_SESSION_ID, it) }
            route.inboxItemId?.let { putExtra(AndroidNotificationRoute.EXTRA_INBOX_ITEM_ID, it) }
        }
    }

    fun createOpenPendingIntent(route: AndroidNotificationRoute): PendingIntent {
        return PendingIntent.getActivity(
            context,
            route.requestCode(),
            createOpenIntent(route),
            pendingIntentFlags,
        )
    }

    private fun AndroidNotificationRoute.toDeepLinkUri(): Uri {
        val target = section.toTarget()
        val params = buildList {
            projectId?.let { add("projectId=$it") }
            sessionId?.let { add("sessionId=$it") }
            inboxItemId?.let { add("inboxItemId=$it") }
        }
        val query = params.takeIf { it.isNotEmpty() }?.joinToString(separator = "&")
        return Uri.parse(
            buildString {
                append("yepanywhere://open/")
                append(target)
                if (query != null) {
                    append('?')
                    append(query)
                }
            },
        )
    }

    private fun AndroidNotificationRoute.requestCode(): Int {
        return listOf(section.name, projectId, sessionId, inboxItemId)
            .joinToString(separator = "|")
            .hashCode()
    }

    private fun SupervisorShellSection.toTarget(): String {
        return when (this) {
            SupervisorShellSection.PROJECTS -> "projects"
            SupervisorShellSection.SESSIONS -> "sessions"
            SupervisorShellSection.INBOX -> "inbox"
            SupervisorShellSection.ACTIVE -> "session"
        }
    }
}
