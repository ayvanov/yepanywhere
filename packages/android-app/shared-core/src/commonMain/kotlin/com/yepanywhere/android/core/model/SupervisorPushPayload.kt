package com.yepanywhere.android.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface SupervisorPushPayload {
    val type: String
    val timestamp: String

    fun toEvent(): SupervisorPushEvent? {
        return when (this) {
            is PendingInput -> SupervisorPushEvent.PendingInput(
                sessionId = sessionId,
                projectId = projectId,
                projectName = projectName,
                inputType = inputType,
                summary = summary,
                requestId = requestId,
            )

            is SessionHalted -> SupervisorPushEvent.SessionHalted(
                sessionId = sessionId,
                projectId = projectId,
                projectName = projectName,
                reason = reason,
            )

            is Dismiss -> SupervisorPushEvent.Dismiss(sessionId = sessionId)
            is Test -> null
        }
    }

    fun toFields(): Map<String, String> {
        return when (this) {
            is PendingInput -> buildMap {
                put("type", type)
                put("timestamp", timestamp)
                put("sessionId", sessionId)
                put("projectId", projectId)
                put("projectName", projectName)
                put("inputType", inputType)
                put("summary", summary)
                requestId?.let { put("requestId", it) }
            }

            is SessionHalted -> buildMap {
                put("type", type)
                put("timestamp", timestamp)
                put("sessionId", sessionId)
                put("projectId", projectId)
                put("projectName", projectName)
                put("reason", reason)
                put("duration", duration.toString())
            }

            is Dismiss -> mapOf(
                "type" to type,
                "timestamp" to timestamp,
                "sessionId" to sessionId,
            )

            is Test -> buildMap {
                put("type", type)
                put("timestamp", timestamp)
                put("message", message)
                urgency?.let { put("urgency", it) }
            }
        }
    }

    fun encode(): String {
        return when (this) {
            is PendingInput -> json.encodeToString(this)
            is SessionHalted -> json.encodeToString(this)
            is Dismiss -> json.encodeToString(this)
            is Test -> json.encodeToString(this)
        }
    }

    @Serializable
    data class PendingInput(
        override val type: String = TYPE,
        override val timestamp: String = DEFAULT_TIMESTAMP,
        val sessionId: String,
        val projectId: String,
        val projectName: String = "Yep Anywhere",
        val inputType: String = "user-question",
        val summary: String = "Waiting for input",
        val requestId: String? = null,
    ) : SupervisorPushPayload {
        companion object {
            const val TYPE = "pending-input"
        }
    }

    @Serializable
    data class SessionHalted(
        override val type: String = TYPE,
        override val timestamp: String = DEFAULT_TIMESTAMP,
        val sessionId: String,
        val projectId: String,
        val projectName: String = "Yep Anywhere",
        val reason: String = "idle",
        val duration: Long = 0,
    ) : SupervisorPushPayload {
        companion object {
            const val TYPE = "session-halted"
        }
    }

    @Serializable
    data class Dismiss(
        override val type: String = TYPE,
        override val timestamp: String = DEFAULT_TIMESTAMP,
        val sessionId: String,
    ) : SupervisorPushPayload {
        companion object {
            const val TYPE = "dismiss"
        }
    }

    @Serializable
    data class Test(
        override val type: String = TYPE,
        override val timestamp: String = DEFAULT_TIMESTAMP,
        val message: String,
        val urgency: String? = null,
    ) : SupervisorPushPayload {
        companion object {
            const val TYPE = "test"
        }
    }

    companion object {
        const val DEFAULT_TIMESTAMP: String = "1970-01-01T00:00:00Z"

        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun decode(serialized: String): SupervisorPushPayload? {
            return runCatching {
                when (json.parseToJsonElement(serialized).jsonObject["type"]?.jsonPrimitive?.contentOrNull) {
                    PendingInput.TYPE -> json.decodeFromString<PendingInput>(serialized)
                    SessionHalted.TYPE -> json.decodeFromString<SessionHalted>(serialized)
                    Dismiss.TYPE -> json.decodeFromString<Dismiss>(serialized)
                    Test.TYPE -> json.decodeFromString<Test>(serialized)
                    else -> null
                }
            }.getOrNull()
        }

        fun fromFields(fields: Map<String, String>): SupervisorPushPayload? {
            return when (fields["type"]) {
                PendingInput.TYPE -> {
                    val sessionId = fields["sessionId"] ?: return null
                    val projectId = fields["projectId"] ?: return null
                    PendingInput(
                        timestamp = fields["timestamp"] ?: DEFAULT_TIMESTAMP,
                        sessionId = sessionId,
                        projectId = projectId,
                        projectName = fields["projectName"] ?: "Yep Anywhere",
                        inputType = fields["inputType"] ?: "user-question",
                        summary = fields["summary"] ?: "Waiting for input",
                        requestId = fields["requestId"],
                    )
                }

                SessionHalted.TYPE -> {
                    val sessionId = fields["sessionId"] ?: return null
                    val projectId = fields["projectId"] ?: return null
                    SessionHalted(
                        timestamp = fields["timestamp"] ?: DEFAULT_TIMESTAMP,
                        sessionId = sessionId,
                        projectId = projectId,
                        projectName = fields["projectName"] ?: "Yep Anywhere",
                        reason = fields["reason"] ?: "idle",
                        duration = fields["duration"]?.toLongOrNull() ?: 0,
                    )
                }

                Dismiss.TYPE -> {
                    val sessionId = fields["sessionId"] ?: return null
                    Dismiss(
                        timestamp = fields["timestamp"] ?: DEFAULT_TIMESTAMP,
                        sessionId = sessionId,
                    )
                }

                Test.TYPE -> {
                    val message = fields["message"] ?: return null
                    Test(
                        timestamp = fields["timestamp"] ?: DEFAULT_TIMESTAMP,
                        message = message,
                        urgency = fields["urgency"],
                    )
                }

                else -> null
            }
        }

        fun fromEvent(
            event: SupervisorPushEvent,
            timestamp: String = DEFAULT_TIMESTAMP,
        ): SupervisorPushPayload? {
            return when (event) {
                is SupervisorPushEvent.PendingInput -> PendingInput(
                    timestamp = timestamp,
                    sessionId = event.sessionId,
                    projectId = event.projectId,
                    projectName = event.projectName,
                    inputType = event.inputType,
                    summary = event.summary,
                    requestId = event.requestId,
                )

                is SupervisorPushEvent.SessionHalted -> SessionHalted(
                    timestamp = timestamp,
                    sessionId = event.sessionId,
                    projectId = event.projectId,
                    projectName = event.projectName,
                    reason = event.reason ?: "idle",
                    duration = 0,
                )

                is SupervisorPushEvent.Dismiss -> Dismiss(
                    timestamp = timestamp,
                    sessionId = event.sessionId,
                )

                is SupervisorPushEvent.Unknown -> null
            }
        }
    }
}
