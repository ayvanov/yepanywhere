package com.yepanywhere.android.data

import com.yepanywhere.android.core.model.SupervisorShellSnapshot
import com.yepanywhere.android.core.model.SupervisorPushEvent
import com.yepanywhere.android.core.model.SupervisorPushPayload
import com.yepanywhere.android.core.model.StoredRelaySession
import com.yepanywhere.android.core.repository.ApprovalsRepository
import com.yepanywhere.android.core.repository.InboxRepository
import com.yepanywhere.android.core.repository.ProjectsRepository
import com.yepanywhere.android.core.repository.RelayConnectionClient
import com.yepanywhere.android.core.repository.SessionsRepository
import com.yepanywhere.android.core.usecase.SecureRelayAuthHandshakeResult
import kotlinx.coroutines.flow.StateFlow

interface SupervisorShellDataSource {
    val summary: String
    val shellState: StateFlow<SupervisorShellSnapshot>

    suspend fun reconnectPersistedSession(): Boolean

    suspend fun login(credentials: RelayCredentials)

    suspend fun restorePersistedCredentials(): RelayCredentials?
}

interface SupervisorFeatureDependencies {
    val activeSessionId: String
    val projectsRepository: ProjectsRepository
    val sessionsRepository: SessionsRepository
    val inboxRepository: InboxRepository
    val approvalsRepository: ApprovalsRepository
}

class AndroidDataLayer(
    runtimeOverride: InMemorySupervisorRuntime? = null,
    relayAuthHandshake: (suspend (
        username: String,
        password: String?,
        relayUrl: String,
        storedSession: StoredRelaySession?,
    ) -> SecureRelayAuthHandshakeResult)? = null,
    private val relayAuthStateStore: RelayAuthStateStore = InMemoryRelayAuthStateStore(),
) : SupervisorShellDataSource, SupervisorFeatureDependencies {
    private val runtime: InMemorySupervisorRuntime = runtimeOverride ?: if (relayAuthHandshake != null) {
        InMemorySupervisorRuntime(relayAuthHandshake = relayAuthHandshake)
    } else {
        InMemorySupervisorRuntime()
    }

    override val summary: String = SUMMARY

    override val shellState = runtime.shellState
    override val activeSessionId: String = runtime.activeSessionId
    override val projectsRepository: ProjectsRepository = runtime.projectsRepository
    override val sessionsRepository: SessionsRepository = runtime.sessionsRepository
    override val inboxRepository: InboxRepository = runtime.inboxRepository
    override val approvalsRepository: ApprovalsRepository = runtime.approvalsRepository
    val relayConnectionClient: RelayConnectionClient = runtime.relayConnectionClient

    override suspend fun reconnectPersistedSession(): Boolean {
        val persistedState = relayAuthStateStore.read() ?: return false
        persistedState.storedSession?.let { runtime.relayAuthRepository.persistStoredSession(it) }

        return try {
            runtime.relayAuthRepository.login(
                username = persistedState.credentials.username,
                password = persistedState.credentials.password,
                relayUrl = persistedState.credentials.relayUrl,
            )
            persistRelayAuthState(persistedState.credentials)
            true
        } catch (_: Throwable) {
            runtime.relayAuthRepository.clearSession()
            false
        }
    }

    override suspend fun login(credentials: RelayCredentials) {
        val normalizedCredentials = credentials.copy(
            relayUrl = credentials.relayUrl.trim(),
            username = credentials.username.trim(),
        )
        runtime.relayAuthRepository.login(
            username = normalizedCredentials.username,
            password = normalizedCredentials.password,
            relayUrl = normalizedCredentials.relayUrl,
        )
        persistRelayAuthState(normalizedCredentials)
    }

    override suspend fun restorePersistedCredentials(): RelayCredentials? {
        return relayAuthStateStore.read()?.credentials
    }

    private suspend fun persistRelayAuthState(credentials: RelayCredentials) {
        relayAuthStateStore.write(
            PersistedRelayAuthState(
                credentials = credentials,
                storedSession = runtime.relayAuthRepository.restoreStoredSession(),
            ),
        )
    }

    suspend fun applyPendingInputNotification(
        sessionId: String,
        projectId: String,
        projectName: String,
        inputType: String,
        summary: String,
        requestId: String,
    ) {
        runtime.applyPendingInputNotification(
            sessionId = sessionId,
            projectId = projectId,
            projectName = projectName,
            inputType = inputType,
            summary = summary,
            requestId = requestId,
        )
    }

    suspend fun clearSessionAttention(sessionId: String) {
        runtime.clearSessionAttention(sessionId)
    }

    suspend fun emitSupervisorPushEvent(event: SupervisorPushEvent) {
        runtime.emitSupervisorPushEvent(event)
    }

    suspend fun emitSupervisorPushPayload(payload: SupervisorPushPayload) {
        runtime.emitSupervisorPushPayload(payload)
    }

    suspend fun emitSupervisorPushPayload(payload: Map<String, String>): Boolean {
        return runtime.emitSupervisorPushPayload(payload)
    }

    companion object {
        const val SUMMARY: String =
            "Android-owned cache/storage layer for Room, DataStore, and secure relay session persistence."
    }
}
