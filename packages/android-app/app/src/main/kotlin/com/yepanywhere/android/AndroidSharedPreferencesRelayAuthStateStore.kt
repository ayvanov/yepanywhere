package com.yepanywhere.android

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.data.PersistedRelayAuthState
import com.yepanywhere.android.data.RelayAuthStateStore
import com.yepanywhere.android.data.RelayCredentials
import kotlinx.coroutines.flow.first

class AndroidSharedPreferencesRelayAuthStateStore(
    context: Context,
    private val securePreferences: SharedPreferences = context.getSharedPreferences(SECURE_PREFERENCES_NAME, Context.MODE_PRIVATE),
    private val settingsDataStore: DataStore<Preferences> = context.applicationContext.relayAuthSettingsDataStore,
) : RelayAuthStateStore {
    override suspend fun read(): PersistedRelayAuthState? {
        var relayUrl: String
        var username: String
        val settingsSnapshot = settingsDataStore.data.first()
        relayUrl = settingsSnapshot[KEY_RELAY_URL]?.trim().orEmpty()
        username = settingsSnapshot[KEY_USERNAME]?.trim().orEmpty()
        if (relayUrl.isBlank() || username.isBlank()) {
            migrateLegacySettingsIfNeeded()
            val migratedSettings = settingsDataStore.data.first()
            relayUrl = migratedSettings[KEY_RELAY_URL]?.trim().orEmpty()
            username = migratedSettings[KEY_USERNAME]?.trim().orEmpty()
        }
        if (relayUrl.isBlank() || username.isBlank()) {
            return null
        }

        val password = securePreferences.getString(KEY_PASSWORD, null).orEmpty()
        val storedSession = securePreferences.getString(KEY_STORED_SESSION, null)
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
        settingsDataStore.edit { settings ->
            settings[KEY_RELAY_URL] = state.credentials.relayUrl
            settings[KEY_USERNAME] = state.credentials.username
        }

        val persisted = securePreferences.edit()
            .putString(KEY_PASSWORD, state.credentials.password)
            .putString(KEY_STORED_SESSION, state.storedSession?.encode())
            .commit()
        check(persisted) { "relay_auth_state_write_failed" }
    }

    override suspend fun clear() {
        settingsDataStore.edit { settings ->
            settings.remove(KEY_RELAY_URL)
            settings.remove(KEY_USERNAME)
        }

        val persisted = securePreferences.edit()
            .remove(KEY_PASSWORD)
            .remove(KEY_STORED_SESSION)
            .remove(KEY_LEGACY_RELAY_URL)
            .remove(KEY_LEGACY_USERNAME)
            .commit()
        check(persisted) { "relay_auth_state_clear_failed" }
    }

    private suspend fun migrateLegacySettingsIfNeeded() {
        val legacyRelayUrl = securePreferences.getString(KEY_LEGACY_RELAY_URL, null)?.trim().orEmpty()
        val legacyUsername = securePreferences.getString(KEY_LEGACY_USERNAME, null)?.trim().orEmpty()
        if (legacyRelayUrl.isBlank() || legacyUsername.isBlank()) {
            return
        }

        settingsDataStore.edit { settings ->
            settings[KEY_RELAY_URL] = legacyRelayUrl
            settings[KEY_USERNAME] = legacyUsername
        }

        val persisted = securePreferences.edit()
            .remove(KEY_LEGACY_RELAY_URL)
            .remove(KEY_LEGACY_USERNAME)
            .commit()
        check(persisted) { "relay_auth_state_settings_migration_failed" }
    }

    companion object {
        private const val SECURE_PREFERENCES_NAME = "relay_auth_state"
        private val KEY_RELAY_URL = stringPreferencesKey("relay_url")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private const val KEY_LEGACY_RELAY_URL = "relay_url"
        private const val KEY_LEGACY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_STORED_SESSION = "stored_session"
    }
}

private val Context.relayAuthSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "relay_auth_settings",
)
