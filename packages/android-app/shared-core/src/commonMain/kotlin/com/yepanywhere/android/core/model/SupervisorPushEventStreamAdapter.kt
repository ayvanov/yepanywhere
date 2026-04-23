package com.yepanywhere.android.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

class SupervisorPushEventStreamAdapter(
    private val payloadStream: Flow<SupervisorPushPayload>,
) {
    fun events(): Flow<SupervisorPushEvent> {
        return payloadStream.mapNotNull { payload -> SupervisorPushEvent.fromPayload(payload) }
    }
}
