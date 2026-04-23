package com.yepanywhere.android

import android.content.Context
import android.content.SharedPreferences

data class AndroidPushTokenSnapshot(
    val token: String,
    val updatedAtEpochMs: Long,
)

class AndroidPushTokenStore(
    context: Context,
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
) {
    fun read(): AndroidPushTokenSnapshot? {
        val token = preferences.getString(KEY_TOKEN, null)?.trim().orEmpty()
        if (token.isBlank()) {
            return null
        }
        val updatedAt = preferences.getLong(KEY_UPDATED_AT, 0L)
        return AndroidPushTokenSnapshot(
            token = token,
            updatedAtEpochMs = updatedAt,
        )
    }

    fun write(
        token: String,
        updatedAtEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
        return preferences.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_UPDATED_AT, updatedAtEpochMs)
            .commit()
    }

    companion object {
        private const val PREFERENCES_NAME = "relay_push_token_state"
        private const val KEY_TOKEN = "fcm_token"
        private const val KEY_UPDATED_AT = "updated_at_epoch_ms"
    }
}

class AndroidPushTokenLifecycleManager(
    private val store: AndroidPushTokenStore,
    private val onTokenUpdated: (String) -> Unit = {},
) {
    fun onNewToken(token: String): Boolean {
        val normalizedToken = token.trim()
        if (normalizedToken.isBlank()) {
            return false
        }

        val previousToken = store.read()?.token
        val persisted = store.write(normalizedToken)
        if (!persisted) {
            return false
        }
        if (previousToken != normalizedToken) {
            onTokenUpdated(normalizedToken)
        }
        return true
    }
}
