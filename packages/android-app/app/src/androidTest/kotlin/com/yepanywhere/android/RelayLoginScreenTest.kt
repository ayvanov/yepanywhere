package com.yepanywhere.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelayLoginScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initializingStateStillShowsLoginFields() {
        renderLogin(
            state = RelayLoginUiState(
                relayUrl = "relay.yepanywhere.com",
                username = "demo@yepanywhere",
                password = "",
                isInitializing = true,
                isSubmitting = false,
                isAuthenticated = false,
            ),
        )

        composeRule.onNodeWithText("Relay URL")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Identity")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Password")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Trying saved relay session...")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Sign in")
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("relay-url-input")
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("identity-input")
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("password-input")
            .assertIsNotEnabled()
    }

    @Test
    fun submittingStateDisablesInputFields() {
        renderLogin(
            state = RelayLoginUiState(
                relayUrl = "relay.yepanywhere.com",
                username = "demo@yepanywhere",
                password = "secret",
                isInitializing = false,
                isSubmitting = true,
                isAuthenticated = false,
            ),
        )

        composeRule.onNodeWithText("Signing in...")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("relay-url-input")
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("identity-input")
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("password-input")
            .assertIsNotEnabled()
    }

    private fun renderLogin(
        state: RelayLoginUiState,
    ) {
        composeRule.setContent {
            RelayLoginScreen(
                state = state,
                onRelayUrlChanged = {},
                onUsernameChanged = {},
                onPasswordChanged = {},
                onSubmit = {},
            )
        }
    }
}
