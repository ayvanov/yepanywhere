package com.yepanywhere.android

import android.app.PendingIntent
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yepanywhere.android.ui.SupervisorShellSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNotificationIntentFactoryTest {
    @Test
    fun createsMainActivityIntentWithRouteExtrasAndDeepLinkData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = AndroidNotificationIntentFactory(context)
        val route = AndroidNotificationRoute(
            section = SupervisorShellSection.INBOX,
            projectId = "project-1",
            sessionId = "session-1",
            inboxItemId = "inbox-1",
        )

        val intent = factory.createOpenIntent(route)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(route, AndroidNotificationRoute.fromIntent(intent))
        assertEquals("yepanywhere://open/inbox?projectId=project-1&sessionId=session-1&inboxItemId=inbox-1", intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
    }

    @Test
    fun createsImmutablePendingIntentForRoute() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = AndroidNotificationIntentFactory(context)

        val pendingIntent = factory.createOpenPendingIntent(
            AndroidNotificationRoute(
                section = SupervisorShellSection.ACTIVE,
                sessionId = "session-1",
            ),
        )

        assertNotNull(pendingIntent)
        assertTrue(factory.pendingIntentFlags and PendingIntent.FLAG_IMMUTABLE != 0)
        assertTrue(factory.pendingIntentFlags and PendingIntent.FLAG_UPDATE_CURRENT != 0)
    }
}
