package com.yepanywhere.android.core.model

sealed interface SupervisorPushEvent {
    val type: String

    fun toPayload(): Map<String, String> {
        return toPushPayload()?.toFields() ?: mapOf("type" to type)
    }

    fun toPushPayload(
        timestamp: String = SupervisorPushPayload.DEFAULT_TIMESTAMP,
    ): SupervisorPushPayload? {
        return SupervisorPushPayload.fromEvent(this, timestamp)
    }

    data class PendingInput(
        val sessionId: String,
        val projectId: String,
        val projectName: String = "Yep Anywhere",
        val inputType: String = "user-question",
        val summary: String = "Waiting for input",
        val requestId: String? = null,
    ) : SupervisorPushEvent {
        override val type: String = TYPE

        companion object {
            const val TYPE = "pending-input"
        }
    }

    data class SessionHalted(
        val sessionId: String,
        val projectId: String,
        val projectName: String = "Yep Anywhere",
        val reason: String? = null,
    ) : SupervisorPushEvent {
        override val type: String = TYPE

        companion object {
            const val TYPE = "session-halted"
        }
    }

    data class Dismiss(
        val sessionId: String,
    ) : SupervisorPushEvent {
        override val type: String = TYPE

        companion object {
            const val TYPE = "dismiss"
        }
    }

    data class Unknown(
        override val type: String,
    ) : SupervisorPushEvent

    companion object {
        fun fromPayload(payload: Map<String, String>): SupervisorPushEvent? {
            return SupervisorPushPayload.fromFields(payload)?.toEvent()
        }

        fun fromPayload(payload: SupervisorPushPayload): SupervisorPushEvent? {
            return payload.toEvent()
        }
    }
}
