package com.yepanywhere.android

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.yepanywhere.android.data.AndroidDataLayer

class AndroidAppContainer {
    val androidDataLayer = AndroidDataLayer()

    fun createSupervisorShellViewModelFactory(): ViewModelProvider.Factory {
        return SupervisorShellViewModel.factory(androidDataLayer)
    }
}

class YepAnywhereAndroidApplication : Application() {
    val appContainer: AndroidAppContainer by lazy { AndroidAppContainer() }
}
