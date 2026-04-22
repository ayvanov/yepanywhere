package com.yepanywhere.android

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.yepanywhere.android.data.AndroidDataLayer
import com.yepanywhere.android.core.usecase.ObserveActiveSessionUseCase
import com.yepanywhere.android.core.usecase.ObserveInboxUseCase
import com.yepanywhere.android.core.usecase.ObserveProjectsUseCase
import com.yepanywhere.android.core.usecase.ObserveSessionsUseCase

class AndroidAppContainer {
    val androidDataLayer = AndroidDataLayer()
    private val observeProjectsUseCase = ObserveProjectsUseCase(androidDataLayer.projectsRepository)
    private val observeSessionsUseCase = ObserveSessionsUseCase(androidDataLayer.sessionsRepository)
    private val observeInboxUseCase = ObserveInboxUseCase(androidDataLayer.inboxRepository)
    private val observeActiveSessionUseCase = ObserveActiveSessionUseCase(
        sessionsRepository = androidDataLayer.sessionsRepository,
        approvalsRepository = androidDataLayer.approvalsRepository,
    )

    fun createSupervisorShellViewModelFactory(): ViewModelProvider.Factory {
        return SupervisorShellViewModel.factory(androidDataLayer)
    }

    fun createProjectsScreenViewModelFactory(): ViewModelProvider.Factory {
        return ProjectsScreenViewModel.factory(observeProjectsUseCase)
    }

    fun createSessionsScreenViewModelFactory(): ViewModelProvider.Factory {
        return SessionsScreenViewModel.factory(observeSessionsUseCase)
    }

    fun createInboxScreenViewModelFactory(): ViewModelProvider.Factory {
        return InboxScreenViewModel.factory(observeInboxUseCase)
    }

    fun createActiveSessionViewModelFactory(): ViewModelProvider.Factory {
        return ActiveSessionViewModel.factory(
            observeActiveSessionUseCase = observeActiveSessionUseCase,
            activeSessionId = androidDataLayer.activeSessionId,
        )
    }
}

class YepAnywhereAndroidApplication : Application() {
    val appContainer: AndroidAppContainer by lazy { AndroidAppContainer() }
}
