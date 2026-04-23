package com.yepanywhere.android

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.yepanywhere.android.core.model.SupervisorPushEvent
import kotlinx.coroutines.runBlocking

class AndroidFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val payload = message.data
        if (payload.isEmpty()) {
            return
        }

        val app = application as? YepAnywhereAndroidApplication ?: return
        val appContainer = app.appContainer
        val event = SupervisorPushEvent.fromPayload(payload)
        if (event != null) {
            runBlocking {
                appContainer.supervisorPushEventHandler.handle(event)
            }
            return
        }

        appContainer.pushNotificationPayloadHandler.handleDataPayload(
            data = payload,
            title = message.notification?.title,
            body = message.notification?.body,
        )
    }

    override fun onNewToken(token: String) {
        val app = application as? YepAnywhereAndroidApplication ?: return
        app.appContainer.pushTokenLifecycleManager.onNewToken(token)
    }
}
