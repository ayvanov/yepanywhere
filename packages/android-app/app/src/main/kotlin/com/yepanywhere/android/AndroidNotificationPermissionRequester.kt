package com.yepanywhere.android

import android.content.pm.PackageManager
import android.os.Build

class AndroidNotificationPermissionRequester(
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val checkPermission: () -> Int,
    private val requestPermission: () -> Unit,
) {
    fun requestIfNeeded(): Boolean {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        if (checkPermission() == PackageManager.PERMISSION_GRANTED) {
            return false
        }

        requestPermission()
        return true
    }
}
