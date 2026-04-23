package com.yepanywhere.android.core.usecase

import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.SrpMessage
import com.yepanywhere.android.core.model.SrpSessionInvalidReason
import com.yepanywhere.android.core.model.StoredRelaySession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExecuteSecureRelayAuthHandshakeUseCaseTest {
    private val relayUrl = "wss://relay.yepanywhere.local"
    private val username = "demo@yepanywhere"
    private val storedSession = StoredRelaySession(
        wsUrl = relayUrl,
        username = username,
        sessionId = "session-1",
        sessionKey = "base64-session-key",
    )

    @Test
    fun resumeHandshakeReturnsAuthenticatedResult() = runTest {
        val transport = FakeSecureRelayAuthTransport(
            incomingMessages = listOf(
                SrpMessage.SessionResumeChallenge(
                    sessionId = "session-1",
                    nonce = "resume-challenge",
                ),
                SrpMessage.SessionResumed(
                    sessionId = "session-1",
                    transportNonce = "transport-nonce",
                ),
            ),
        )
        val proofProvider = FakeSecureRelayProofProvider(
            resumeProof = "{\"nonce\":\"resume-nonce\",\"ciphertext\":\"resume-ciphertext\"}",
        )
        val useCase = ExecuteSecureRelayAuthHandshakeUseCase(
            transport = transport,
            proofProvider = proofProvider,
        )

        assertEquals(
            SecureRelayAuthHandshakeResult(
                session = RelaySession(
                    username = username,
                    relayUrl = relayUrl,
                    sessionId = "session-1",
                ),
                persistedSession = null,
                clearedStoredSession = false,
                transportNonce = "transport-nonce",
                resumed = true,
            ),
            useCase(
                relayUrl = relayUrl,
                identity = username,
                password = "secret",
                storedSession = storedSession,
            ),
        )
        assertEquals(
            listOf(
                SrpMessage.SessionResumeInit(
                    identity = username,
                    sessionId = "session-1",
                ),
                SrpMessage.SessionResume(
                    identity = username,
                    sessionId = "session-1",
                    proof = "{\"nonce\":\"resume-nonce\",\"ciphertext\":\"resume-ciphertext\"}",
                ),
            ),
            transport.sentMessages,
        )
    }

    @Test
    fun invalidResumeFallsBackToFullHandshakeAndPersistsFreshSession() = runTest {
        val transport = FakeSecureRelayAuthTransport(
            incomingMessages = listOf(
                SrpMessage.SessionInvalid(reason = SrpSessionInvalidReason.EXPIRED),
                SrpMessage.ServerChallenge(
                    salt = "salt-value",
                    B = "server-public",
                ),
                SrpMessage.ServerVerify(
                    M2 = "server-verify",
                    sessionId = "session-2",
                    transportNonce = "transport-nonce",
                ),
            ),
        )
        val proofProvider = FakeSecureRelayProofProvider(
            srpProof = SecureRelayClientProof(
                A = "client-public",
                M1 = "client-proof",
                sessionKey = "new-base64-session-key",
            ),
        )
        val useCase = ExecuteSecureRelayAuthHandshakeUseCase(
            transport = transport,
            proofProvider = proofProvider,
        )

        assertEquals(
            SecureRelayAuthHandshakeResult(
                session = RelaySession(
                    username = username,
                    relayUrl = relayUrl,
                    sessionId = "session-2",
                ),
                persistedSession = StoredRelaySession(
                    wsUrl = relayUrl,
                    username = username,
                    sessionId = "session-2",
                    sessionKey = "new-base64-session-key",
                ),
                clearedStoredSession = true,
                transportNonce = "transport-nonce",
                resumed = false,
            ),
            useCase(
                relayUrl = relayUrl,
                identity = username,
                password = "secret",
                storedSession = storedSession,
            ),
        )
        assertEquals(
            listOf(
                SrpMessage.SessionResumeInit(
                    identity = username,
                    sessionId = "session-1",
                ),
                SrpMessage.ClientHello(identity = username),
                SrpMessage.ClientProof(
                    A = "client-public",
                    M1 = "client-proof",
                ),
            ),
            transport.sentMessages,
        )
    }

    @Test
    fun invalidResumeWithoutPasswordFailsHandshake() = runTest {
        val transport = FakeSecureRelayAuthTransport(
            incomingMessages = listOf(
                SrpMessage.SessionInvalid(reason = SrpSessionInvalidReason.UNKNOWN),
            ),
        )
        val useCase = ExecuteSecureRelayAuthHandshakeUseCase(
            transport = transport,
            proofProvider = FakeSecureRelayProofProvider(),
        )

        val error = assertFailsWith<SecureRelayAuthException> {
            useCase(
                relayUrl = relayUrl,
                identity = username,
                password = null,
                storedSession = storedSession,
            )
        }

        assertEquals("session_invalid: unknown", error.message)
        assertEquals(
            listOf<SrpMessage>(
                SrpMessage.SessionResumeInit(
                    identity = username,
                    sessionId = "session-1",
                ),
            ),
            transport.sentMessages,
        )
    }

    private class FakeSecureRelayAuthTransport(
        incomingMessages: List<SrpMessage>,
    ) : SecureRelayAuthTransport {
        private val queue = ArrayDeque(incomingMessages)
        val sentMessages = mutableListOf<SrpMessage>()

        override suspend fun send(message: SrpMessage) {
            sentMessages += message
        }

        override suspend fun receive(): SrpMessage {
            return queue.removeFirstOrNull() ?: error("No more incoming auth messages")
        }
    }

    private class FakeSecureRelayProofProvider(
        private val resumeProof: String = "resume-proof",
        private val srpProof: SecureRelayClientProof = SecureRelayClientProof(
            A = "A",
            M1 = "M1",
            sessionKey = "session-key",
        ),
    ) : SecureRelayProofProvider {
        override suspend fun generateResumeProof(
            storedSession: StoredRelaySession,
            challenge: SrpMessage.SessionResumeChallenge,
        ): String = resumeProof

        override suspend fun generateSrpProof(
            identity: String,
            password: String,
            challenge: SrpMessage.ServerChallenge,
        ): SecureRelayClientProof = srpProof
    }
}
