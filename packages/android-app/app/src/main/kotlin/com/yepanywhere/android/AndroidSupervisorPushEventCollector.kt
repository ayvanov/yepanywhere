package com.yepanywhere.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class AndroidSupervisorPushEventCollector(
    private val eventStream: Flow<Map<String, String>>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val handleEvent: suspend (Map<String, String>) -> Boolean,
) {
    fun start(scope: CoroutineScope): Job {
        return scope.launch(dispatcher) {
            eventStream.collect { event ->
                try {
                    handleEvent(event)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Throwable) {
                    // Keep foreground collection alive; bad events should not break future notifications.
                }
            }
        }
    }
}
