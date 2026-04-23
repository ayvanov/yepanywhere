package com.yepanywhere.android

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAppContainerPushEventTest {
    @Test
    fun foregroundPushEventsUpdateSupervisorCache() = runTest {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext as Application
        val container = AndroidAppContainer(
            application = application,
            pushEventDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val job = container.supervisorPushEventCollector.start(backgroundScope)

        org.junit.Assert.assertTrue(
            container.androidDataLayer.emitSupervisorPushPayload(
                mapOf(
                    "type" to "pending-input",
                    "sessionId" to "session-container",
                    "projectId" to "project-container",
                    "projectName" to "Container Project",
                    "inputType" to "user-question",
                    "summary" to "Confirm foreground event wiring?",
                    "requestId" to "request-container",
                ),
            ),
        )
        advanceUntilIdle()

        assertTrue(
            container.androidDataLayer.shellState.value.pendingRequests.any { request ->
                request.id == "request-container" &&
                    request.sessionId == "session-container"
            },
        )

        job.cancelAndJoin()
    }
}
