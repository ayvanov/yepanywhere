package com.yepanywhere.android.core.usecase

import com.yepanywhere.android.core.model.SrpMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class KtorSecureRelayAuthTransportTest {
    @Test
    fun sendEncodesAndWritesSrpMessageText() = runTest {
        val socket = FakeSecureRelayAuthSocket()
        val transport = KtorSecureRelayAuthTransport(
            sessionFactory = { socket },
        )

        transport.send(
            SrpMessage.SessionResumeInit(
                identity = "demo@yepanywhere",
                sessionId = "session-1",
            ),
        )

        assertEquals(
            listOf(
                """{"type":"srp_resume_init","identity":"demo@yepanywhere","sessionId":"session-1"}""",
            ),
            socket.sentTexts,
        )
    }

    @Test
    fun receiveDecodesSrpMessageFromTextFrame() = runTest {
        val socket = FakeSecureRelayAuthSocket(
            incomingTexts = ArrayDeque(
                listOf(
                    """{"type":"srp_invalid","reason":"expired"}""",
                ),
            ),
        )
        val transport = KtorSecureRelayAuthTransport(
            sessionFactory = { socket },
        )

        assertEquals(
            SrpMessage.SessionInvalid(reason = com.yepanywhere.android.core.model.SrpSessionInvalidReason.EXPIRED),
            transport.receive(),
        )
    }

    @Test
    fun receiveRejectsInvalidAuthPayload() = runTest {
        val socket = FakeSecureRelayAuthSocket(
            incomingTexts = ArrayDeque(
                listOf(
                    """{"type":"unknown"}""",
                ),
            ),
        )
        val transport = KtorSecureRelayAuthTransport(
            sessionFactory = { socket },
        )

        val error = assertFailsWith<IllegalStateException> {
            transport.receive()
        }
        assertEquals("invalid_auth_message", error.message)
    }

    @Test
    fun closeDelegatesToUnderlyingSocket() = runTest {
        val socket = FakeSecureRelayAuthSocket()
        val transport = KtorSecureRelayAuthTransport(
            sessionFactory = { socket },
        )

        transport.send(SrpMessage.ClientHello(identity = "demo@yepanywhere"))
        transport.close()

        assertEquals(true, socket.closed)
    }

    private class FakeSecureRelayAuthSocket(
        private val incomingTexts: ArrayDeque<String> = ArrayDeque(),
    ) : SecureRelayAuthSocket {
        val sentTexts = mutableListOf<String>()
        var closed: Boolean = false

        override suspend fun sendText(text: String) {
            sentTexts += text
        }

        override suspend fun receiveText(): String {
            return incomingTexts.removeFirstOrNull() ?: error("No incoming auth payload")
        }

        override suspend fun close() {
            closed = true
        }
    }
}
