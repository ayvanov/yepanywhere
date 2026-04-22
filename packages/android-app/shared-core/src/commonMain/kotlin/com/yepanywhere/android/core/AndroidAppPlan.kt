package com.yepanywhere.android.core

data class AndroidAppPlan(
    val summary: String,
    val modules: List<String>,
    val scope: List<String>,
)

object AndroidAppPlanDefaults {
    val supervisorMvp = AndroidAppPlan(
        summary = "Relay-first native Android client scaffold with Supervisor MVP boundaries.",
        modules = listOf(
            "app",
            "shared-core",
            "shared-ui",
            "android-data",
        ),
        scope = listOf(
            "Relay login and reconnect",
            "Projects and sessions list",
            "Session detail with reply and approvals",
            "Inbox",
            "Push-driven reopen flow",
            "Cached read-only offline snapshots",
        ),
    )
}

