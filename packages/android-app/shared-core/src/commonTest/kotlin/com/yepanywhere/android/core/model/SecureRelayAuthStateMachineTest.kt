package com.yepanywhere.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecureRelayAuthStateMachineTest {
    private val storedSession = StoredRelaySession(
        wsUrl = "wss://relay.example/socket",
        username = "alice",
        sessionId = "session-1",
        sessionKey = "base64-key",
    )

    @Test
    fun storedSessionStartsWithResumeInit() {
        val machine = SecureRelayAuthStateMachine(
            identity = "alice",
            password = "secret",
            storedSession = storedSession,
        )

        val step = machine.start()

        assertEquals(SecureRelayAuthPhase.RESUME_INIT_SENT, machine.phase)
        assertEquals(
            SecureRelayAuthEffect.Send(
                SrpMessage.SessionResumeInit(
                    identity = "alice",
                    sessionId = "session-1",
                ),
            ),
            step.effect,
        )
    }

    @Test
    fun invalidResumeFallsBackToFullSrpWhenPasswordIsAvailable() {
        val machine = SecureRelayAuthStateMachine(
            identity = "alice",
            password = "secret",
            storedSession = storedSession,
        )
        machine.start()

        val step = machine.onServerMessage(
            SrpMessage.SessionInvalid(reason = SrpSessionInvalidReason.EXPIRED),
        )

        assertEquals(SecureRelayAuthPhase.SRP_HELLO_SENT, machine.phase)
        assertEquals(
            SecureRelayAuthEffect.Send(SrpMessage.ClientHello(identity = "alice")),
            step.effect,
        )
    }

    @Test
    fun invalidResumeFailsWithoutPasswordFallback() {
        val machine = SecureRelayAuthStateMachine(
            identity = "alice",
            password = null,
            storedSession = storedSession,
        )
        machine.start()

        val step = machine.onServerMessage(
            SrpMessage.SessionInvalid(reason = SrpSessionInvalidReason.EXPIRED),
        )

        assertEquals(SecureRelayAuthPhase.FAILED, machine.phase)
        assertEquals(
            SecureRelayAuthEffect.Failed("session_invalid: expired"),
            step.effect,
        )
    }

    @Test
    fun resumeChallengeRequestsProofAndResumeSuccessAuthenticates() {
        val machine = SecureRelayAuthStateMachine(
            identity = "alice",
            password = "secret",
            storedSession = storedSession,
        )
        machine.start()

        val challengeStep = machine.onServerMessage(
            SrpMessage.SessionResumeChallenge(
                sessionId = "session-1",
                nonce = "nonce-base64",
            ),
        )

        assertEquals(SecureRelayAuthPhase.AWAITING_RESUME_PROOF, machine.phase)
        assertEquals(
            SecureRelayAuthEffect.RequestResumeProof(
                SrpMessage.SessionResumeChallenge(
                    sessionId = "session-1",
                    nonce = "nonce-base64",
                ),
            ),
            challengeStep.effect,
        )

        val proofStep = machine.submitResumeProof("proof-payload")
        assertEquals(SecureRelayAuthPhase.RESUME_PROOF_SENT, machine.phase)
        assertEquals(
            SecureRelayAuthEffect.Send(
                SrpMessage.SessionResume(
                    identity = "alice",
                    sessionId = "session-1",
                    proof = "proof-payload",
                ),
            ),
            proofStep.effect,
        )

        val resumedStep = machine.onServerMessage(
            SrpMessage.SessionResumed(
                sessionId = "session-1",
                transportNonce = "transport-nonce",
            ),
        )
        assertEquals(SecureRelayAuthPhase.AUTHENTICATED, machine.phase)
        assertEquals(
            SecureRelayAuthEffect.Authenticated(
                sessionId = "session-1",
                transportNonce = "transport-nonce",
                resumed = true,
            ),
            resumedStep.effect,
        )
    }

    @Test
    fun serverChallengeRequestsClientProofAndVerifyAuthenticates() {
        val machine = SecureRelayAuthStateMachine(
            identity = "alice",
            password = "secret",
            storedSession = null,
        )

        val startStep = machine.start()
        assertEquals(
            SecureRelayAuthEffect.Send(SrpMessage.ClientHello(identity = "alice")),
            startStep.effect,
        )

        val challengeStep = machine.onServerMessage(
            SrpMessage.ServerChallenge(
                salt = "salt-hex",
                B = "server-public",
            ),
        )
        assertEquals(SecureRelayAuthPhase.AWAITING_SRP_PROOF, machine.phase)
        assertEquals(
            SecureRelayAuthEffect.RequestSrpProof(
                SrpMessage.ServerChallenge(
                    salt = "salt-hex",
                    B = "server-public",
                ),
            ),
            challengeStep.effect,
        )

        val proofStep = machine.submitClientProof(
            A = "client-public",
            M1 = "client-proof",
        )
        assertEquals(SecureRelayAuthPhase.SRP_PROOF_SENT, machine.phase)
        assertEquals(
            SecureRelayAuthEffect.Send(
                SrpMessage.ClientProof(
                    A = "client-public",
                    M1 = "client-proof",
                ),
            ),
            proofStep.effect,
        )

        val verifyStep = machine.onServerMessage(
            SrpMessage.ServerVerify(
                M2 = "server-proof",
                sessionId = "session-2",
                transportNonce = "transport-nonce",
            ),
        )
        assertEquals(SecureRelayAuthPhase.AUTHENTICATED, machine.phase)
        assertEquals(
            SecureRelayAuthEffect.Authenticated(
                sessionId = "session-2",
                transportNonce = "transport-nonce",
                resumed = false,
            ),
            verifyStep.effect,
        )
    }

    @Test
    fun unexpectedMessageFailsTheMachine() {
        val machine = SecureRelayAuthStateMachine(
            identity = "alice",
            password = "secret",
            storedSession = null,
        )
        machine.start()

        val step = machine.onServerMessage(
            SrpMessage.SessionResumed(sessionId = "session-1"),
        )

        assertEquals(SecureRelayAuthPhase.FAILED, machine.phase)
        assertTrue(step.effect is SecureRelayAuthEffect.Failed)
    }
}
