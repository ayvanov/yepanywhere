package com.yepanywhere.android.core.usecase

import com.yepanywhere.android.core.model.RelayClientErrorReason
import com.yepanywhere.android.core.model.RelayRoutingMessage
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
    private val relayUsername: String? = null,
    private val sessionFactory: suspend () -> SecureRelayAuthSocket,
) : SecureRelayAuthTransport {
    constructor(
        client: HttpClient,
        websocketUrl: String,
        relayUsername: String? = null,
    ) : this(
        relayUsername = relayUsername,
        sessionFactory = {
            KtorClientSecureRelayAuthSocket(
                session = client.webSocketSession {
                    url(websocketUrl)
                },
            )
        },
    )

    private var activeSession: SecureRelayAuthSocket? = null
    private var relayClientConnected: Boolean = relayUsername == null

    override suspend fun send(message: SrpMessage) {
        ensureRelayClientConnected()
        session().sendText(message.encode())
    }

    override suspend fun receive(): SrpMessage {
        ensureRelayClientConnected()
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

    private suspend fun ensureRelayClientConnected() {
        if (relayClientConnected) {
            return
        }

        val username = relayUsername ?: return
        val socket = session()
        socket.sendText(
            RelayRoutingMessage.ClientConnect(username = username).encode(),
        )

        while (true) {
            val payload = socket.receiveText()
            when (val message = RelayRoutingMessage.decode(payload)) {
                is RelayRoutingMessage.ClientConnected -> {
                    relayClientConnected = true
                    return
                }

                is RelayRoutingMessage.ClientError -> {
                    throw IllegalStateException(
                        "relay_client_error: ${message.reason.wireValue()}",
                    )
                }

                else -> {
                    throw IllegalStateException("unexpected_relay_routing_message")
                }
            }
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

private fun RelayClientErrorReason.wireValue(): String {
    return when (this) {
        RelayClientErrorReason.SERVER_OFFLINE -> "server_offline"
        RelayClientErrorReason.UNKNOWN_USERNAME -> "unknown_username"
    }
}
