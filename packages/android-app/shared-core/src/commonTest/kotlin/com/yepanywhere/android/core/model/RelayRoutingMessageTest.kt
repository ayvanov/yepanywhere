package com.yepanywhere.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayRoutingMessageTest {
    @Test
    fun decodesServerRegisterWithCompatibilityMetadata() {
        val json = """
            {
              "type": "server_register",
              "username": "alice",
              "installId": "abc-123",
              "appVersion": "0.2.0",
              "resumeProtocolVersion": 2,
              "renderProtocolVersion": 1,
              "capabilities": ["git-status", "deviceBridge"]
            }
        """.trimIndent()

        assertEquals(
            RelayRoutingMessage.ServerRegister(
                username = "alice",
                installId = "abc-123",
                appVersion = "0.2.0",
                resumeProtocolVersion = 2,
                renderProtocolVersion = 1,
                capabilities = listOf("git-status", "deviceBridge"),
            ),
            RelayRoutingMessage.decode(json),
        )
    }

    @Test
    fun rejectsClientErrorsWithUnknownReason() {
        val json = """
            {
              "type": "client_error",
              "reason": "timeout"
            }
        """.trimIndent()

        assertNull(RelayRoutingMessage.decode(json))
    }

    @Test
    fun roundTripsClientConnectMessages() {
        val message = RelayRoutingMessage.ClientConnect(username = "dev-server")

        assertEquals(message, RelayRoutingMessage.decode(message.encode()))
    }

    @Test
    fun validatesRelayUsernameFormat() {
        assertTrue(isValidRelayUsername("alice"))
        assertTrue(isValidRelayUsername("dev-server"))
        assertFalse(isValidRelayUsername("ab"))
        assertFalse(isValidRelayUsername("Alice"))
        assertFalse(isValidRelayUsername("alice_bob"))
        assertFalse(isValidRelayUsername("-alice"))
    }
}
