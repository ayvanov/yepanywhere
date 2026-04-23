package com.yepanywhere.android.core.usecase

import com.yepanywhere.android.core.model.OriginMetadata
import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.SecureRelayAuthEffect
import com.yepanywhere.android.core.model.SecureRelayAuthStateMachine
import com.yepanywhere.android.core.model.SecureRelayAuthStep
import com.yepanywhere.android.core.model.SrpMessage
import com.yepanywhere.android.core.model.SrpSessionInvalidReason
import com.yepanywhere.android.core.model.StoredRelaySession

data class SecureRelayClientProof(
    val A: String,
    val M1: String,
    val sessionKey: String,
)

interface SecureRelayProofProvider {
    suspend fun generateResumeProof(
        storedSession: StoredRelaySession,
        challenge: SrpMessage.SessionResumeChallenge,
    ): String

    suspend fun generateSrpProof(
        identity: String,
        password: String,
        challenge: SrpMessage.ServerChallenge,
    ): SecureRelayClientProof
}

sealed interface SecureRelayAuthAction {
    data class Send(val message: SrpMessage) : SecureRelayAuthAction

    data class PersistStoredSession(
        val session: StoredRelaySession,
    ) : SecureRelayAuthAction

    data class ClearStoredSession(
        val reason: String,
    ) : SecureRelayAuthAction

    data class Authenticated(
        val session: RelaySession,
        val transportNonce: String?,
        val resumed: Boolean,
    ) : SecureRelayAuthAction

    data class Failed(
        val reason: String,
    ) : SecureRelayAuthAction
}

class SecureRelayAuthCoordinator(
    private val relayUrl: String,
    private val identity: String,
    private val password: String?,
    storedSession: StoredRelaySession?,
    private val proofProvider: SecureRelayProofProvider,
    private val browserProfileId: String? = null,
    private val originMetadata: OriginMetadata? = null,
) {
    private val machine = SecureRelayAuthStateMachine(
        identity = identity,
        password = password,
        storedSession = storedSession,
    )
    private var activeStoredSession: StoredRelaySession? = storedSession
    private var pendingSrpSessionKey: String? = null

    suspend fun start(): List<SecureRelayAuthAction> = resolve(machine.start())

    suspend fun onServerMessage(message: SrpMessage): List<SecureRelayAuthAction> {
        val actions = mutableListOf<SecureRelayAuthAction>()
        if (message is SrpMessage.SessionInvalid && activeStoredSession != null) {
            activeStoredSession = null
            actions += SecureRelayAuthAction.ClearStoredSession(
                reason = "session_invalid: ${message.reason.wireValue()}",
            )
        }

        actions += resolve(machine.onServerMessage(message))
        return actions
    }

    private suspend fun resolve(step: SecureRelayAuthStep): List<SecureRelayAuthAction> {
        return when (val effect = step.effect) {
            is SecureRelayAuthEffect.Send -> {
                listOf(
                    SecureRelayAuthAction.Send(
                        message = enrichOutgoingMessage(effect.message),
                    ),
                )
            }

            is SecureRelayAuthEffect.RequestResumeProof -> resolveResumeProof(effect.challenge)
            is SecureRelayAuthEffect.RequestSrpProof -> resolveSrpProof(effect.challenge)
            is SecureRelayAuthEffect.Authenticated -> resolveAuthenticated(effect)
            is SecureRelayAuthEffect.Failed -> {
                pendingSrpSessionKey = null
                listOf(SecureRelayAuthAction.Failed(effect.reason))
            }
        }
    }

    private suspend fun resolveResumeProof(
        challenge: SrpMessage.SessionResumeChallenge,
    ): List<SecureRelayAuthAction> {
        val session = activeStoredSession
            ?: return listOf(SecureRelayAuthAction.Failed("missing_stored_session"))
        val proof = proofProvider.generateResumeProof(
            storedSession = session,
            challenge = challenge,
        )
        return resolve(machine.submitResumeProof(proof))
    }

    private suspend fun resolveSrpProof(
        challenge: SrpMessage.ServerChallenge,
    ): List<SecureRelayAuthAction> {
        val currentPassword = password
            ?: return listOf(SecureRelayAuthAction.Failed("missing_credentials"))
        val clientProof = proofProvider.generateSrpProof(
            identity = identity,
            password = currentPassword,
            challenge = challenge,
        )
        pendingSrpSessionKey = clientProof.sessionKey
        return resolve(
            machine.submitClientProof(
                A = clientProof.A,
                M1 = clientProof.M1,
            ),
        )
    }

    private fun resolveAuthenticated(
        effect: SecureRelayAuthEffect.Authenticated,
    ): List<SecureRelayAuthAction> {
        val actions = mutableListOf<SecureRelayAuthAction>()
        if (!effect.resumed) {
            val sessionId = effect.sessionId
            val sessionKey = pendingSrpSessionKey
            if (sessionId != null && sessionKey != null) {
                val session = StoredRelaySession(
                    wsUrl = relayUrl,
                    username = identity,
                    sessionId = sessionId,
                    sessionKey = sessionKey,
                )
                activeStoredSession = session
                actions += SecureRelayAuthAction.PersistStoredSession(session)
            }
            pendingSrpSessionKey = null
        }

        actions += SecureRelayAuthAction.Authenticated(
            session = RelaySession(
                username = identity,
                relayUrl = relayUrl,
                sessionId = effect.sessionId,
            ),
            transportNonce = effect.transportNonce,
            resumed = effect.resumed,
        )
        return actions
    }

    private fun enrichOutgoingMessage(message: SrpMessage): SrpMessage {
        return when (message) {
            is SrpMessage.ClientHello -> message.copy(
                browserProfileId = browserProfileId,
                originMetadata = originMetadata,
            )

            else -> message
        }
    }
}

private fun SrpSessionInvalidReason.wireValue(): String {
    return when (this) {
        SrpSessionInvalidReason.EXPIRED -> "expired"
        SrpSessionInvalidReason.UNKNOWN -> "unknown"
        SrpSessionInvalidReason.INVALID_PROOF -> "invalid_proof"
    }
}
