package com.yepanywhere.android

import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.core.usecase.KtorSecureRelayAuthHandshakeRunner
import com.yepanywhere.android.core.usecase.SecureRelayAuthHandshakeResult
import com.yepanywhere.android.core.usecase.SecureRelayProofProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets

class AndroidKtorRelayAuthRunner(
    private val handshakeRunner: KtorSecureRelayAuthHandshakeRunner,
) : AndroidRelayAuthRunner {
    override suspend fun run(
        relayUrl: String,
        relayUsername: String?,
        identity: String,
        password: String?,
        storedSession: StoredRelaySession?,
    ): SecureRelayAuthHandshakeResult {
        return handshakeRunner.run(
            relayUrl = relayUrl,
            relayUsername = relayUsername,
            identity = identity,
            password = password,
            storedSession = storedSession,
        )
    }

    companion object {
        fun createDefault(
            proofProvider: SecureRelayProofProvider = AndroidSecureRelayProofProvider(),
        ): AndroidKtorRelayAuthRunner {
            return AndroidKtorRelayAuthRunner(
                handshakeRunner = KtorSecureRelayAuthHandshakeRunner(
                    client = createHttpClient(),
                    proofProvider = proofProvider,
                ),
            )
        }

        private fun createHttpClient(): HttpClient {
            return HttpClient(OkHttp) {
                install(WebSockets)
            }
        }
    }
}
