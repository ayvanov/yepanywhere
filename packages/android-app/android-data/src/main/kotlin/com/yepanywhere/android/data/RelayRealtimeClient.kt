package com.yepanywhere.android.data

import com.iwebpp.crypto.TweetNaclFast
import com.yepanywhere.android.core.model.RelayConnectionStatus
import com.yepanywhere.android.core.model.RelayRoutingMessage
import com.yepanywhere.android.core.model.SrpMessage
import com.yepanywhere.android.core.model.StoredRelaySession
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val SECRETBOX_KEY_LENGTH = 32
private const val SECRETBOX_NONCE_LENGTH = 24
private const val REQUEST_TIMEOUT_MS = 30_000L
internal const val RELAY_REALTIME_CONNECT_TIMEOUT_MS = 60_000L
internal const val RELAY_REALTIME_CONNECT_TIMEOUT_ERROR_MESSAGE = "Login timed out after 60 seconds"
private const val TRANSPORT_KEY_LABEL = "yep-transport-v1"

internal class RelayApiException(
    val status: Int,
    message: String,
) : IllegalStateException(message)

data class RelayRealtimeEvent(
    val subscriptionId: String,
    val eventType: String,
    val eventId: String?,
    val data: JsonElement?,
)

interface RelayRealtimeGateway {
    val connectionState: Flow<RelayConnectionStatus>

    fun events(): Flow<RelayRealtimeEvent>

    suspend fun connect(
        relayUrl: String,
        storedSession: StoredRelaySession,
        routingUsername: String? = null,
    )

    suspend fun disconnect()

    suspend fun ensureConnected()

    suspend fun request(
        method: String,
        path: String,
        body: JsonElement?,
    ): JsonElement?

    suspend fun subscribeSession(sessionId: String): String

    suspend fun subscribeActivity(): String

    suspend fun unsubscribe(subscriptionId: String)
}

@Serializable
private data class RelayRequestMessage(
    val type: String = "request",
    val id: String,
    val method: String,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val body: JsonElement? = null,
)

@Serializable
private data class RelayResponseMessage(
    val type: String = "response",
    val id: String,
    val status: Int,
    val headers: Map<String, String>? = null,
    val body: JsonElement? = null,
)

@Serializable
private data class RelaySubscribeMessage(
    val type: String = "subscribe",
    val subscriptionId: String,
    val channel: String,
    val sessionId: String? = null,
)

@Serializable
private data class RelayUnsubscribeMessage(
    val type: String = "unsubscribe",
    val subscriptionId: String,
)

@Serializable
private data class RelayEventMessage(
    val type: String = "event",
    val subscriptionId: String,
    val eventType: String,
    val eventId: String? = null,
    val data: JsonElement? = null,
)

@Serializable
private data class EncryptedEnvelopeMessage(
    val type: String = "encrypted",
    val nonce: String,
    val ciphertext: String,
)

internal class RelayRealtimeClient(
    private val scope: CoroutineScope,
    private val httpClient: HttpClient,
    private val secureRandom: SecureRandom = SecureRandom(),
) : RelayRealtimeGateway {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val connectionStateMutable = MutableStateFlow(RelayConnectionStatus.DISCONNECTED)
    private val eventsMutable = MutableSharedFlow<RelayRealtimeEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<RelayResponseMessage>>()
    private val pendingMutex = Mutex()
    private val sendMutex = Mutex()
    private var socket: DefaultClientWebSocketSession? = null
    private var readerJob: Job? = null
    private var encryptionKey: ByteArray? = null
    private var nextOutboundSeq: Long = 0
    private var lastInboundSeq: Long? = null
    private var lastStoredSession: StoredRelaySession? = null
    private var lastRelayUrl: String? = null

    override val connectionState = connectionStateMutable.asStateFlow()

    override fun events(): Flow<RelayRealtimeEvent> = eventsMutable.asSharedFlow()

    override suspend fun connect(
        relayUrl: String,
        storedSession: StoredRelaySession,
        routingUsername: String?,
    ) {
        disconnect()
        connectionStateMutable.value = RelayConnectionStatus.CONNECTING
        try {
            withTimeout(RELAY_REALTIME_CONNECT_TIMEOUT_MS) {
                connectInternal(
                    relayUrl = relayUrl,
                    storedSession = storedSession,
                    routingUsername = routingUsername,
                )
            }
        } catch (_: TimeoutCancellationException) {
            disconnect()
            throw IllegalStateException(RELAY_REALTIME_CONNECT_TIMEOUT_ERROR_MESSAGE)
        }
    }

    override suspend fun disconnect() {
        readerJob?.cancelAndJoin()
        readerJob = null
        socket?.close()
        socket = null
        encryptionKey = null
        nextOutboundSeq = 0
        lastInboundSeq = null
        lastStoredSession = null
        lastRelayUrl = null
        failPendingRequests("relay_socket_disconnected")
        connectionStateMutable.value = RelayConnectionStatus.DISCONNECTED
    }

    override suspend fun ensureConnected() {
        if (connectionStateMutable.value == RelayConnectionStatus.CONNECTED && socket != null && encryptionKey != null) {
            return
        }

        val stored = lastStoredSession ?: throw IllegalStateException("missing_stored_session")
        val relayUrl = lastRelayUrl ?: stored.wsUrl
        connect(relayUrl = relayUrl, storedSession = stored)
    }

    override suspend fun request(
        method: String,
        path: String,
        body: JsonElement?,
    ): JsonElement? {
        ensureConnected()
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<RelayResponseMessage>()
        pendingMutex.withLock {
            pendingRequests[requestId] = deferred
        }

        runCatching {
            sendEncryptedMessage(
                json.encodeToJsonElement(
                    RelayRequestMessage(
                        id = requestId,
                        method = method,
                        path = if (path.startsWith("/api")) path else "/api$path",
                        headers = mapOf(
                            "Content-Type" to "application/json",
                            "X-Yep-Anywhere" to "true",
                        ),
                        body = body,
                    ),
                ),
            )
        }.onFailure { error ->
            pendingMutex.withLock {
                pendingRequests.remove(requestId)
            }
            throw error
        }

        val response = withTimeout(REQUEST_TIMEOUT_MS) {
            deferred.await()
        }
        if (response.status >= 400) {
            throw RelayApiException(
                status = response.status,
                message = extractErrorMessage(response.body)
                    ?: "relay_api_error_${response.status}",
            )
        }
        return response.body
    }

    override suspend fun subscribeSession(sessionId: String): String {
        ensureConnected()
        val subscriptionId = UUID.randomUUID().toString()
        sendEncryptedMessage(
            json.encodeToJsonElement(
                RelaySubscribeMessage(
                    subscriptionId = subscriptionId,
                    channel = "session",
                    sessionId = sessionId,
                ),
            ),
        )
        return subscriptionId
    }

    override suspend fun subscribeActivity(): String {
        ensureConnected()
        val subscriptionId = UUID.randomUUID().toString()
        sendEncryptedMessage(
            json.encodeToJsonElement(
                RelaySubscribeMessage(
                    subscriptionId = subscriptionId,
                    channel = "activity",
                ),
            ),
        )
        return subscriptionId
    }

    override suspend fun unsubscribe(subscriptionId: String) {
        if (connectionStateMutable.value != RelayConnectionStatus.CONNECTED) {
            return
        }
        sendEncryptedMessage(
            json.encodeToJsonElement(
                RelayUnsubscribeMessage(subscriptionId = subscriptionId),
            ),
        )
    }

    private suspend fun connectInternal(
        relayUrl: String,
        storedSession: StoredRelaySession,
        routingUsername: String?,
    ) {
        val wsSession = httpClient.webSocketSession {
            url(relayUrl)
        }

        runCatching {
            if (!routingUsername.isNullOrBlank()) {
                wsSession.send(Frame.Text(RelayRoutingMessage.ClientConnect(username = routingUsername).encode()))
                when (val routingMessage = readTextFrame(wsSession)) {
                    is RelayRoutingMessage.ClientConnected -> Unit
                    is RelayRoutingMessage.ClientError -> {
                        throw IllegalStateException("relay_client_error_${routingMessage.reason.name.lowercase()}")
                    }

                    else -> {
                        throw IllegalStateException("unexpected_relay_routing_message")
                    }
                }
            }

            val transportKey = performSessionResumeHandshake(wsSession, storedSession)

            encryptionKey = transportKey
            socket = wsSession
            lastStoredSession = storedSession
            lastRelayUrl = relayUrl
            nextOutboundSeq = 0
            lastInboundSeq = null
            readerJob = scope.launch {
                readLoop(wsSession)
            }
            connectionStateMutable.value = RelayConnectionStatus.CONNECTED
        }.onFailure { error ->
            wsSession.close()
            throw error
        }
    }

    private suspend fun performSessionResumeHandshake(
        wsSession: DefaultClientWebSocketSession,
        storedSession: StoredRelaySession,
    ): ByteArray {
        val resumeInit = SrpMessage.SessionResumeInit(
            identity = storedSession.username,
            sessionId = storedSession.sessionId,
        ).encode()
        wsSession.send(Frame.Text(resumeInit))

        val resumeChallenge = when (val message = readTextFrame(wsSession)) {
            is SrpMessage.SessionResumeChallenge -> message
            is SrpMessage.SessionInvalid -> {
                throw IllegalStateException("relay_session_invalid_${message.reason.name.lowercase()}")
            }

            is SrpMessage.Error -> {
                throw IllegalStateException(message.message)
            }

            else -> {
                throw IllegalStateException("unexpected_resume_init_response")
            }
        }

        val resumeProof = SrpMessage.SessionResume(
            identity = storedSession.username,
            sessionId = storedSession.sessionId,
            proof = generateResumeProof(storedSession, resumeChallenge),
        )
        wsSession.send(Frame.Text(resumeProof.encode()))

        val resumed = when (val message = readTextFrame(wsSession)) {
            is SrpMessage.SessionResumed -> message
            is SrpMessage.SessionInvalid -> {
                throw IllegalStateException("relay_session_invalid_${message.reason.name.lowercase()}")
            }

            is SrpMessage.Error -> {
                throw IllegalStateException(message.message)
            }

            else -> {
                throw IllegalStateException("unexpected_resume_verify_response")
            }
        }

        val baseSessionKey = decodeBase64(storedSession.sessionKey)
        require(baseSessionKey.size == SECRETBOX_KEY_LENGTH) { "invalid_stored_session_key_length" }
        return resumed.transportNonce?.let { nonce ->
            deriveTransportKey(
                baseKey = baseSessionKey,
                transportNonceBase64 = nonce,
            )
        } ?: baseSessionKey
    }

    private suspend fun readTextFrame(session: DefaultClientWebSocketSession): Any {
        while (true) {
            when (val frame = session.incoming.receiveCatching().getOrNull()) {
                null -> throw IllegalStateException("relay_socket_closed")
                is Frame.Text -> {
                    val text = frame.readText()
                    RelayRoutingMessage.decode(text)?.let { return it }
                    SrpMessage.decode(text)?.let { return it }
                    return text
                }

                is Frame.Close -> throw IllegalStateException("relay_socket_closed")
                else -> Unit
            }
        }
    }

    private suspend fun readLoop(session: DefaultClientWebSocketSession) {
        runCatching {
            while (true) {
                when (val frame = session.incoming.receiveCatching().getOrNull()) {
                    null -> break
                    is Frame.Text -> handleInboundText(frame.readText())
                    is Frame.Close -> break
                    else -> Unit
                }
            }
        }.onFailure {
            // Reader loop errors are handled by disconnect path below.
        }

        connectionStateMutable.value = RelayConnectionStatus.DISCONNECTED
        socket = null
        encryptionKey = null
        nextOutboundSeq = 0
        lastInboundSeq = null
        failPendingRequests("relay_socket_disconnected")
    }

    private suspend fun handleInboundText(payload: String) {
        val envelope = runCatching {
            json.decodeFromString<EncryptedEnvelopeMessage>(payload)
        }.getOrNull() ?: return
        if (envelope.type != "encrypted") {
            return
        }

        val key = encryptionKey ?: return
        val decrypted = decryptEnvelope(
            envelope = envelope,
            key = key,
        ) ?: return
        val parsed = runCatching { json.parseToJsonElement(decrypted).jsonObject }.getOrNull() ?: return
        val seq = parsed["seq"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        if (seq != null) {
            val last = lastInboundSeq
            if (last != null && seq <= last) {
                return
            }
            lastInboundSeq = seq
        }
        val message = parsed["msg"] ?: return
        routeDecodedMessage(message)
    }

    private suspend fun routeDecodedMessage(message: JsonElement) {
        val messageObject = message as? JsonObject ?: return
        when (messageObject["type"]?.jsonPrimitive?.contentOrNull) {
            "response" -> {
                val response = runCatching {
                    json.decodeFromJsonElement<RelayResponseMessage>(messageObject)
                }.getOrNull() ?: return
                val pending = pendingMutex.withLock {
                    pendingRequests.remove(response.id)
                } ?: return
                pending.complete(response)
            }

            "event" -> {
                val event = runCatching {
                    json.decodeFromJsonElement<RelayEventMessage>(messageObject)
                }.getOrNull() ?: return
                eventsMutable.tryEmit(
                    RelayRealtimeEvent(
                        subscriptionId = event.subscriptionId,
                        eventType = event.eventType,
                        eventId = event.eventId,
                        data = event.data,
                    ),
                )
            }

            else -> Unit
        }
    }

    private suspend fun sendEncryptedMessage(message: JsonElement) {
        sendMutex.withLock {
            val currentSocket = socket ?: throw IllegalStateException("relay_socket_not_connected")
            val key = encryptionKey ?: throw IllegalStateException("relay_transport_key_missing")
            val wrapped = buildJsonObject {
                put("seq", JsonPrimitive(nextOutboundSeq))
                put("msg", message)
            }
            nextOutboundSeq += 1
            val plaintext = wrapped.toString()
            val envelope = encryptEnvelope(
                plaintext = plaintext,
                key = key,
            )
            currentSocket.send(
                Frame.Text(
                    json.encodeToString(
                        EncryptedEnvelopeMessage.serializer(),
                        envelope,
                    ),
                ),
            )
        }
    }

    private suspend fun failPendingRequests(reason: String) {
        val pending = pendingMutex.withLock {
            val snapshot = pendingRequests.values.toList()
            pendingRequests.clear()
            snapshot
        }
        pending.forEach { deferred ->
            deferred.completeExceptionally(IllegalStateException(reason))
        }
    }

    private fun extractErrorMessage(body: JsonElement?): String? {
        val jsonObject = body as? JsonObject ?: return null
        return jsonObject["error"]?.jsonPrimitive?.contentOrNull
            ?: jsonObject["message"]?.jsonPrimitive?.contentOrNull
    }

    private fun encryptEnvelope(
        plaintext: String,
        key: ByteArray,
    ): EncryptedEnvelopeMessage {
        val nonce = ByteArray(SECRETBOX_NONCE_LENGTH).also(secureRandom::nextBytes)
        val ciphertext = TweetNaclFast.SecretBox(key).box(plaintext.encodeToByteArray(), nonce)
            ?: error("relay_encryption_failed")

        return EncryptedEnvelopeMessage(
            nonce = encodeBase64(nonce),
            ciphertext = encodeBase64(ciphertext),
        )
    }

    private fun decryptEnvelope(
        envelope: EncryptedEnvelopeMessage,
        key: ByteArray,
    ): String? {
        val nonce = runCatching { decodeBase64(envelope.nonce) }.getOrNull() ?: return null
        if (nonce.size != SECRETBOX_NONCE_LENGTH) {
            return null
        }
        val ciphertext = runCatching { decodeBase64(envelope.ciphertext) }.getOrNull() ?: return null
        val plaintext = TweetNaclFast.SecretBox(key).open(ciphertext, nonce) ?: return null
        return plaintext.decodeToString()
    }

    private fun generateResumeProof(
        storedSession: StoredRelaySession,
        challenge: SrpMessage.SessionResumeChallenge,
    ): String {
        val sessionKey = decodeBase64(storedSession.sessionKey)
        require(sessionKey.size == SECRETBOX_KEY_LENGTH) { "invalid_stored_session_key_length" }

        val nonce = ByteArray(SECRETBOX_NONCE_LENGTH).also(secureRandom::nextBytes)
        val payload = buildJsonObject {
            put("timestamp", System.currentTimeMillis())
            put("challenge", challenge.nonce)
            put("sessionId", storedSession.sessionId)
        }.toString().encodeToByteArray()

        val ciphertext = TweetNaclFast.SecretBox(sessionKey).box(payload, nonce)
            ?: error("resume_proof_encryption_failed")
        return buildJsonObject {
            put("nonce", encodeBase64(nonce))
            put("ciphertext", encodeBase64(ciphertext))
        }.toString()
    }

    private fun deriveTransportKey(
        baseKey: ByteArray,
        transportNonceBase64: String,
    ): ByteArray {
        require(baseKey.size == SECRETBOX_KEY_LENGTH) { "invalid_base_session_key_length" }
        val nonce = decodeBase64(transportNonceBase64)
        require(nonce.size == SECRETBOX_NONCE_LENGTH) { "invalid_transport_nonce_length" }
        val label = TRANSPORT_KEY_LABEL.encodeToByteArray()
        val material = ByteArray(label.size + baseKey.size + nonce.size)
        label.copyInto(material, destinationOffset = 0)
        baseKey.copyInto(material, destinationOffset = label.size)
        nonce.copyInto(material, destinationOffset = label.size + baseKey.size)
        return MessageDigest.getInstance("SHA-512")
            .digest(material)
            .copyOf(SECRETBOX_KEY_LENGTH)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeBase64(value: ByteArray): String = Base64.encode(value)

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64(value: String): ByteArray = Base64.decode(value)
}
