package com.yepanywhere.android.core.usecase

import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.SrpMessage
import com.yepanywhere.android.core.model.StoredRelaySession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorSecureRelayAuthHandshakeRunnerTest {
    @Test
    fun runExecutesHandshakeAndClosesTransport() = runTest {
        val socket = FakeSecureRelayAuthSocket(
            incomingTexts = ArrayDeque(
                listOf(
                    """{"type":"srp_resume_challenge","sessionId":"session-1","nonce":"resume-challenge"}""",
                    """{"type":"srp_resumed","sessionId":"session-1","transportNonce":"transport-nonce"}""",
                ),
            ),
        )
        val transport = KtorSecureRelayAuthTransport(sessionFactory = { socket })
        var createdTransports = 0
        val runner = KtorSecureRelayAuthHandshakeRunner(
            proofProvider = FakeSecureRelayProofProvider(
                resumeProof = """{"nonce":"resume-nonce","ciphertext":"resume-ciphertext"}""",
            ),
            transportFactory = { _, _ ->
                createdTransports += 1
                transport
            },
        )

        val result = runner.run(
            relayUrl = "wss://relay.yepanywhere.local",
            identity = "demo@yepanywhere",
            password = "secret",
            storedSession = StoredRelaySession(
                wsUrl = "wss://relay.yepanywhere.local",
                username = "demo@yepanywhere",
                sessionId = "session-1",
                sessionKey = "stored-session-key",
            ),
        )

        assertEquals(1, createdTransports)
        assertEquals(
            SecureRelayAuthHandshakeResult(
                session = RelaySession(
                    username = "demo@yepanywhere",
                    relayUrl = "wss://relay.yepanywhere.local",
                    sessionId = "session-1",
                ),
                persistedSession = null,
                clearedStoredSession = false,
                transportNonce = "transport-nonce",
                resumed = true,
            ),
            result,
        )
        assertEquals(
            listOf(
                """{"type":"srp_resume_init","identity":"demo@yepanywhere","sessionId":"session-1"}""",
                """{"type":"srp_resume","identity":"demo@yepanywhere","sessionId":"session-1","proof":"{\"nonce\":\"resume-nonce\",\"ciphertext\":\"resume-ciphertext\"}"}""",
            ),
            socket.sentTexts,
        )
        assertTrue(socket.closed)
    }

    @Test
    fun runForwardsRelayUsernameIntoTransportFactory() = runTest {
        val socket = FakeSecureRelayAuthSocket(
            incomingTexts = ArrayDeque(
                listOf(
                    """{"type":"client_connected"}""",
                    """{"type":"srp_resume_challenge","sessionId":"session-1","nonce":"resume-challenge"}""",
                    """{"type":"srp_resumed","sessionId":"session-1","transportNonce":"transport-nonce"}""",
                ),
            ),
        )
        var capturedRelayUsername: String? = null
        val runner = KtorSecureRelayAuthHandshakeRunner(
            proofProvider = FakeSecureRelayProofProvider(
                resumeProof = """{"nonce":"resume-nonce","ciphertext":"resume-ciphertext"}""",
            ),
            transportFactory = { _, relayUsername ->
                capturedRelayUsername = relayUsername
                KtorSecureRelayAuthTransport(
                    relayUsername = relayUsername,
                    sessionFactory = { socket },
                )
            },
        )

        runner.run(
            relayUrl = "wss://relay.yepanywhere.local",
            relayUsername = "relay-user",
            identity = "demo@yepanywhere",
            password = "secret",
            storedSession = StoredRelaySession(
                wsUrl = "wss://relay.yepanywhere.local",
                username = "demo@yepanywhere",
                sessionId = "session-1",
                sessionKey = "stored-session-key",
            ),
        )

        assertEquals("relay-user", capturedRelayUsername)
        assertEquals(
            listOf(
                """{"type":"client_connect","username":"relay-user"}""",
                """{"type":"srp_resume_init","identity":"demo@yepanywhere","sessionId":"session-1"}""",
                """{"type":"srp_resume","identity":"demo@yepanywhere","sessionId":"session-1","proof":"{\"nonce\":\"resume-nonce\",\"ciphertext\":\"resume-ciphertext\"}"}""",
            ),
            socket.sentTexts,
        )
    }

    private class FakeSecureRelayProofProvider(
        private val resumeProof: String = "resume-proof",
    ) : SecureRelayProofProvider {
        override suspend fun generateResumeProof(
            storedSession: StoredRelaySession,
            challenge: SrpMessage.SessionResumeChallenge,
        ): String = resumeProof

        override suspend fun generateSrpProof(
            identity: String,
            password: String,
            challenge: SrpMessage.ServerChallenge,
        ): SecureRelayClientProof = error("generateSrpProof should not be used in this test")
    }

    private class FakeSecureRelayAuthSocket(
        private val incomingTexts: ArrayDeque<String> = ArrayDeque(),
    ) : SecureRelayAuthSocket {
        val sentTexts = mutableListOf<String>()
        var closed: Boolean = false

        override suspend fun sendText(text: String) {
            sentTexts += text
        }

        override suspend fun receiveText(): String {
            return incomingTexts.removeFirstOrNull() ?: error("No incoming auth payload")
        }

        override suspend fun close() {
            closed = true
        }
    }
}
