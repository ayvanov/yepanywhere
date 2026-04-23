package com.yepanywhere.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

data class AndroidNotificationContent(
    val title: String,
    val body: String,
    val route: AndroidNotificationRoute,
) {
    fun notificationId(): Int {
        return listOf(
            route.section.name,
            route.projectId,
            route.sessionId,
            route.inboxItemId,
        ).joinToString(separator = "|").hashCode()
    }

    companion object {
        fun fromPayload(
            title: String?,
            body: String?,
            data: Map<String, String>,
        ): AndroidNotificationContent? {
            val route = AndroidNotificationRoute.fromData(data) ?: return null

            return AndroidNotificationContent(
                title = title.takeUnlessBlank() ?: "Yep Anywhere",
                body = body.takeUnlessBlank() ?: "Open the app to review this update.",
                route = route,
            )
        }

        private fun String?.takeUnlessBlank(): String? {
            return this?.takeIf { it.isNotBlank() }
        }
    }
}

fun interface AndroidRouteNotificationBuilder {
    fun build(content: AndroidNotificationContent): Notification
}

class AndroidNotificationChannelRegistrar(
    private val context: Context,
) {
    fun ensureDefaultChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                DEFAULT_CHANNEL_ID,
                "Yep Anywhere",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Agent session updates and approval requests"
            }
            notificationManager.createNotificationChannel(channel)
        }

        return DEFAULT_CHANNEL_ID
    }

    companion object {
        const val DEFAULT_CHANNEL_ID = "yep_anywhere_agent_updates"
    }
}

class AndroidNotificationBuilder(
    private val context: Context,
    private val intentFactory: AndroidNotificationIntentFactory = AndroidNotificationIntentFactory(context),
    private val channelRegistrar: AndroidNotificationChannelRegistrar = AndroidNotificationChannelRegistrar(context),
) : AndroidRouteNotificationBuilder {
    override fun build(content: AndroidNotificationContent): Notification {
        val channelId = channelRegistrar.ensureDefaultChannel()
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        @Suppress("DEPRECATION")
        return builder
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setStyle(Notification.BigTextStyle().bigText(content.body))
            .setContentIntent(intentFactory.createOpenPendingIntent(content.route))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .build()
    }
}
