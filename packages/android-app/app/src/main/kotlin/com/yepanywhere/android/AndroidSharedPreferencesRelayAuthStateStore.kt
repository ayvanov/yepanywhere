package com.yepanywhere.android

import android.content.Context
import android.content.SharedPreferences
import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.data.PersistedRelayAuthState
import com.yepanywhere.android.data.RelayAuthStateStore
import com.yepanywhere.android.data.RelayCredentials

class AndroidSharedPreferencesRelayAuthStateStore(
    context: Context,
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
) : RelayAuthStateStore {
    override suspend fun read(): PersistedRelayAuthState? {
        val relayUrl = preferences.getString(KEY_RELAY_URL, null)?.trim().orEmpty()
        val username = preferences.getString(KEY_USERNAME, null)?.trim().orEmpty()
        val password = preferences.getString(KEY_PASSWORD, null).orEmpty()
        if (relayUrl.isBlank() || username.isBlank() || password.isBlank()) {
            return null
        }

        val storedSession = preferences.getString(KEY_STORED_SESSION, null)
            ?.let(StoredRelaySession::decode)

        return PersistedRelayAuthState(
            credentials = RelayCredentials(
                relayUrl = relayUrl,
                username = username,
                password = password,
            ),
            storedSession = storedSession,
        )
    }

    override suspend fun write(state: PersistedRelayAuthState) {
        preferences.edit()
            .putString(KEY_RELAY_URL, state.credentials.relayUrl)
            .putString(KEY_USERNAME, state.credentials.username)
            .putString(KEY_PASSWORD, state.credentials.password)
            .putString(KEY_STORED_SESSION, state.storedSession?.encode())
            .apply()
    }

    override suspend fun clear() {
        preferences.edit()
            .remove(KEY_RELAY_URL)
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .remove(KEY_STORED_SESSION)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "relay_auth_state"
        private const val KEY_RELAY_URL = "relay_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_STORED_SESSION = "stored_session"
    }
}
