package com.yepanywhere.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val USERNAME_REGEX: Regex = Regex("^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$")

fun isValidRelayUsername(username: String): Boolean {
    return USERNAME_REGEX.matches(username)
}

@Serializable
data class RelayServerCompatibilityMetadata(
    val appVersion: String? = null,
    val resumeProtocolVersion: Int? = null,
    val renderProtocolVersion: Int? = null,
    val capabilities: List<String>? = null,
)

@Serializable
enum class RelayServerRejectedReason {
    @SerialName("username_taken")
    USERNAME_TAKEN,

    @SerialName("invalid_username")
    INVALID_USERNAME,
}

@Serializable
enum class RelayClientErrorReason {
    @SerialName("server_offline")
    SERVER_OFFLINE,

    @SerialName("unknown_username")
    UNKNOWN_USERNAME,
}

sealed interface RelayRoutingMessage {
    val type: String

    fun encode(): String {
        return when (this) {
            is ServerRegister -> json.encodeToString(this)
            is ServerRegistered -> json.encodeToString(this)
            is ServerRejected -> json.encodeToString(this)
            is ClientConnect -> json.encodeToString(this)
            is ClientConnected -> json.encodeToString(this)
            is ClientError -> json.encodeToString(this)
        }
    }

    @Serializable
    data class ServerRegister(
        override val type: String = TYPE,
        val username: String,
        val installId: String,
        val appVersion: String? = null,
        val resumeProtocolVersion: Int? = null,
        val renderProtocolVersion: Int? = null,
        val capabilities: List<String>? = null,
    ) : RelayRoutingMessage {
        val compatibilityMetadata: RelayServerCompatibilityMetadata?
            get() {
                if (
                    appVersion == null &&
                    resumeProtocolVersion == null &&
                    renderProtocolVersion == null &&
                    capabilities == null
                ) {
                    return null
                }

                return RelayServerCompatibilityMetadata(
                    appVersion = appVersion,
                    resumeProtocolVersion = resumeProtocolVersion,
                    renderProtocolVersion = renderProtocolVersion,
                    capabilities = capabilities,
                )
            }

        companion object {
            const val TYPE = "server_register"
        }
    }

    @Serializable
    data class ServerRegistered(
        override val type: String = TYPE,
    ) : RelayRoutingMessage {
        companion object {
            const val TYPE = "server_registered"
        }
    }

    @Serializable
    data class ServerRejected(
        override val type: String = TYPE,
        val reason: RelayServerRejectedReason,
    ) : RelayRoutingMessage {
        companion object {
            const val TYPE = "server_rejected"
        }
    }

    @Serializable
    data class ClientConnect(
        override val type: String = TYPE,
        val username: String,
    ) : RelayRoutingMessage {
        companion object {
            const val TYPE = "client_connect"
        }
    }

    @Serializable
    data class ClientConnected(
        override val type: String = TYPE,
    ) : RelayRoutingMessage {
        companion object {
            const val TYPE = "client_connected"
        }
    }

    @Serializable
    data class ClientError(
        override val type: String = TYPE,
        val reason: RelayClientErrorReason,
    ) : RelayRoutingMessage {
        companion object {
            const val TYPE = "client_error"
        }
    }

    companion object {
        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun decode(serialized: String): RelayRoutingMessage? {
            return runCatching {
                when (json.parseToJsonElement(serialized).jsonObject["type"]?.jsonPrimitive?.contentOrNull) {
                    ServerRegister.TYPE -> json.decodeFromString<ServerRegister>(serialized)
                    ServerRegistered.TYPE -> json.decodeFromString<ServerRegistered>(serialized)
                    ServerRejected.TYPE -> json.decodeFromString<ServerRejected>(serialized)
                    ClientConnect.TYPE -> json.decodeFromString<ClientConnect>(serialized)
                    ClientConnected.TYPE -> json.decodeFromString<ClientConnected>(serialized)
                    ClientError.TYPE -> json.decodeFromString<ClientError>(serialized)
                    else -> null
                }
            }.getOrNull()
        }
    }
}
