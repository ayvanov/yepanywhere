package com.yepanywhere.android

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidRelayAuthSettingsTest {
    @Test
    fun parsesRelayModeAndUsername() {
        val settings = AndroidRelayAuthSettings.fromRaw(
            modeRaw = "relay",
            relayUrlRaw = " wss://relay.yepanywhere.local ",
            relayUsernameRaw = " relay-user ",
        )

        assertEquals(AndroidRelayAuthMode.RELAY, settings.mode)
        assertEquals("wss://relay.yepanywhere.local", settings.relayUrl)
        assertEquals("relay-user", settings.relayUsername)
    }

    @Test
    fun defaultsToDemoModeForUnknownModeAndEmptyUsername() {
        val settings = AndroidRelayAuthSettings.fromRaw(
            modeRaw = "unexpected",
            relayUrlRaw = "ws://127.0.0.1:3400",
            relayUsernameRaw = "   ",
        )

        assertEquals(AndroidRelayAuthMode.DEMO, settings.mode)
        assertEquals("ws://127.0.0.1:3400", settings.relayUrl)
        assertEquals(null, settings.relayUsername)
    }
}
