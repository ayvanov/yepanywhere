package com.yepanywhere.android.core.usecase

import com.yepanywhere.android.core.model.OriginMetadata
import com.yepanywhere.android.core.model.StoredRelaySession
import io.ktor.client.HttpClient

class KtorSecureRelayAuthHandshakeRunner(
    private val proofProvider: SecureRelayProofProvider,
    private val transportFactory: suspend (
        websocketUrl: String,
        relayUsername: String?,
    ) -> KtorSecureRelayAuthTransport,
) {
    constructor(
        client: HttpClient,
        proofProvider: SecureRelayProofProvider,
    ) : this(
        proofProvider = proofProvider,
        transportFactory = { websocketUrl, relayUsername ->
            KtorSecureRelayAuthTransport(
                client = client,
                websocketUrl = websocketUrl,
                relayUsername = relayUsername,
            )
        },
    )

    suspend fun run(
        relayUrl: String,
        relayUsername: String? = null,
        identity: String,
        password: String?,
        storedSession: StoredRelaySession? = null,
        browserProfileId: String? = null,
        originMetadata: OriginMetadata? = null,
    ): SecureRelayAuthHandshakeResult {
        val transport = transportFactory(relayUrl, relayUsername)
        return try {
            ExecuteSecureRelayAuthHandshakeUseCase(
                transport = transport,
                proofProvider = proofProvider,
            )(
                relayUrl = relayUrl,
                identity = identity,
                password = password,
                storedSession = storedSession,
                browserProfileId = browserProfileId,
                originMetadata = originMetadata,
            )
        } finally {
            transport.close()
        }
    }
}
