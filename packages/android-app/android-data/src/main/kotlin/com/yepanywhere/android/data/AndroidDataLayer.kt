package com.yepanywhere.android.data

import com.yepanywhere.android.core.model.SupervisorShellSnapshot
import kotlinx.coroutines.flow.StateFlow

interface SupervisorShellDataSource {
    val summary: String
    val shellState: StateFlow<SupervisorShellSnapshot>

    suspend fun connectDemoSession()
}

class AndroidDataLayer(
    private val runtime: InMemorySupervisorRuntime = InMemorySupervisorRuntime(),
) : SupervisorShellDataSource {
    override val summary: String = SUMMARY

    override val shellState = runtime.shellState

    override suspend fun connectDemoSession() {
        runtime.connectDemoSession()
    }

    companion object {
        const val SUMMARY: String =
            "Android-owned cache/storage layer for Room, DataStore, and secure relay session persistence."
    }
}
