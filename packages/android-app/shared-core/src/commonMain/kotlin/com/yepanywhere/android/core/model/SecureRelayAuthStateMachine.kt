package com.yepanywhere.android.core.model

enum class SecureRelayAuthPhase {
    DISCONNECTED,
    RESUME_INIT_SENT,
    AWAITING_RESUME_PROOF,
    RESUME_PROOF_SENT,
    SRP_HELLO_SENT,
    AWAITING_SRP_PROOF,
    SRP_PROOF_SENT,
    AUTHENTICATED,
    FAILED,
}

sealed interface SecureRelayAuthEffect {
    data class Send(val message: SrpMessage) : SecureRelayAuthEffect

    data class RequestResumeProof(
        val challenge: SrpMessage.SessionResumeChallenge,
    ) : SecureRelayAuthEffect

    data class RequestSrpProof(
        val challenge: SrpMessage.ServerChallenge,
    ) : SecureRelayAuthEffect

    data class Authenticated(
        val sessionId: String?,
        val transportNonce: String?,
        val resumed: Boolean,
    ) : SecureRelayAuthEffect

    data class Failed(val reason: String) : SecureRelayAuthEffect
}

data class SecureRelayAuthStep(
    val effect: SecureRelayAuthEffect,
)

class SecureRelayAuthStateMachine(
    private val identity: String,
    private val password: String?,
    private val storedSession: StoredRelaySession?,
) {
    var phase: SecureRelayAuthPhase = SecureRelayAuthPhase.DISCONNECTED
        private set

    private var pendingResumeChallenge: SrpMessage.SessionResumeChallenge? = null
    private var pendingSrpChallenge: SrpMessage.ServerChallenge? = null

    fun start(): SecureRelayAuthStep {
        return when {
            storedSession != null -> transition(
                SecureRelayAuthPhase.RESUME_INIT_SENT,
                SecureRelayAuthEffect.Send(
                    SrpMessage.SessionResumeInit(
                        identity = identity,
                        sessionId = storedSession.sessionId,
                    ),
                ),
            )

            password != null -> startFullSrp()
            else -> fail("missing_credentials")
        }
    }

    fun onServerMessage(message: SrpMessage): SecureRelayAuthStep {
        return when (phase) {
            SecureRelayAuthPhase.RESUME_INIT_SENT -> handleResumeInitPhase(message)
            SecureRelayAuthPhase.AWAITING_RESUME_PROOF -> fail("unexpected_message: ${message.type}")
            SecureRelayAuthPhase.RESUME_PROOF_SENT -> handleResumeProofPhase(message)
            SecureRelayAuthPhase.SRP_HELLO_SENT -> handleSrpHelloPhase(message)
            SecureRelayAuthPhase.AWAITING_SRP_PROOF -> fail("unexpected_message: ${message.type}")
            SecureRelayAuthPhase.SRP_PROOF_SENT -> handleSrpProofPhase(message)
            SecureRelayAuthPhase.AUTHENTICATED -> fail("unexpected_message: ${message.type}")
            SecureRelayAuthPhase.FAILED -> fail("unexpected_message: ${message.type}")
            SecureRelayAuthPhase.DISCONNECTED -> fail("unexpected_message: ${message.type}")
        }
    }

    fun submitResumeProof(proof: String): SecureRelayAuthStep {
        if (phase != SecureRelayAuthPhase.AWAITING_RESUME_PROOF || storedSession == null) {
            return fail("resume_proof_not_expected")
        }

        pendingResumeChallenge = null
        return transition(
            SecureRelayAuthPhase.RESUME_PROOF_SENT,
            SecureRelayAuthEffect.Send(
                SrpMessage.SessionResume(
                    identity = identity,
                    sessionId = storedSession.sessionId,
                    proof = proof,
                ),
            ),
        )
    }

    fun submitClientProof(
        A: String,
        M1: String,
    ): SecureRelayAuthStep {
        if (phase != SecureRelayAuthPhase.AWAITING_SRP_PROOF) {
            return fail("client_proof_not_expected")
        }

        pendingSrpChallenge = null
        return transition(
            SecureRelayAuthPhase.SRP_PROOF_SENT,
            SecureRelayAuthEffect.Send(
                SrpMessage.ClientProof(
                    A = A,
                    M1 = M1,
                ),
            ),
        )
    }

    private fun handleResumeInitPhase(message: SrpMessage): SecureRelayAuthStep {
        return when (message) {
            is SrpMessage.SessionResumeChallenge -> {
                if (storedSession == null || message.sessionId != storedSession.sessionId) {
                    fail("resume_session_mismatch")
                } else {
                    pendingResumeChallenge = message
                    transition(
                        SecureRelayAuthPhase.AWAITING_RESUME_PROOF,
                        SecureRelayAuthEffect.RequestResumeProof(message),
                    )
                }
            }

            is SrpMessage.SessionInvalid -> handleResumeInvalid(message)
            is SrpMessage.Error -> fail("auth_error: ${message.message}")
            else -> fail("unexpected_message: ${message.type}")
        }
    }

    private fun handleResumeProofPhase(message: SrpMessage): SecureRelayAuthStep {
        return when (message) {
            is SrpMessage.SessionResumed -> transition(
                SecureRelayAuthPhase.AUTHENTICATED,
                SecureRelayAuthEffect.Authenticated(
                    sessionId = message.sessionId,
                    transportNonce = message.transportNonce,
                    resumed = true,
                ),
            )

            is SrpMessage.SessionInvalid -> handleResumeInvalid(message)
            is SrpMessage.Error -> fail("auth_error: ${message.message}")
            else -> fail("unexpected_message: ${message.type}")
        }
    }

    private fun handleSrpHelloPhase(message: SrpMessage): SecureRelayAuthStep {
        return when (message) {
            is SrpMessage.ServerChallenge -> {
                pendingSrpChallenge = message
                transition(
                    SecureRelayAuthPhase.AWAITING_SRP_PROOF,
                    SecureRelayAuthEffect.RequestSrpProof(message),
                )
            }

            is SrpMessage.Error -> fail("auth_error: ${message.message}")
            else -> fail("unexpected_message: ${message.type}")
        }
    }

    private fun handleSrpProofPhase(message: SrpMessage): SecureRelayAuthStep {
        return when (message) {
            is SrpMessage.ServerVerify -> transition(
                SecureRelayAuthPhase.AUTHENTICATED,
                SecureRelayAuthEffect.Authenticated(
                    sessionId = message.sessionId,
                    transportNonce = message.transportNonce,
                    resumed = false,
                ),
            )

            is SrpMessage.Error -> fail("auth_error: ${message.message}")
            else -> fail("unexpected_message: ${message.type}")
        }
    }

    private fun handleResumeInvalid(message: SrpMessage.SessionInvalid): SecureRelayAuthStep {
        pendingResumeChallenge = null
        return if (password != null) {
            startFullSrp()
        } else {
            fail("session_invalid: ${message.reason.wireValue()}")
        }
    }

    private fun startFullSrp(): SecureRelayAuthStep {
        pendingSrpChallenge = null
        return transition(
            SecureRelayAuthPhase.SRP_HELLO_SENT,
            SecureRelayAuthEffect.Send(
                SrpMessage.ClientHello(identity = identity),
            ),
        )
    }

    private fun transition(
        nextPhase: SecureRelayAuthPhase,
        effect: SecureRelayAuthEffect,
    ): SecureRelayAuthStep {
        phase = nextPhase
        return SecureRelayAuthStep(effect = effect)
    }

    private fun fail(reason: String): SecureRelayAuthStep {
        phase = SecureRelayAuthPhase.FAILED
        return SecureRelayAuthStep(
            effect = SecureRelayAuthEffect.Failed(reason),
        )
    }
}

private fun SrpSessionInvalidReason.wireValue(): String {
    return when (this) {
        SrpSessionInvalidReason.EXPIRED -> "expired"
        SrpSessionInvalidReason.UNKNOWN -> "unknown"
        SrpSessionInvalidReason.INVALID_PROOF -> "invalid_proof"
    }
}
