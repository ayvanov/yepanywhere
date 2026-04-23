package com.yepanywhere.android.core.usecase

import com.yepanywhere.android.core.model.SrpMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send

internal interface SecureRelayAuthSocket {
    suspend fun sendText(text: String)

    suspend fun receiveText(): String

    suspend fun close()
}

class KtorSecureRelayAuthTransport internal constructor(
    private val sessionFactory: suspend () -> SecureRelayAuthSocket,
) : SecureRelayAuthTransport {
    constructor(
        client: HttpClient,
        websocketUrl: String,
    ) : this(
        sessionFactory = {
            KtorClientSecureRelayAuthSocket(
                session = client.webSocketSession {
                    url(websocketUrl)
                },
            )
        },
    )

    private var activeSession: SecureRelayAuthSocket? = null

    override suspend fun send(message: SrpMessage) {
        session().sendText(message.encode())
    }

    override suspend fun receive(): SrpMessage {
        val payload = session().receiveText()
        return SrpMessage.decode(payload) ?: throw IllegalStateException("invalid_auth_message")
    }

    suspend fun close() {
        activeSession?.close()
        activeSession = null
    }

    private suspend fun session(): SecureRelayAuthSocket {
        val existing = activeSession
        if (existing != null) {
            return existing
        }

        return sessionFactory().also { created ->
            activeSession = created
        }
    }
}

private class KtorClientSecureRelayAuthSocket(
    private val session: DefaultClientWebSocketSession,
) : SecureRelayAuthSocket {
    override suspend fun sendText(text: String) {
        session.send(Frame.Text(text))
    }

    override suspend fun receiveText(): String {
        while (true) {
            when (val frame = session.incoming.receiveCatching().getOrNull()) {
                null -> throw IllegalStateException("auth_socket_closed")
                is Frame.Text -> return frame.readText()
                is Frame.Close -> throw IllegalStateException("auth_socket_closed")
                else -> Unit
            }
        }
    }

    override suspend fun close() {
        session.close()
    }
}
