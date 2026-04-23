package com.yepanywhere.android

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yepanywhere.android.ui.SupervisorShellSection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNotificationRouteIntentTest {
    @Test
    fun extractsRouteFromNotificationExtras() {
        val intent = Intent().apply {
            putExtra(AndroidNotificationRoute.EXTRA_TARGET, "inbox")
            putExtra(AndroidNotificationRoute.EXTRA_PROJECT_ID, "project-1")
            putExtra(AndroidNotificationRoute.EXTRA_SESSION_ID, "session-1")
            putExtra(AndroidNotificationRoute.EXTRA_INBOX_ITEM_ID, "inbox-1")
        }

        assertEquals(
            AndroidNotificationRoute(
                section = SupervisorShellSection.INBOX,
                projectId = "project-1",
                sessionId = "session-1",
                inboxItemId = "inbox-1",
            ),
            AndroidNotificationRoute.fromIntent(intent),
        )
    }

    @Test
    fun extractsRouteFromDeepLinkIntentData() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("yepanywhere://open/session/session-1"),
        )

        assertEquals(
            AndroidNotificationRoute(
                section = SupervisorShellSection.ACTIVE,
                sessionId = "session-1",
            ),
            AndroidNotificationRoute.fromIntent(intent),
        )
    }
}
