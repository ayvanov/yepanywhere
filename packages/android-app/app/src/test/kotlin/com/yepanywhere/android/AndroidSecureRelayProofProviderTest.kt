package com.yepanywhere.android

import com.iwebpp.crypto.TweetNaclFast
import com.yepanywhere.android.core.model.SrpMessage
import com.yepanywhere.android.core.model.StoredRelaySession
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
    fun generateSrpProofMatchesTssrp6aReferenceVector() = runTest {
        val provider = AndroidSecureRelayProofProvider(
            secureRandom = FixedSecureRandom(
                decodeHex(
                    "135ca5ee3780c9125ba4ed367fc8115aa3ec357ec71059a2eb347dc60f58a1ea" +
                        "337cc50e57a0e9327bc40d569fe8317ac30c559ee73079c20b549de62f78c10a" +
                        "539ce52e77c009529be42d76bf08519ae32c75be075099e22b74bd064f98e12a" +
                        "73bc054e97e02972bb044d96df2871ba034c95de2770b9024b94dd266fb8014a" +
                        "93dc256eb7004992db246db6ff4891da236cb5fe4790d9226bb4fd468fd8216a" +
                        "b3fc458ed72069b2fb448dd61f68b1fa438cd51e67b0f9428bd41d66aff8418a" +
                        "d31c65aef74089d21b64adf63f88d11a63acf53e87d01962abf43d86cf1861aa" +
                        "f33c85ce1760a9f23b84cd165fa8f13a83cc155ea7f03982cb145da6ef3881ca",
                ),
            ),
        )
        val clientProof = provider.generateSrpProof(
            identity = "demo@yepanywhere",
            password = "secret-password",
            challenge = SrpMessage.ServerChallenge(
                salt = "7b4f3ab91c2de0475512a8c4de99f003",
                B = "158d9989cf97816f6bba8f2776c9efe832c6a2a2fb78d3f43240e2036fe94977d81ddf45838c6400" +
                    "b2e3f52c6837dd70cf26f449a22de4a22d963afa61b2a49a8d3b702b9c09912604bc2db42d8e9b41" +
                    "049285ae8c7f90d9554ff3d2da7d9cfe1dc9a83fab3ad443c5ab10726bf5820ff8b29fe644258e0d" +
                    "ec2b8b06483be240d90dd7c03aa7aa05ee699e8fc16dc49051e03cf73c46bc6677a335c25abdfe99" +
                    "c5003dd05752e77bbc5c83e76cbc8fb1a8131eaf4c0b7b7c30fe6b7fb1e54b72f2c0aaa197a84534" +
                    "12d92478b3de77463bbd9bc92e7599e14b650cb881ea5a265cc30ede4dd21b398336daadc3bd27a8" +
                    "331159de73e6ac60a4a22fbea382cab2",
            ),
        )

        assertEquals(
            expected = "5ee6aca502401dddb16f60373e7f71430de73130cd43303b16858ea1320c37f269f7fc7de4577c4a995405efc542844560aee7d3c47ec20c93fa01eb53cc8b33ead06e70ef8f9967484740825eeeb04ee8a55f71841d8122bdbd4c1b449771856f0d7ee655730196554104dc11c3211c2e1711d3e8379b514cc0f80af63881a476ed357ce0de284161321197d46b58c798346db22e90ccee8cf30d973b3ce4472395eb260786384a5873279c27a1694002c823ab85443ba8f456ea4a605ed1f97f706346d51d23a1af9e7859c8c2a49605c6750722c088c115ecf1501a618d967a611217a034e39110ced397780a2a564f7134351c7bb6d7553e70b8d2f66e5a",
            actual = clientProof.A,
        )
        assertEquals(
            expected = "4e2f99682a19355e289caaf94c2a2452848b6378e5e6525df77eb8f9666b9b9881b0b9908ed84eaf72db4b13c1087a99850d8b32a1e7ed103101071911bef35e",
            actual = clientProof.M1,
        )
        assertEquals(
            expected = "Ff4pDZ4oJRR/VLJaNd3zwPWdksvzTjvNXMG/AX1CFf0=",
            actual = clientProof.sessionKey,
        )
    }
}

private fun decodeHex(hex: String): ByteArray {
    val normalized = hex.trim().removePrefix("0x")
    val padded = if (normalized.length % 2 == 0) normalized else "0$normalized"
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
