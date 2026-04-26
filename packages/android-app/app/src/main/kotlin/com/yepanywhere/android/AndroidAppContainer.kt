package com.yepanywhere.android

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.yepanywhere.android.data.AndroidDataLayer
import com.yepanywhere.android.data.RoomSessionCacheStore
import com.yepanywhere.android.core.usecase.AnswerQuestionUseCase
import com.yepanywhere.android.core.usecase.ApproveRequestUseCase
import com.yepanywhere.android.core.usecase.DenyRequestUseCase
import com.yepanywhere.android.core.usecase.ObserveActiveSessionUseCase
import com.yepanywhere.android.core.usecase.ObserveInboxUseCase
import com.yepanywhere.android.core.usecase.ObserveProjectsUseCase
import com.yepanywhere.android.core.usecase.SendSessionReplyUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class AndroidAppContainer(
    application: Application,
    pushEventDispatcher: CoroutineDispatcher = Dispatchers.Default,
    relayAuthSettings: AndroidRelayAuthSettings = AndroidRelayAuthSettings.fromBuildConfig(),
    relayAuthRunner: AndroidRelayAuthRunner? = null,
) {
    private val effectiveRelayAuthRunner: AndroidRelayAuthRunner? = relayAuthRunner
        ?: when (relayAuthSettings.mode) {
            AndroidRelayAuthMode.DEMO -> null
            AndroidRelayAuthMode.RELAY -> AndroidKtorRelayAuthRunner.createDefault()
        }

    private val relayAuthHandshakeExecutor = AndroidRelayAuthHandshakeExecutor(
        settings = relayAuthSettings,
        relayAuthRunner = effectiveRelayAuthRunner,
    )
    private val sessionCacheStore = RoomSessionCacheStore.create(application)
    val androidDataLayer = AndroidDataLayer(
        relayAuthHandshake = relayAuthHandshakeExecutor::execute,
        relayAuthStateStore = AndroidSharedPreferencesRelayAuthStateStore(application),
        relayRoutingUsername = relayAuthSettings.relayUsername,
        cacheStoreOverride = sessionCacheStore,
    )
    private val notificationPoster = AndroidNotificationPoster(application)
    private val notificationEventDispatcher = AndroidNotificationEventDispatcher(notificationPoster)
    val pushNotificationPayloadHandler = AndroidPushNotificationPayloadHandler(
        dispatcher = notificationEventDispatcher,
        poster = notificationPoster,
    )
    val supervisorPushEventHandler = AndroidSupervisorPushEventHandler(
        dataLayer = androidDataLayer,
        notificationPayloadHandler = pushNotificationPayloadHandler,
    )
    val pushTokenLifecycleManager = AndroidPushTokenLifecycleManager(
        store = AndroidPushTokenStore(application),
    )
    val routeResyncOrchestrator = AndroidNotificationRouteResyncOrchestrator(
        dataLayer = androidDataLayer,
    )
    val supervisorPushEventCollector = AndroidSupervisorPushEventCollector(
        eventStream = androidDataLayer.relayConnectionClient.supervisorPushEventStream(),
        dispatcher = pushEventDispatcher,
        handleEvent = supervisorPushEventHandler::handle,
    )
    private val observeProjectsUseCase = ObserveProjectsUseCase(androidDataLayer.projectsRepository)
    private val observeInboxUseCase = ObserveInboxUseCase(androidDataLayer.inboxRepository)
    private val sendSessionReplyUseCase = SendSessionReplyUseCase(androidDataLayer.sessionsRepository)
    private val approveRequestUseCase = ApproveRequestUseCase(androidDataLayer.approvalsRepository)
    private val denyRequestUseCase = DenyRequestUseCase(androidDataLayer.approvalsRepository)
    private val answerQuestionUseCase = AnswerQuestionUseCase(androidDataLayer.approvalsRepository)
    private val observeActiveSessionUseCase = ObserveActiveSessionUseCase(
        sessionsRepository = androidDataLayer.sessionsRepository,
        approvalsRepository = androidDataLayer.approvalsRepository,
    )

    fun createSupervisorShellViewModelFactory(): ViewModelProvider.Factory {
        return SupervisorShellViewModel.factory(androidDataLayer)
    }

    fun createRelayLoginViewModelFactory(): ViewModelProvider.Factory {
        return RelayLoginViewModel.factory(androidDataLayer)
    }

    fun createProjectsScreenViewModelFactory(): ViewModelProvider.Factory {
        return ProjectsScreenViewModel.factory(observeProjectsUseCase)
    }

    fun createSessionsScreenViewModelFactory(): ViewModelProvider.Factory {
        return SessionsScreenViewModel.factory(androidDataLayer.sessionsRepository)
    }

    fun createInboxScreenViewModelFactory(): ViewModelProvider.Factory {
        return InboxScreenViewModel.factory(observeInboxUseCase)
    }

    fun createActiveSessionViewModelFactory(): ViewModelProvider.Factory {
        return ActiveSessionViewModel.factory(
            observeActiveSessionUseCase = observeActiveSessionUseCase,
            sendSessionReplyUseCase = sendSessionReplyUseCase,
            approveRequestUseCase = approveRequestUseCase,
            denyRequestUseCase = denyRequestUseCase,
            answerQuestionUseCase = answerQuestionUseCase,
            activeSessionId = androidDataLayer.activeSessionId,
        )
    }
}

class YepAnywhereAndroidApplication : Application() {
    val appContainer: AndroidAppContainer by lazy { AndroidAppContainer(this) }
}
