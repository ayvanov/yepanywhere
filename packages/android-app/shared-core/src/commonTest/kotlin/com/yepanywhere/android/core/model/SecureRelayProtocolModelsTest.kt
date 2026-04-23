package com.yepanywhere.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureRelayProtocolModelsTest {
    @Test
    fun decodesSrpHelloWithOriginMetadata() {
        val json = """
            {
              "type": "srp_hello",
              "identity": "alice",
              "browserProfileId": "browser-1",
              "originMetadata": {
                "origin": "https://relay.example",
                "scheme": "https",
                "hostname": "relay.example",
                "port": 443,
                "userAgent": "Android"
              }
            }
        """.trimIndent()

        assertEquals(
            SrpMessage.ClientHello(
                identity = "alice",
                browserProfileId = "browser-1",
                originMetadata = OriginMetadata(
                    origin = "https://relay.example",
                    scheme = "https",
                    hostname = "relay.example",
                    port = 443,
                    userAgent = "Android",
                ),
            ),
            SrpMessage.decode(json),
        )
    }

    @Test
    fun decodesResumeSuccessWithTransportNonce() {
        val json = """
            {
              "type": "srp_resumed",
              "sessionId": "session-1",
              "transportNonce": "nonce-base64"
            }
        """.trimIndent()

        assertEquals(
            SrpMessage.SessionResumed(
                sessionId = "session-1",
                transportNonce = "nonce-base64",
            ),
            SrpMessage.decode(json),
        )
    }

    @Test
    fun rejectsInvalidSessionReasonOutsideKnownEnum() {
        val json = """
            {
              "type": "srp_invalid",
              "reason": "timeout"
            }
        """.trimIndent()

        assertNull(SrpMessage.decode(json))
    }

    @Test
    fun roundTripsStoredRelaySession() {
        val session = StoredRelaySession(
            wsUrl = "wss://relay.example/socket",
            username = "alice",
            sessionId = "session-1",
            sessionKey = "base64-key",
        )

        assertEquals(session, StoredRelaySession.decode(session.encode()))
    }

    @Test
    fun validatesEncryptedEnvelopeShape() {
        assertTrue(
            isEncryptedEnvelope(
                EncryptedEnvelope(
                    nonce = "nonce-base64",
                    ciphertext = "ciphertext-base64",
                ),
            ),
        )
        assertFalse(
            isEncryptedEnvelope(
                EncryptedEnvelope(
                    nonce = "",
                    ciphertext = "ciphertext-base64",
                ),
            ),
        )
    }

    @Test
    fun validatesSequencedEncryptedPayloadShape() {
        assertTrue(isSequencedEncryptedPayload(SequencedEncryptedPayload(seq = 0, msg = "payload")))
        assertFalse(isSequencedEncryptedPayload(SequencedEncryptedPayload(seq = -1, msg = "payload")))
    }
}
