package com.yepanywhere.android

import com.iwebpp.crypto.TweetNaclFast
import com.yepanywhere.android.core.model.SrpMessage
import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.core.usecase.SecureRelayClientProof
import com.yepanywhere.android.core.usecase.SecureRelayProofProvider
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bouncycastle.crypto.agreement.srp.SRP6StandardGroups
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val SECRETBOX_KEY_LENGTH = 32
private const val SECRETBOX_NONCE_LENGTH = 24
private const val SRP_HASH_ALGORITHM = "SHA-512"
private const val TRANSPORT_HASH_ALGORITHM = "SHA-512"
private val SRP_N: BigInteger = SRP6StandardGroups.rfc5054_2048.getN()
private val SRP_G: BigInteger = SRP6StandardGroups.rfc5054_2048.getG()
private val SRP_PADDED_LENGTH: Int = (SRP_N.bitLength() + 7) / 8

/**
 * Production proof provider used by Android relay auth:
 * - full SRP client proof generation (A, M1) compatible with tssrp6a server flow
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
        require(identity.isNotBlank()) { "identity_missing" }
        val salt = hexToBigInteger(challenge.salt)
        val serverPublicB = hexToBigInteger(challenge.B)
        require(isValidPublicValue(serverPublicB)) { "invalid_server_public_value" }

        val privateA = generatePrivateValue(secureRandom)
        val publicA = SRP_G.modPow(privateA, SRP_N)
        require(isValidPublicValue(publicA)) { "invalid_client_public_value" }

        val multiplierK = computeMultiplierK()
        // tssrp6a intentionally hashes only the password for identity hash.
        val identityHash = sha256(password.encodeToByteArray())
        val x = sha256AsBigInteger(bigIntegerToByteArray(salt), identityHash)
        val scramblingU = sha256PaddedAsBigInteger(publicA, serverPublicB)

        val verifier = SRP_G.modPow(x, SRP_N)
        val base = serverPublicB.subtract(verifier.multiply(multiplierK).mod(SRP_N)).mod(SRP_N)
        val exponent = scramblingU.multiply(x).add(privateA)
        val sharedSecret = base.modPow(exponent, SRP_N)
        val clientEvidenceM1 = sha256AsBigInteger(
            bigIntegerToByteArray(publicA),
            bigIntegerToByteArray(serverPublicB),
            bigIntegerToByteArray(sharedSecret),
        )
        val derivedSessionKey = deriveSecretboxKey(bigIntegerToByteArray(sharedSecret))

        return SecureRelayClientProof(
            A = publicA.toString(16),
            M1 = clientEvidenceM1.toString(16),
            sessionKey = encodeBase64(derivedSessionKey),
        )
    }
}

private fun deriveSecretboxKey(srpSessionKey: ByteArray): ByteArray {
    return MessageDigest.getInstance(TRANSPORT_HASH_ALGORITHM)
        .digest(srpSessionKey)
        .copyOf(SECRETBOX_KEY_LENGTH)
}

private fun isValidPublicValue(value: BigInteger): Boolean {
    return value.mod(SRP_N) != BigInteger.ZERO
}

private fun computeMultiplierK(): BigInteger {
    return sha256AsBigInteger(
        leftPadToSrpLength(bigIntegerToByteArray(SRP_N)),
        leftPadToSrpLength(bigIntegerToByteArray(SRP_G)),
    )
}

private fun generatePrivateValue(secureRandom: SecureRandom): BigInteger {
    val numBits = maxOf(256, SRP_N.bitLength())
    val numBytes = numBits / 8
    while (true) {
        val randomBytes = ByteArray(numBytes).also(secureRandom::nextBytes)
        val candidate = BigInteger(1, randomBytes).mod(SRP_N)
        if (candidate != BigInteger.ZERO) {
            return candidate
        }
    }
}

private fun sha256PaddedAsBigInteger(left: BigInteger, right: BigInteger): BigInteger {
    return sha256AsBigInteger(
        leftPadToSrpLength(bigIntegerToByteArray(left)),
        leftPadToSrpLength(bigIntegerToByteArray(right)),
    )
}

private fun leftPadToSrpLength(bytes: ByteArray): ByteArray {
    if (bytes.size >= SRP_PADDED_LENGTH) {
        return bytes
    }
    val padded = ByteArray(SRP_PADDED_LENGTH)
    bytes.copyInto(padded, destinationOffset = SRP_PADDED_LENGTH - bytes.size)
    return padded
}

private fun sha256AsBigInteger(vararg chunks: ByteArray): BigInteger {
    return BigInteger(1, sha256(*chunks))
}

private fun sha256(vararg chunks: ByteArray): ByteArray {
    val digest = MessageDigest.getInstance(SRP_HASH_ALGORITHM)
    chunks.forEach(digest::update)
    return digest.digest()
}

private fun hexToBigInteger(hex: String): BigInteger {
    val normalized = hex.trim().removePrefix("0x").ifEmpty { "0" }
    return BigInteger(normalized, 16)
}

private fun bigIntegerToByteArray(value: BigInteger): ByteArray {
    val hex = value.toString(16)
    val padded = if (hex.length % 2 == 0) hex else "0$hex"
    return ByteArray(padded.length / 2) { index ->
        padded.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun encodeBase64(bytes: ByteArray): String = Base64.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
private fun decodeBase64(raw: String): ByteArray = Base64.decode(raw)
