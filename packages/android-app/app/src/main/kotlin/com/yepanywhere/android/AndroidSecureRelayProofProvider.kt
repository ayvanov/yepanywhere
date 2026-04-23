package com.yepanywhere.android

import com.iwebpp.crypto.TweetNaclFast
import com.yepanywhere.android.core.model.SrpMessage
import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.core.usecase.SecureRelayClientProof
import com.yepanywhere.android.core.usecase.SecureRelayProofProvider
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bouncycastle.crypto.agreement.srp.SRP6Client
import org.bouncycastle.crypto.agreement.srp.SRP6StandardGroups
import org.bouncycastle.crypto.digests.SHA512Digest
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val SECRETBOX_KEY_LENGTH = 32
private const val SECRETBOX_NONCE_LENGTH = 24

/**
 * Production proof provider used by Android relay auth:
 * - full SRP client proof generation (A, M1)
 * - resume-proof encryption bound to server challenge nonce
 */
class AndroidSecureRelayProofProvider(
    private val secureRandom: SecureRandom = SecureRandom(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : SecureRelayProofProvider {
    override suspend fun generateResumeProof(
        storedSession: StoredRelaySession,
        challenge: SrpMessage.SessionResumeChallenge,
    ): String {
        val sessionKey = decodeBase64(storedSession.sessionKey)
        require(sessionKey.size == SECRETBOX_KEY_LENGTH) { "invalid_stored_session_key_length" }

        val nonce = ByteArray(SECRETBOX_NONCE_LENGTH).also(secureRandom::nextBytes)
        val payload = buildJsonObject {
            put("timestamp", clock())
            put("challenge", challenge.nonce)
            put("sessionId", storedSession.sessionId)
        }.toString().encodeToByteArray()

        val ciphertext = TweetNaclFast.SecretBox(sessionKey).box(payload, nonce)
            ?: error("resume_proof_encryption_failed")

        return buildJsonObject {
            put("nonce", encodeBase64(nonce))
            put("ciphertext", encodeBase64(ciphertext))
        }.toString()
    }

    override suspend fun generateSrpProof(
        identity: String,
        password: String,
        challenge: SrpMessage.ServerChallenge,
    ): SecureRelayClientProof {
        val srpClient = SRP6Client().apply {
            init(
                SRP6StandardGroups.rfc5054_2048,
                SHA512Digest(),
                secureRandom,
            )
        }

        val A = srpClient.generateClientCredentials(
            decodeHex(challenge.salt),
            identity.encodeToByteArray(),
            password.encodeToByteArray(),
        )
        val sessionSecret = srpClient.calculateSecret(hexToBigInteger(challenge.B))
        val M1 = srpClient.calculateClientEvidenceMessage()
        val derivedSessionKey = deriveSecretboxKey(bigIntegerToByteArray(sessionSecret))

        return SecureRelayClientProof(
            A = A.toString(16),
            M1 = M1.toString(16),
            sessionKey = encodeBase64(derivedSessionKey),
        )
    }
}

private fun deriveSecretboxKey(srpSessionKey: ByteArray): ByteArray {
    return MessageDigest.getInstance("SHA-512")
        .digest(srpSessionKey)
        .copyOf(SECRETBOX_KEY_LENGTH)
}

private fun hexToBigInteger(hex: String): BigInteger {
    val normalized = hex.trim().removePrefix("0x").ifEmpty { "0" }
    return BigInteger(normalized, 16)
}

private fun decodeHex(hex: String): ByteArray {
    val normalized = hex.trim().removePrefix("0x")
    if (normalized.isEmpty()) {
        return byteArrayOf()
    }
    val padded = if (normalized.length % 2 == 0) normalized else "0$normalized"
    return ByteArray(padded.length / 2) { index ->
        padded.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private fun bigIntegerToByteArray(value: BigInteger): ByteArray {
    val hex = value.toString(16)
    if (hex.isEmpty()) {
        return byteArrayOf()
    }
    val padded = if (hex.length % 2 == 0) hex else "0$hex"
    return ByteArray(padded.length / 2) { index ->
        padded.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun encodeBase64(bytes: ByteArray): String = Base64.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
private fun decodeBase64(raw: String): ByteArray = Base64.decode(raw)
