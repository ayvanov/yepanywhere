package com.yepanywhere.android

import com.yepanywhere.android.core.model.SupervisorPushEvent
import com.yepanywhere.android.data.AndroidDataLayer

class AndroidSupervisorPushEventHandler(
    private val handleNotificationPayload: (SupervisorPushEvent) -> Boolean,
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

    suspend fun handle(event: SupervisorPushEvent): Boolean {
        when (event) {
            is SupervisorPushEvent.PendingInput -> applyPendingInputPayload(event)
            is SupervisorPushEvent.Dismiss -> clearSessionAttention(event.sessionId)
            else -> Unit
        }

        return handleNotificationPayload(event)
    }

    private suspend fun applyPendingInputPayload(event: SupervisorPushEvent.PendingInput) {
        applyPendingInputNotification(
            event.sessionId,
            event.projectId,
            event.projectName,
            event.inputType,
            event.summary,
            event.requestId ?: return,
        )
    }
}
