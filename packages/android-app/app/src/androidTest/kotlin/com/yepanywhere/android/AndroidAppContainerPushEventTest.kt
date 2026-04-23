package com.yepanywhere.android

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yepanywhere.android.core.model.SupervisorPushPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

        container.androidDataLayer.emitSupervisorPushPayload(
            SupervisorPushPayload.PendingInput(
                timestamp = "2026-04-23T10:15:30Z",
                sessionId = "session-container",
                projectId = "project-container",
                projectName = "Container Project",
                inputType = "user-question",
                summary = "Confirm foreground event wiring?",
                requestId = "request-container",
            ),
        )
        val deadline = System.currentTimeMillis() + 5_000
        while (
            System.currentTimeMillis() < deadline &&
            container.androidDataLayer.shellState.value.pendingRequests.none { request ->
                request.id == "request-container" && request.sessionId == "session-container"
            }
        ) {
            Thread.sleep(50)
        }

        assertTrue(
            "pending request from foreground push payload was not observed",
            container.androidDataLayer.shellState.value.pendingRequests.any { request ->
                request.id == "request-container" &&
                    request.sessionId == "session-container"
            },
        )

        job.cancelAndJoin()
    }
}
