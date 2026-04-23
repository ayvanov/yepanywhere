package com.yepanywhere.android.core.usecase

import com.yepanywhere.android.core.model.OriginMetadata
import com.yepanywhere.android.core.model.StoredRelaySession
import io.ktor.client.HttpClient

class KtorSecureRelayAuthHandshakeRunner(
    private val proofProvider: SecureRelayProofProvider,
    private val transportFactory: suspend (websocketUrl: String) -> KtorSecureRelayAuthTransport,
) {
    constructor(
        client: HttpClient,
        proofProvider: SecureRelayProofProvider,
    ) : this(
        proofProvider = proofProvider,
        transportFactory = { websocketUrl ->
            KtorSecureRelayAuthTransport(
                client = client,
                websocketUrl = websocketUrl,
            )
        },
    )

    suspend fun run(
        relayUrl: String,
        identity: String,
        password: String?,
        storedSession: StoredRelaySession? = null,
        browserProfileId: String? = null,
        originMetadata: OriginMetadata? = null,
    ): SecureRelayAuthHandshakeResult {
        val transport = transportFactory(relayUrl)
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
