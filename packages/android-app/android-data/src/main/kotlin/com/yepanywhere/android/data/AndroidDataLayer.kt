package com.yepanywhere.android.data

object AndroidDataLayer {
    const val summary: String =
        "Android-owned cache/storage layer for Room, DataStore, and secure relay session persistence."

    private val runtime = InMemorySupervisorRuntime()

    val shellState = runtime.shellState

    suspend fun connectDemoSession() {
        runtime.connectDemoSession()
    }
}
