package com.yepanywhere.android

import com.yepanywhere.android.data.AndroidDataLayer

class AndroidSupervisorPushEventHandler(
    private val handleNotificationPayload: (Map<String, String>) -> Boolean,
    private val applyPendingInputNotification: suspend (
        sessionId: String,
        projectId: String,
        projectName: String,
        inputType: String,
        summary: String,
        requestId: String,
    ) -> Unit,
    private val clearSessionAttention: suspend (String) -> Unit,
) {
    constructor(
        dataLayer: AndroidDataLayer,
        notificationPayloadHandler: AndroidPushNotificationPayloadHandler,
    ) : this(
        handleNotificationPayload = notificationPayloadHandler::handle,
        applyPendingInputNotification = dataLayer::applyPendingInputNotification,
        clearSessionAttention = dataLayer::clearSessionAttention,
    )

    suspend fun handle(payload: Map<String, String>): Boolean {
        when (payload["type"]) {
            "pending-input" -> applyPendingInputPayload(payload)
            "dismiss" -> payload["sessionId"]?.let { clearSessionAttention(it) }
        }

        return handleNotificationPayload(payload)
    }

    private suspend fun applyPendingInputPayload(payload: Map<String, String>) {
        applyPendingInputNotification(
            payload["sessionId"] ?: return,
            payload["projectId"] ?: return,
            payload["projectName"] ?: "Yep Anywhere",
            payload["inputType"] ?: "user-question",
            payload["summary"] ?: "Waiting for input",
            payload["requestId"] ?: return,
        )
    }
}
