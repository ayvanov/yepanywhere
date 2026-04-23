package com.yepanywhere.android.core.usecase

import com.yepanywhere.android.core.model.OriginMetadata
import com.yepanywhere.android.core.model.RelaySession
import com.yepanywhere.android.core.model.SrpMessage
import com.yepanywhere.android.core.model.StoredRelaySession

interface SecureRelayAuthTransport {
    suspend fun send(message: SrpMessage)

    suspend fun receive(): SrpMessage
}

data class SecureRelayAuthHandshakeResult(
    val session: RelaySession,
    val persistedSession: StoredRelaySession?,
    val clearedStoredSession: Boolean,
    val transportNonce: String?,
    val resumed: Boolean,
)

class SecureRelayAuthException(
    reason: String,
) : IllegalStateException(reason)

class ExecuteSecureRelayAuthHandshakeUseCase(
    private val transport: SecureRelayAuthTransport,
    private val proofProvider: SecureRelayProofProvider,
) {
    suspend operator fun invoke(
        relayUrl: String,
        identity: String,
        password: String?,
        storedSession: StoredRelaySession? = null,
        browserProfileId: String? = null,
        originMetadata: OriginMetadata? = null,
    ): SecureRelayAuthHandshakeResult {
        val coordinator = SecureRelayAuthCoordinator(
            relayUrl = relayUrl,
            identity = identity,
            password = password,
            storedSession = storedSession,
            proofProvider = proofProvider,
            browserProfileId = browserProfileId,
            originMetadata = originMetadata,
        )
        var persistedSession: StoredRelaySession? = null
        var clearedStoredSession = false
        var pendingActions = coordinator.start()

        while (true) {
            val result = applyActions(
                actions = pendingActions,
                onPersistedSession = { session -> persistedSession = session },
                onClearedStoredSession = { clearedStoredSession = true },
            )
            if (result != null) {
                return result.copy(
                    persistedSession = persistedSession,
                    clearedStoredSession = clearedStoredSession,
                )
            }

            pendingActions = coordinator.onServerMessage(transport.receive())
        }
    }

    private suspend fun applyActions(
        actions: List<SecureRelayAuthAction>,
        onPersistedSession: (StoredRelaySession) -> Unit,
        onClearedStoredSession: () -> Unit,
    ): SecureRelayAuthHandshakeResult? {
        actions.forEach { action ->
            when (action) {
                is SecureRelayAuthAction.Send -> transport.send(action.message)
                is SecureRelayAuthAction.PersistStoredSession -> onPersistedSession(action.session)
                is SecureRelayAuthAction.ClearStoredSession -> onClearedStoredSession()
                is SecureRelayAuthAction.Authenticated -> {
                    return SecureRelayAuthHandshakeResult(
                        session = action.session,
                        persistedSession = null,
                        clearedStoredSession = false,
                        transportNonce = action.transportNonce,
                        resumed = action.resumed,
                    )
                }

                is SecureRelayAuthAction.Failed -> {
                    throw SecureRelayAuthException(action.reason)
                }
            }
        }

        return null
    }
}
