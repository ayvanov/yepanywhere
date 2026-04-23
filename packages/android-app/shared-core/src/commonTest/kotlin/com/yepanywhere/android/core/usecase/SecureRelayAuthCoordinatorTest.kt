package com.yepanywhere.android.core.usecase

import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.SrpMessage
import com.yepanywhere.android.core.model.SrpSessionInvalidReason
import com.yepanywhere.android.core.model.StoredRelaySession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SecureRelayAuthCoordinatorTest {
    private val relayUrl = "wss://relay.yepanywhere.local"
    private val username = "demo@yepanywhere"
    private val storedSession = StoredRelaySession(
        wsUrl = relayUrl,
        username = username,
        sessionId = "session-1",
        sessionKey = "base64-session-key",
    )

    @Test
    fun resumeChallengeGeneratesResumeProofAndSendsResumeMessage() = runTest {
        val proofProvider = FakeSecureRelayProofProvider(
            resumeProof = "{\"nonce\":\"resume-nonce\",\"ciphertext\":\"resume-ciphertext\"}",
        )
        val coordinator = SecureRelayAuthCoordinator(
            relayUrl = relayUrl,
            identity = username,
            password = "secret",
            storedSession = storedSession,
            proofProvider = proofProvider,
        )

        assertEquals(
            listOf(
                SecureRelayAuthAction.Send(
                    SrpMessage.SessionResumeInit(
                        identity = username,
                        sessionId = "session-1",
                    ),
                ),
            ),
            coordinator.start(),
        )

        assertEquals(
            listOf(
                SecureRelayAuthAction.Send(
                    SrpMessage.SessionResume(
                        identity = username,
                        sessionId = "session-1",
                        proof = "{\"nonce\":\"resume-nonce\",\"ciphertext\":\"resume-ciphertext\"}",
                    ),
                ),
            ),
            coordinator.onServerMessage(
                SrpMessage.SessionResumeChallenge(
                    sessionId = "session-1",
                    nonce = "resume-challenge",
                ),
            ),
        )
        assertEquals(
            listOf("session-1|resume-challenge"),
            proofProvider.resumeProofRequests,
        )
    }

    @Test
    fun sessionInvalidClearsStoredSessionAndFallsBackToClientHello() = runTest {
        val coordinator = SecureRelayAuthCoordinator(
            relayUrl = relayUrl,
            identity = username,
            password = "secret",
            storedSession = storedSession,
            proofProvider = FakeSecureRelayProofProvider(),
        )

        coordinator.start()

        assertEquals(
            listOf(
                SecureRelayAuthAction.ClearStoredSession("session_invalid: expired"),
                SecureRelayAuthAction.Send(
                    SrpMessage.ClientHello(identity = username),
                ),
            ),
            coordinator.onServerMessage(
                SrpMessage.SessionInvalid(reason = SrpSessionInvalidReason.EXPIRED),
            ),
        )
    }

    @Test
    fun serverVerifyPersistsStoredSessionAndAuthenticatesFullSrp() = runTest {
        val proofProvider = FakeSecureRelayProofProvider(
            srpProof = SecureRelayClientProof(
                A = "client-public",
                M1 = "client-proof",
                sessionKey = "new-base64-session-key",
            ),
        )
        val coordinator = SecureRelayAuthCoordinator(
            relayUrl = relayUrl,
            identity = username,
            password = "secret",
            storedSession = null,
            proofProvider = proofProvider,
        )

        assertEquals(
            listOf(
                SecureRelayAuthAction.Send(
                    SrpMessage.ClientHello(identity = username),
                ),
            ),
            coordinator.start(),
        )

        assertEquals(
            listOf(
                SecureRelayAuthAction.Send(
                    SrpMessage.ClientProof(
                        A = "client-public",
                        M1 = "client-proof",
                    ),
                ),
            ),
            coordinator.onServerMessage(
                SrpMessage.ServerChallenge(
                    salt = "salt-value",
                    B = "server-public",
                ),
            ),
        )
        assertEquals(
            listOf("demo@yepanywhere|salt-value|server-public"),
            proofProvider.srpProofRequests,
        )

        assertEquals(
            listOf(
                SecureRelayAuthAction.PersistStoredSession(
                    StoredRelaySession(
                        wsUrl = relayUrl,
                        username = username,
                        sessionId = "session-2",
                        sessionKey = "new-base64-session-key",
                    ),
                ),
                SecureRelayAuthAction.Authenticated(
                    session = RelaySession(
                        username = username,
                        relayUrl = relayUrl,
                        sessionId = "session-2",
                    ),
                    transportNonce = "transport-nonce",
                    resumed = false,
                ),
            ),
            coordinator.onServerMessage(
                SrpMessage.ServerVerify(
                    M2 = "server-verify",
                    sessionId = "session-2",
                    transportNonce = "transport-nonce",
                ),
            ),
        )
    }

    @Test
    fun sessionResumedAuthenticatesWithoutPersistingNewSession() = runTest {
        val coordinator = SecureRelayAuthCoordinator(
            relayUrl = relayUrl,
            identity = username,
            password = "secret",
            storedSession = storedSession,
            proofProvider = FakeSecureRelayProofProvider(
                resumeProof = "{\"nonce\":\"resume-nonce\",\"ciphertext\":\"resume-ciphertext\"}",
            ),
        )

        coordinator.start()
        coordinator.onServerMessage(
            SrpMessage.SessionResumeChallenge(
                sessionId = "session-1",
                nonce = "resume-challenge",
            ),
        )

        assertEquals(
            listOf(
                SecureRelayAuthAction.Authenticated(
                    session = RelaySession(
                        username = username,
                        relayUrl = relayUrl,
                        sessionId = "session-1",
                    ),
                    transportNonce = "transport-nonce",
                    resumed = true,
                ),
            ),
            coordinator.onServerMessage(
                SrpMessage.SessionResumed(
                    sessionId = "session-1",
                    transportNonce = "transport-nonce",
                ),
            ),
        )
    }

    @Test
    fun sessionInvalidWithoutPasswordClearsStoredSessionAndFails() = runTest {
        val coordinator = SecureRelayAuthCoordinator(
            relayUrl = relayUrl,
            identity = username,
            password = null,
            storedSession = storedSession,
            proofProvider = FakeSecureRelayProofProvider(),
        )

        coordinator.start()

        assertEquals(
            listOf(
                SecureRelayAuthAction.ClearStoredSession("session_invalid: invalid_proof"),
                SecureRelayAuthAction.Failed("session_invalid: invalid_proof"),
            ),
            coordinator.onServerMessage(
                SrpMessage.SessionInvalid(reason = SrpSessionInvalidReason.INVALID_PROOF),
            ),
        )
    }

    private class FakeSecureRelayProofProvider(
        private val resumeProof: String = "resume-proof",
        private val srpProof: SecureRelayClientProof = SecureRelayClientProof(
            A = "A",
            M1 = "M1",
            sessionKey = "session-key",
        ),
    ) : SecureRelayProofProvider {
        val resumeProofRequests = mutableListOf<String>()
        val srpProofRequests = mutableListOf<String>()

        override suspend fun generateResumeProof(
            storedSession: StoredRelaySession,
            challenge: SrpMessage.SessionResumeChallenge,
        ): String {
            resumeProofRequests += "${storedSession.sessionId}|${challenge.nonce}"
            return resumeProof
        }

        override suspend fun generateSrpProof(
            identity: String,
            password: String,
            challenge: SrpMessage.ServerChallenge,
        ): SecureRelayClientProof {
            srpProofRequests += "$identity|${challenge.salt}|${challenge.B}"
            return srpProof
        }
    }
}
