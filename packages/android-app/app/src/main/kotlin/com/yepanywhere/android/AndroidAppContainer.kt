package com.yepanywhere.android

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.yepanywhere.android.data.AndroidDataLayer

class AndroidAppContainer {
    val androidDataLayer = AndroidDataLayer()

    fun createSupervisorShellViewModelFactory(): ViewModelProvider.Factory {
        return SupervisorShellViewModel.factory(androidDataLayer)
    }

    fun createProjectsScreenViewModelFactory(): ViewModelProvider.Factory {
        return ProjectsScreenViewModel.factory(androidDataLayer)
    }

    fun createSessionsScreenViewModelFactory(): ViewModelProvider.Factory {
        return SessionsScreenViewModel.factory(androidDataLayer)
    }

    fun createInboxScreenViewModelFactory(): ViewModelProvider.Factory {
        return InboxScreenViewModel.factory(androidDataLayer)
    }

    fun createActiveSessionViewModelFactory(): ViewModelProvider.Factory {
        return ActiveSessionViewModel.factory(androidDataLayer)
    }
}

class YepAnywhereAndroidApplication : Application() {
    val appContainer: AndroidAppContainer by lazy { AndroidAppContainer() }
}
