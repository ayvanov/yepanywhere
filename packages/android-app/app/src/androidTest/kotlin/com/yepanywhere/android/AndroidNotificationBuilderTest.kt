package com.yepanywhere.android

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yepanywhere.android.ui.SupervisorShellSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNotificationBuilderTest {
    @Test
    fun manifestRequestsRuntimeNotificationPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permissions = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()

        assertTrue(permissions.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    fun registersDefaultNotificationChannelOnAndroidOAndNewer() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.deleteNotificationChannel(AndroidNotificationChannelRegistrar.DEFAULT_CHANNEL_ID)

        val channelId = AndroidNotificationChannelRegistrar(context).ensureDefaultChannel()

        val channel = notificationManager.getNotificationChannel(channelId)
        assertNotNull(channel)
        assertEquals("Yep Anywhere", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun buildsNotificationWithRoutePendingIntentAndChannel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notification = AndroidNotificationBuilder(context).build(
            AndroidNotificationContent(
                title = "Approval needed",
                body = "Session asks to run a command.",
                route = AndroidNotificationRoute(
                    section = SupervisorShellSection.INBOX,
                    projectId = "project-1",
                    sessionId = "session-1",
                    inboxItemId = "inbox-1",
                ),
            ),
        )

        assertEquals("Approval needed", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "Session asks to run a command.",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertNotNull(notification.contentIntent)
        assertEquals(Notification.CATEGORY_MESSAGE, notification.category)
        assertTrue((notification.flags.toInt() and Notification.FLAG_AUTO_CANCEL) != 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertEquals(AndroidNotificationChannelRegistrar.DEFAULT_CHANNEL_ID, notification.channelId)
        }
    }
}
