package com.yepanywhere.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class OriginMetadata(
    val origin: String,
    val scheme: String,
    val hostname: String,
    val port: Int? = null,
    val userAgent: String,
)

@Serializable
data class StoredRelaySession(
    val wsUrl: String,
    val username: String,
    val sessionId: String,
    val sessionKey: String,
) {
    fun encode(): String = json.encodeToString(this)

    companion object {
        fun decode(serialized: String): StoredRelaySession? {
            return runCatching { json.decodeFromString<StoredRelaySession>(serialized) }.getOrNull()
        }
    }
}

@Serializable
enum class SrpErrorCode {
    @SerialName("invalid_identity")
    INVALID_IDENTITY,

    @SerialName("invalid_proof")
    INVALID_PROOF,

    @SerialName("server_error")
    SERVER_ERROR,
}

@Serializable
enum class SrpSessionInvalidReason {
    @SerialName("expired")
    EXPIRED,

    @SerialName("unknown")
    UNKNOWN,

    @SerialName("invalid_proof")
    INVALID_PROOF,
}

sealed interface SrpMessage {
    val type: String

    fun encode(): String {
        return when (this) {
            is ClientHello -> json.encodeToString(this)
            is ServerChallenge -> json.encodeToString(this)
            is ClientProof -> json.encodeToString(this)
            is ServerVerify -> json.encodeToString(this)
            is Error -> json.encodeToString(this)
            is SessionResumeInit -> json.encodeToString(this)
            is SessionResumeChallenge -> json.encodeToString(this)
            is SessionResume -> json.encodeToString(this)
            is SessionResumed -> json.encodeToString(this)
            is SessionInvalid -> json.encodeToString(this)
        }
    }

    @Serializable
    data class ClientHello(
        override val type: String = TYPE,
        val identity: String,
        val browserProfileId: String? = null,
        val originMetadata: OriginMetadata? = null,
    ) : SrpMessage {
        companion object {
            const val TYPE = "srp_hello"
        }
    }

    @Serializable
    data class ServerChallenge(
        override val type: String = TYPE,
        val salt: String,
        val B: String,
    ) : SrpMessage {
        companion object {
            const val TYPE = "srp_challenge"
        }
    }

    @Serializable
    data class ClientProof(
        override val type: String = TYPE,
        val A: String,
        val M1: String,
    ) : SrpMessage {
        companion object {
            const val TYPE = "srp_proof"
        }
    }

    @Serializable
    data class ServerVerify(
        override val type: String = TYPE,
        val M2: String,
        val sessionId: String? = null,
        val transportNonce: String? = null,
    ) : SrpMessage {
        companion object {
            const val TYPE = "srp_verify"
        }
    }

    @Serializable
    data class Error(
        override val type: String = TYPE,
        val code: SrpErrorCode,
        val message: String,
    ) : SrpMessage {
        companion object {
            const val TYPE = "srp_error"
        }
    }

    @Serializable
    data class SessionResumeInit(
        override val type: String = TYPE,
        val identity: String,
        val sessionId: String,
    ) : SrpMessage {
        companion object {
            const val TYPE = "srp_resume_init"
        }
    }

    @Serializable
    data class SessionResumeChallenge(
        override val type: String = TYPE,
        val sessionId: String,
        val nonce: String,
    ) : SrpMessage {
        companion object {
            const val TYPE = "srp_resume_challenge"
        }
    }

    @Serializable
    data class SessionResume(
        override val type: String = TYPE,
        val identity: String,
        val sessionId: String,
        val proof: String,
    ) : SrpMessage {
        companion object {
            const val TYPE = "srp_resume"
        }
    }

    @Serializable
    data class SessionResumed(
        override val type: String = TYPE,
        val sessionId: String,
        val transportNonce: String? = null,
    ) : SrpMessage {
        companion object {
            const val TYPE = "srp_resumed"
        }
    }

    @Serializable
    data class SessionInvalid(
        override val type: String = TYPE,
        val reason: SrpSessionInvalidReason,
    ) : SrpMessage {
        companion object {
            const val TYPE = "srp_invalid"
        }
    }

    companion object {
        fun decode(serialized: String): SrpMessage? {
            return runCatching {
                when (json.parseToJsonElement(serialized).jsonObject["type"]?.jsonPrimitive?.contentOrNull) {
                    ClientHello.TYPE -> json.decodeFromString<ClientHello>(serialized)
                    ServerChallenge.TYPE -> json.decodeFromString<ServerChallenge>(serialized)
                    ClientProof.TYPE -> json.decodeFromString<ClientProof>(serialized)
                    ServerVerify.TYPE -> json.decodeFromString<ServerVerify>(serialized)
                    Error.TYPE -> json.decodeFromString<Error>(serialized)
                    SessionResumeInit.TYPE -> json.decodeFromString<SessionResumeInit>(serialized)
                    SessionResumeChallenge.TYPE -> json.decodeFromString<SessionResumeChallenge>(serialized)
                    SessionResume.TYPE -> json.decodeFromString<SessionResume>(serialized)
                    SessionResumed.TYPE -> json.decodeFromString<SessionResumed>(serialized)
                    SessionInvalid.TYPE -> json.decodeFromString<SessionInvalid>(serialized)
                    else -> null
                }
            }.getOrNull()
        }
    }
}

@Serializable
data class EncryptedEnvelope(
    val type: String = TYPE,
    val nonce: String,
    val ciphertext: String,
) {
    companion object {
        const val TYPE = "encrypted"
    }
}

data class SequencedEncryptedPayload<T>(
    val seq: Long,
    val msg: T,
)

fun isEncryptedEnvelope(msg: EncryptedEnvelope): Boolean {
    return msg.type == EncryptedEnvelope.TYPE &&
        msg.nonce.isNotBlank() &&
        msg.ciphertext.isNotBlank()
}

fun isSequencedEncryptedPayload(msg: SequencedEncryptedPayload<*>): Boolean {
    return msg.seq >= 0
}

private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
