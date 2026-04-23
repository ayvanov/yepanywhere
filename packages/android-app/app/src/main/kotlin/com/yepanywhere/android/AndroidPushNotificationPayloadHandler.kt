package com.yepanywhere.android

import com.yepanywhere.android.core.model.SupervisorPushEvent

class AndroidPushNotificationPayloadHandler(
    private val dispatchNotification: (String?, String?, Map<String, String>) -> Boolean,
    private val dismissSessionNotifications: (String) -> Boolean = { false },
) {
    constructor(dispatcher: AndroidNotificationEventDispatcher) : this(dispatcher::dispatch)
    constructor(
        dispatcher: AndroidNotificationEventDispatcher,
        poster: AndroidNotificationPoster,
    ) : this(dispatcher::dispatch, poster::cancelSession)

    fun handle(event: SupervisorPushEvent): Boolean {
        return when (event) {
            is SupervisorPushEvent.PendingInput -> handlePendingInput(event)
            is SupervisorPushEvent.SessionHalted -> handleSessionHalted(event)
            is SupervisorPushEvent.Dismiss -> handleDismiss(event)
            else -> false
        }
    }

    private fun handlePendingInput(event: SupervisorPushEvent.PendingInput): Boolean {
        val routeData = buildMap {
            put("target", "inbox")
            put("projectId", event.projectId)
            put("sessionId", event.sessionId)
            event.requestId?.let { put("inboxItemId", it) }
        }

        return dispatchNotification(
            event.projectName,
            event.summary,
            routeData,
        )
    }

    private fun handleSessionHalted(event: SupervisorPushEvent.SessionHalted): Boolean {
        val routeData = mapOf(
            "target" to "session",
            "projectId" to event.projectId,
            "sessionId" to event.sessionId,
        )

        return dispatchNotification(
            event.projectName,
            sessionHaltedBody(event.reason),
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

    private fun handleDismiss(event: SupervisorPushEvent.Dismiss): Boolean {
        return dismissSessionNotifications(event.sessionId)
    }
}
