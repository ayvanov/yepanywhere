package com.yepanywhere.android.core.model

sealed interface SupervisorPushEvent {
    val type: String

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
            return when (payload["type"]) {
                PendingInput.TYPE -> {
                    val sessionId = payload["sessionId"] ?: return null
                    val projectId = payload["projectId"] ?: return null
                    PendingInput(
                        sessionId = sessionId,
                        projectId = projectId,
                        projectName = payload["projectName"] ?: "Yep Anywhere",
                        inputType = payload["inputType"] ?: "user-question",
                        summary = payload["summary"] ?: "Waiting for input",
                        requestId = payload["requestId"],
                    )
                }

                SessionHalted.TYPE -> {
                    val sessionId = payload["sessionId"] ?: return null
                    val projectId = payload["projectId"] ?: return null
                    SessionHalted(
                        sessionId = sessionId,
                        projectId = projectId,
                        projectName = payload["projectName"] ?: "Yep Anywhere",
                        reason = payload["reason"],
                    )
                }

                Dismiss.TYPE -> {
                    val sessionId = payload["sessionId"] ?: return null
                    Dismiss(sessionId = sessionId)
                }

                else -> null
            }
        }
    }
}
