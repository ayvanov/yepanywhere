package com.yepanywhere.android

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

private fun Context.hasPostNotificationsPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return true
    }

    return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

class AndroidNotificationPoster(
    private val context: Context,
    private val notificationBuilder: AndroidRouteNotificationBuilder = AndroidNotificationBuilder(context),
    private val hasPostPermission: () -> Boolean = { context.hasPostNotificationsPermission() },
    private val notifyNotification: (Int, Notification) -> Unit = { id, notification ->
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    },
    private val cancelNotification: (Int) -> Unit = { id ->
        context.getSystemService(NotificationManager::class.java).cancel(id)
    },
) {
    fun post(notificationId: Int, content: AndroidNotificationContent): Boolean {
        if (!hasPostPermission()) {
            return false
        }

        return runCatching {
            notifyNotification(notificationId, notificationBuilder.build(content))
        }.isSuccess
    }

    fun cancel(notificationId: Int): Boolean {
        return runCatching {
            cancelNotification(notificationId)
        }.isSuccess
    }

    fun cancelSession(sessionId: String): Boolean {
        return cancel(AndroidNotificationContent.notificationIdForSession(sessionId))
    }
}
