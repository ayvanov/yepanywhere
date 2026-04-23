package com.yepanywhere.android.core.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RelayWebSocketUrlResolverTest {
    @Test
    fun keepsWebSocketSchemesAndTrimsTrailingSlash() {
        assertEquals(
            "wss://relay.yepanywhere.local",
            resolveRelayWebSocketUrl("wss://relay.yepanywhere.local/"),
        )
        assertEquals(
            "ws://127.0.0.1:3400",
            resolveRelayWebSocketUrl("ws://127.0.0.1:3400/"),
        )
    }

    @Test
    fun mapsHttpSchemesToWebSocketSchemes() {
        assertEquals(
            "ws://127.0.0.1:3400",
            resolveRelayWebSocketUrl("http://127.0.0.1:3400"),
        )
        assertEquals(
            "wss://relay.yepanywhere.local",
            resolveRelayWebSocketUrl("https://relay.yepanywhere.local"),
        )
    }

    @Test
    fun defaultsToSecureWebSocketWhenSchemeMissing() {
        assertEquals(
            "wss://relay.yepanywhere.local",
            resolveRelayWebSocketUrl("relay.yepanywhere.local"),
        )
    }

    @Test
    fun rejectsUnsupportedScheme() {
        val error = assertFailsWith<IllegalArgumentException> {
            resolveRelayWebSocketUrl("ftp://relay.yepanywhere.local")
        }

        assertEquals("relay_url_scheme_unsupported", error.message)
    }

    @Test
    fun rejectsBlankUrl() {
        val error = assertFailsWith<IllegalArgumentException> {
            resolveRelayWebSocketUrl("   ")
        }

        assertEquals("relay_url_missing", error.message)
    }
}
