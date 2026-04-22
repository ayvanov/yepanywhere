package com.yepanywhere.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.yepanywhere.android.data.AndroidDataLayer
import com.yepanywhere.android.ui.SupervisorShellScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            YepAnywhereAndroidApp()
        }
    }
}

@Composable
private fun YepAnywhereAndroidApp() {
    SupervisorShellScreen(
        snapshot = AndroidDataLayer.previewSnapshot,
        dataLayerSummary = AndroidDataLayer.summary,
    )
}

