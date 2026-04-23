package com.yepanywhere.android

enum class AndroidRelayAuthMode {
    DEMO,
    RELAY,
}

data class AndroidRelayAuthSettings(
    val mode: AndroidRelayAuthMode,
    val relayUrl: String,
    val relayUsername: String?,
) {
    companion object {
        fun fromRaw(
            modeRaw: String,
            relayUrlRaw: String,
            relayUsernameRaw: String,
        ): AndroidRelayAuthSettings {
            val mode = when (modeRaw.trim().lowercase()) {
                "relay" -> AndroidRelayAuthMode.RELAY
                else -> AndroidRelayAuthMode.DEMO
            }
            val relayUsername = relayUsernameRaw.trim().ifEmpty { null }

            return AndroidRelayAuthSettings(
                mode = mode,
                relayUrl = relayUrlRaw.trim(),
                relayUsername = relayUsername,
            )
        }

        fun fromBuildConfig(): AndroidRelayAuthSettings {
            return fromRaw(
                modeRaw = BuildConfig.YEP_RELAY_AUTH_MODE,
                relayUrlRaw = BuildConfig.YEP_RELAY_URL,
                relayUsernameRaw = BuildConfig.YEP_RELAY_USERNAME,
            )
        }
    }
}
