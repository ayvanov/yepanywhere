package com.yepanywhere.android

import com.yepanywhere.android.data.RelayCredentials
import com.yepanywhere.android.data.SupervisorShellDataSource
import com.yepanywhere.android.data.defaultSupervisorShellSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RelayLoginViewModelTest {
    @Test
    fun initializeReconnectsPersistedSessionAndAuthenticates() = runTest {
        val source = FakeSupervisorShellDataSource().apply {
            persistedCredentials = RelayCredentials(
                relayUrl = "wss://relay.yepanywhere.local",
                username = "demo@yepanywhere",
                password = "secret",
            )
            reconnectResult = true
        }
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val viewModel = RelayLoginViewModel(
            dataSource = source,
            scope = externalScope,
        )

        viewModel.initialize()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAuthenticated)
        assertFalse(viewModel.uiState.value.isInitializing)
        assertEquals("wss://relay.yepanywhere.local", viewModel.uiState.value.relayUrl)
        assertEquals("demo@yepanywhere", viewModel.uiState.value.username)
        assertEquals(1, source.reconnectCalls)

        externalScope.cancel()
    }

    @Test
    fun initializeAttemptsReconnectWithSavedIdentityAndEmptyPassword() = runTest {
        val source = FakeSupervisorShellDataSource().apply {
            persistedCredentials = RelayCredentials(
                relayUrl = "wss://relay.yepanywhere.local",
                username = "demo@yepanywhere",
                password = "",
            )
            reconnectResult = false
        }
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val viewModel = RelayLoginViewModel(
            dataSource = source,
            scope = externalScope,
        )

        viewModel.initialize()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertFalse(viewModel.uiState.value.isInitializing)
        assertEquals("wss://relay.yepanywhere.local", viewModel.uiState.value.relayUrl)
        assertEquals("demo@yepanywhere", viewModel.uiState.value.username)
        assertEquals(1, source.reconnectCalls)

        externalScope.cancel()
    }

    @Test
    fun submitLoginValidatesRequiredFields() = runTest {
        val source = FakeSupervisorShellDataSource()
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val viewModel = RelayLoginViewModel(
            dataSource = source,
            scope = externalScope,
        )

        viewModel.initialize()
        advanceUntilIdle()
        viewModel.submitLogin()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertEquals("Relay URL, username, and password are required.", viewModel.uiState.value.errorMessage)

        externalScope.cancel()
    }

    @Test
    fun submitLoginPersistsCredentialsAndAuthenticates() = runTest {
        val source = FakeSupervisorShellDataSource()
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val viewModel = RelayLoginViewModel(
            dataSource = source,
            scope = externalScope,
        )

        viewModel.initialize()
        advanceUntilIdle()
        viewModel.updateRelayUrl("wss://relay.yepanywhere.local")
        viewModel.updateUsername("demo@yepanywhere")
        viewModel.updatePassword("secret")
        viewModel.submitLogin()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAuthenticated)
        assertEquals(
            RelayCredentials(
                relayUrl = "wss://relay.yepanywhere.local",
                username = "demo@yepanywhere",
                password = "secret",
            ),
            source.loginCalls.single(),
        )

        externalScope.cancel()
    }

    @Test
    fun logoutClearsAuthenticationAndDelegatesToDataSource() = runTest {
        val source = FakeSupervisorShellDataSource().apply {
            reconnectResult = true
        }
        val externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val viewModel = RelayLoginViewModel(
            dataSource = source,
            scope = externalScope,
        )

        viewModel.initialize()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isAuthenticated)

        viewModel.logout()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertEquals(1, source.logoutCalls)

        externalScope.cancel()
    }

    private class FakeSupervisorShellDataSource : SupervisorShellDataSource {
        override val summary: String = "Android-owned cache and secure relay session persistence."
        override val shellState = MutableStateFlow(defaultSupervisorShellSnapshot())

        var reconnectResult: Boolean = false
        var reconnectCalls: Int = 0
        var persistedCredentials: RelayCredentials? = null
        val loginCalls = mutableListOf<RelayCredentials>()
        var logoutCalls: Int = 0

        override suspend fun reconnectPersistedSession(): Boolean {
            reconnectCalls += 1
            return reconnectResult
        }

        override suspend fun login(credentials: RelayCredentials) {
            loginCalls += credentials
        }

        override suspend fun logout() {
            logoutCalls += 1
        }

        override suspend fun restorePersistedCredentials(): RelayCredentials? = persistedCredentials
    }
}
