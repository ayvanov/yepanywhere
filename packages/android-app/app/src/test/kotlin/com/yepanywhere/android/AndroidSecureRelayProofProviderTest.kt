package com.yepanywhere.android

import com.iwebpp.crypto.TweetNaclFast
import com.yepanywhere.android.core.model.SrpMessage
import com.yepanywhere.android.core.model.StoredRelaySession
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.bouncycastle.crypto.agreement.srp.SRP6Server
import org.bouncycastle.crypto.agreement.srp.SRP6StandardGroups
import org.bouncycastle.crypto.agreement.srp.SRP6VerifierGenerator
import org.bouncycastle.crypto.digests.SHA512Digest
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidSecureRelayProofProviderTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun generateResumeProofEncryptsExpectedPayload() = runTest {
        val sessionKey = ByteArray(32) { index -> (index + 1).toByte() }
        val fixedNonce = ByteArray(24) { index -> (index + 11).toByte() }
        val provider = AndroidSecureRelayProofProvider(
            secureRandom = FixedSecureRandom(fixedNonce),
            clock = { 1_710_000_000_123L },
        )
        val storedSession = StoredRelaySession(
            wsUrl = "wss://relay.yepanywhere.local",
            username = "demo@yepanywhere",
            sessionId = "session-resume",
            sessionKey = encodeBase64(sessionKey),
        )

        val proof = provider.generateResumeProof(
            storedSession = storedSession,
            challenge = SrpMessage.SessionResumeChallenge(
                sessionId = "session-resume",
                nonce = "resume-challenge",
            ),
        )

        val envelope = json.parseToJsonElement(proof).jsonObject
        assertEquals(encodeBase64(fixedNonce), envelope.getValue("nonce").jsonPrimitive.content)

        val ciphertext = decodeBase64(envelope.getValue("ciphertext").jsonPrimitive.content)
        val decryptedPayload = TweetNaclFast.SecretBox(sessionKey).open(ciphertext, fixedNonce)
        assertNotNull(decryptedPayload)

        val payload = json.parseToJsonElement(decryptedPayload.decodeToString()).jsonObject
        assertEquals(1_710_000_000_123L, payload.getValue("timestamp").jsonPrimitive.long)
        assertEquals("resume-challenge", payload.getValue("challenge").jsonPrimitive.content)
        assertEquals("session-resume", payload.getValue("sessionId").jsonPrimitive.content)
    }

    @Test
    fun generateSrpProofIsAcceptedByServerAndDerivesExpectedSessionKey() = runTest {
        val identity = "demo@yepanywhere"
        val password = "secret-password"
        val group = SRP6StandardGroups.rfc5054_2048
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)

        val verifierGenerator = SRP6VerifierGenerator().apply {
            init(group, SHA512Digest())
        }
        val verifier = verifierGenerator.generateVerifier(
            salt,
            identity.encodeToByteArray(),
            password.encodeToByteArray(),
        )

        val server = SRP6Server().apply {
            init(group, verifier, SHA512Digest(), random)
        }
        val serverPublicB = server.generateServerCredentials()

        val provider = AndroidSecureRelayProofProvider()
        val clientProof = provider.generateSrpProof(
            identity = identity,
            password = password,
            challenge = SrpMessage.ServerChallenge(
                salt = salt.toHexString(),
                B = serverPublicB.toString(16),
            ),
        )

        val clientPublicA = BigInteger(clientProof.A, 16)
        val clientM1 = BigInteger(clientProof.M1, 16)
        val sharedSecret = server.calculateSecret(clientPublicA)

        assertTrue(server.verifyClientEvidenceMessage(clientM1))
        assertEquals(
            expected = encodeBase64(deriveSecretboxKey(bigIntegerToUnsignedByteArray(sharedSecret))),
            actual = clientProof.sessionKey,
        )
    }
}

private fun deriveSecretboxKey(srpSessionKey: ByteArray): ByteArray {
    return MessageDigest.getInstance("SHA-512").digest(srpSessionKey).copyOf(32)
}

private fun ByteArray.toHexString(): String {
    return joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}

private fun bigIntegerToUnsignedByteArray(value: BigInteger): ByteArray {
    val hex = value.toString(16)
    val padded = if (hex.length % 2 == 0) hex else "0$hex"
    return ByteArray(padded.length / 2) { index ->
        padded.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private class FixedSecureRandom(
    private val fixedBytes: ByteArray,
) : SecureRandom() {
    override fun nextBytes(bytes: ByteArray) {
        require(bytes.size == fixedBytes.size) {
            "Unexpected requested random byte length: ${bytes.size}"
        }
        fixedBytes.copyInto(bytes)
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun encodeBase64(bytes: ByteArray): String = Base64.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
private fun decodeBase64(raw: String): ByteArray = Base64.decode(raw)
