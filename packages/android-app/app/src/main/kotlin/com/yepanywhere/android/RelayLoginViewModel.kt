package com.yepanywhere.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yepanywhere.android.data.RelayCredentials
import com.yepanywhere.android.data.SupervisorShellDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RelayLoginUiState(
    val relayUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isInitializing: Boolean = true,
    val isSubmitting: Boolean = false,
    val isAuthenticated: Boolean = false,
    val errorMessage: String? = null,
)

class RelayLoginViewModel(
    private val dataSource: SupervisorShellDataSource,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope
    private var hasInitialized = false

    private val mutableUiState = MutableStateFlow(RelayLoginUiState())
    val uiState: StateFlow<RelayLoginUiState> = mutableUiState

    fun initialize() {
        if (hasInitialized) {
            return
        }

        hasInitialized = true
        coroutineScope.launch {
            val persistedCredentials = dataSource.restorePersistedCredentials()
            mutableUiState.update { current ->
                current.copy(
                    relayUrl = persistedCredentials?.relayUrl ?: current.relayUrl,
                    username = persistedCredentials?.username ?: current.username,
                )
            }

            val reconnected = dataSource.reconnectPersistedSession()
            mutableUiState.update { current ->
                current.copy(
                    isInitializing = false,
                    isAuthenticated = reconnected,
                    errorMessage = null,
                )
            }
        }
    }

    fun updateRelayUrl(value: String) {
        mutableUiState.update { it.copy(relayUrl = value) }
    }

    fun updateUsername(value: String) {
        mutableUiState.update { it.copy(username = value) }
    }

    fun updatePassword(value: String) {
        mutableUiState.update { it.copy(password = value) }
    }

    fun submitLogin() {
        val current = mutableUiState.value
        val relayUrl = current.relayUrl.trim()
        val username = current.username.trim()
        val password = current.password
        if (relayUrl.isBlank() || username.isBlank() || password.isBlank()) {
            mutableUiState.update {
                it.copy(errorMessage = "Relay URL, username, and password are required.")
            }
            return
        }
        if (current.isSubmitting || current.isInitializing) {
            return
        }

        mutableUiState.update {
            it.copy(
                isSubmitting = true,
                errorMessage = null,
            )
        }
        coroutineScope.launch {
            runCatching {
                dataSource.login(
                    RelayCredentials(
                        relayUrl = relayUrl,
                        username = username,
                        password = password,
                    ),
                )
            }.onSuccess {
                mutableUiState.update {
                    it.copy(
                        password = "",
                        isSubmitting = false,
                        isAuthenticated = true,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                mutableUiState.update {
                    it.copy(
                        isSubmitting = false,
                        isAuthenticated = false,
                        errorMessage = error.message ?: "Relay login failed.",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(dataSource: SupervisorShellDataSource): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == RelayLoginViewModel::class.java)
                    @Suppress("UNCHECKED_CAST")
                    return RelayLoginViewModel(dataSource = dataSource) as T
                }
            }
        }
    }
}
