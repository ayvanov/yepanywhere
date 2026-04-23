package com.yepanywhere.android

import android.content.pm.PackageManager
import android.os.Build
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidNotificationPermissionRequesterTest {
    @Test
    fun requestsPermissionOnAndroidTiramisuAndNewerWhenDenied() {
        var requestCalls = 0
        val requester = AndroidNotificationPermissionRequester(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            checkPermission = { PackageManager.PERMISSION_DENIED },
            requestPermission = { requestCalls += 1 },
        )

        assertTrue(requester.requestIfNeeded())
        assertEquals(1, requestCalls)
    }

    @Test
    fun skipsRequestWhenAlreadyGranted() {
        var requestCalls = 0
        val requester = AndroidNotificationPermissionRequester(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            checkPermission = { PackageManager.PERMISSION_GRANTED },
            requestPermission = { requestCalls += 1 },
        )

        assertFalse(requester.requestIfNeeded())
        assertEquals(0, requestCalls)
    }

    @Test
    fun skipsRequestBeforeAndroidTiramisu() {
        var permissionChecks = 0
        var requestCalls = 0
        val requester = AndroidNotificationPermissionRequester(
            sdkInt = Build.VERSION_CODES.S_V2,
            checkPermission = {
                permissionChecks += 1
                PackageManager.PERMISSION_DENIED
            },
            requestPermission = { requestCalls += 1 },
        )

        assertFalse(requester.requestIfNeeded())
        assertEquals(0, permissionChecks)
        assertEquals(0, requestCalls)
    }
}
