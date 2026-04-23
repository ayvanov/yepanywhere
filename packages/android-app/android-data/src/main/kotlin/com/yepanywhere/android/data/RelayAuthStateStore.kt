package com.yepanywhere.android.data

import com.yepanywhere.android.core.model.StoredRelaySession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RelayCredentials(
    val relayUrl: String,
    val username: String,
    val password: String,
)

data class PersistedRelayAuthState(
    val credentials: RelayCredentials,
    val storedSession: StoredRelaySession?,
)

interface RelayAuthStateStore {
    suspend fun read(): PersistedRelayAuthState?

    suspend fun write(state: PersistedRelayAuthState)

    suspend fun clear()
}

class InMemoryRelayAuthStateStore(
    initialState: PersistedRelayAuthState? = null,
) : RelayAuthStateStore {
    private val mutex = Mutex()
    private var state: PersistedRelayAuthState? = initialState

    override suspend fun read(): PersistedRelayAuthState? = mutex.withLock { state }

    override suspend fun write(state: PersistedRelayAuthState) {
        mutex.withLock {
            this.state = state
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            state = null
        }
    }
}
