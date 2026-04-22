package com.yepanywhere.android.data

class AndroidDataLayer(
    private val runtime: InMemorySupervisorRuntime = InMemorySupervisorRuntime(),
) {
    val shellState = runtime.shellState

    suspend fun connectDemoSession() {
        runtime.connectDemoSession()
    }

    companion object {
        const val summary: String =
            "Android-owned cache/storage layer for Room, DataStore, and secure relay session persistence."
    }
}
