package com.yepanywhere.android

import android.app.Notification
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yepanywhere.android.ui.SupervisorShellSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNotificationPosterTest {
    @Test
    fun doesNotBuildOrNotifyWhenPermissionCheckFails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val builder = RecordingRouteNotificationBuilder(context)
        var notified = false
        val poster = AndroidNotificationPoster(
            context = context,
            notificationBuilder = builder,
            hasPostPermission = { false },
            notifyNotification = { _, _ -> notified = true },
        )

        val posted = poster.post(notificationId = 7, content = sampleContent())

        assertFalse(posted)
        assertNull(builder.lastContent)
        assertFalse(notified)
    }

    @Test
    fun postsBuiltNotificationWhenPermissionCheckPasses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val builder = RecordingRouteNotificationBuilder(context)
        var notifiedId: Int? = null
        var notifiedNotification: Notification? = null
        val poster = AndroidNotificationPoster(
            context = context,
            notificationBuilder = builder,
            hasPostPermission = { true },
            notifyNotification = { id, notification ->
                notifiedId = id
                notifiedNotification = notification
            },
        )
        val content = sampleContent()

        val posted = poster.post(notificationId = 7, content = content)

        assertTrue(posted)
        assertEquals(content, builder.lastContent)
        assertEquals(7, notifiedId)
        assertNotNull(notifiedNotification)
    }

    private fun sampleContent(): AndroidNotificationContent {
        return AndroidNotificationContent(
            title = "Approval needed",
            body = "Session asks to run a command.",
            route = AndroidNotificationRoute(
                section = SupervisorShellSection.INBOX,
                projectId = "project-1",
                sessionId = "session-1",
                inboxItemId = "inbox-1",
            ),
        )
    }

    private class RecordingRouteNotificationBuilder(
        private val context: Context,
    ) : AndroidRouteNotificationBuilder {
        var lastContent: AndroidNotificationContent? = null

        override fun build(content: AndroidNotificationContent): Notification {
            lastContent = content
            @Suppress("DEPRECATION")
            return Notification.Builder(context)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(content.title)
                .setContentText(content.body)
                .build()
        }
    }
}
