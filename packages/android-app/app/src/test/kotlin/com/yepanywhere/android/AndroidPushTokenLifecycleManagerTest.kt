package com.yepanywhere.android

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidPushTokenLifecycleManagerTest {
    private lateinit var application: Application
    private lateinit var store: AndroidPushTokenStore

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences("relay_push_token_state", Context.MODE_PRIVATE).edit().clear().commit()
        store = AndroidPushTokenStore(application)
    }

    @Test
    fun onNewTokenPersistsAndPublishesOnlyOnChange() {
        val published = mutableListOf<String>()
        val manager = AndroidPushTokenLifecycleManager(
            store = store,
            onTokenUpdated = published::add,
        )

        assertTrue(manager.onNewToken("token-1"))
        assertTrue(manager.onNewToken("token-1"))
        assertTrue(manager.onNewToken("token-2"))

        assertEquals("token-2", store.read()?.token)
        assertEquals(listOf("token-1", "token-2"), published)
    }

    @Test
    fun ignoresBlankTokens() {
        val manager = AndroidPushTokenLifecycleManager(store = store)

        assertEquals(false, manager.onNewToken("   "))
        assertNull(store.read())
    }
}
