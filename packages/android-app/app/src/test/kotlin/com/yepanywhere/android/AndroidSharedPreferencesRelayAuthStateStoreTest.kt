package com.yepanywhere.android

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.data.PersistedRelayAuthState
import com.yepanywhere.android.data.RelayCredentials
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidSharedPreferencesRelayAuthStateStoreTest {
    private lateinit var application: Application
    private lateinit var securePreferences: SharedPreferences
    private lateinit var stateStore: AndroidSharedPreferencesRelayAuthStateStore

    @Before
    fun setUp() = runTest {
        application = ApplicationProvider.getApplicationContext()
        securePreferences = application.getSharedPreferences("relay_auth_state", Context.MODE_PRIVATE)
        securePreferences.edit().clear().commit()
        stateStore = AndroidSharedPreferencesRelayAuthStateStore(
            context = application,
            securePreferences = securePreferences,
        )
        stateStore.clear()
    }

    @Test
    fun writeThenReadRoundTripsDataStoreAndSecureState() = runTest {
        val state = PersistedRelayAuthState(
            credentials = RelayCredentials(
                relayUrl = "wss://relay.yepanywhere.local",
                username = "demo@yepanywhere",
                password = "secret",
            ),
            storedSession = StoredRelaySession(
                wsUrl = "wss://relay.yepanywhere.local",
                username = "demo@yepanywhere",
                sessionId = "relay-session-1",
                sessionKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            ),
        )

        stateStore.write(state)

        val restored = stateStore.read()
        assertEquals(state, restored)
        assertNull(securePreferences.getString("relay_url", null))
        assertNull(securePreferences.getString("username", null))
        assertEquals("secret", securePreferences.getString("password", null))
        assertNotNull(securePreferences.getString("stored_session", null))
    }

    @Test
    fun readMigratesLegacySharedPreferencesSettings() = runTest {
        securePreferences.edit()
            .putString("relay_url", "wss://legacy-relay.yepanywhere.local")
            .putString("username", "legacy@yepanywhere")
            .putString("password", "legacy-secret")
            .putString(
                "stored_session",
                StoredRelaySession(
                    wsUrl = "wss://legacy-relay.yepanywhere.local",
                    username = "legacy@yepanywhere",
                    sessionId = "legacy-session",
                    sessionKey = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
                ).encode(),
            )
            .commit()

        val restored = stateStore.read()
        assertNotNull(restored)
        assertEquals("wss://legacy-relay.yepanywhere.local", restored.credentials.relayUrl)
        assertEquals("legacy@yepanywhere", restored.credentials.username)
        assertEquals("legacy-secret", restored.credentials.password)
        assertNull(securePreferences.getString("relay_url", null))
        assertNull(securePreferences.getString("username", null))
    }
}
