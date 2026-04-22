package com.yepanywhere.android

import android.app.Application
import com.yepanywhere.android.data.AndroidDataLayer

class AndroidAppContainer {
    val androidDataLayer = AndroidDataLayer()
}

class YepAnywhereAndroidApplication : Application() {
    val appContainer: AndroidAppContainer by lazy { AndroidAppContainer() }
}
