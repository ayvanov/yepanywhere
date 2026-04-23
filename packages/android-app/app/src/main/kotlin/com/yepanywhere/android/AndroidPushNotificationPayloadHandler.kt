package com.yepanywhere.android

class AndroidPushNotificationPayloadHandler(
    private val dispatchNotification: (String?, String?, Map<String, String>) -> Boolean,
) {
    constructor(dispatcher: AndroidNotificationEventDispatcher) : this(dispatcher::dispatch)

    fun handle(payload: Map<String, String>): Boolean {
        return when (payload["type"]) {
            "pending-input" -> handlePendingInput(payload)
            "session-halted" -> handleSessionHalted(payload)
            else -> false
        }
    }

    private fun handlePendingInput(payload: Map<String, String>): Boolean {
        val sessionId = payload["sessionId"] ?: return false
        val projectId = payload["projectId"] ?: return false
        val routeData = buildMap {
            put("target", "inbox")
            put("projectId", projectId)
            put("sessionId", sessionId)
            payload["requestId"]?.let { put("inboxItemId", it) }
        }

        return dispatchNotification(
            payload["projectName"],
            payload["summary"] ?: "Waiting for input",
            routeData,
        )
    }

    private fun handleSessionHalted(payload: Map<String, String>): Boolean {
        val sessionId = payload["sessionId"] ?: return false
        val projectId = payload["projectId"] ?: return false
        val routeData = mapOf(
            "target" to "session",
            "projectId" to projectId,
            "sessionId" to sessionId,
        )

        return dispatchNotification(
            payload["projectName"],
            sessionHaltedBody(payload["reason"]),
            routeData,
        )
    }

    private fun sessionHaltedBody(reason: String?): String {
        return when (reason) {
            "completed" -> "Task completed"
            "error" -> "Task encountered an error"
            "idle" -> "Task stopped"
            else -> "Session stopped"
        }
    }
}
