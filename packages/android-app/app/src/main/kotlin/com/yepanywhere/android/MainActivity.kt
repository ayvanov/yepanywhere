package com.yepanywhere.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.yepanywhere.android.data.AndroidDataLayer
import com.yepanywhere.android.ui.SupervisorShellScreen

class MainActivity : ComponentActivity() {
    private val dataLayer: AndroidDataLayer
        get() = (application as YepAnywhereAndroidApplication).appContainer.androidDataLayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            YepAnywhereAndroidApp(dataLayer = dataLayer)
        }
    }
}

@Composable
private fun YepAnywhereAndroidApp(dataLayer: AndroidDataLayer) {
    val snapshot by dataLayer.shellState.collectAsState()

    LaunchedEffect(dataLayer) {
        dataLayer.connectDemoSession()
    }

    SupervisorShellScreen(
        snapshot = snapshot,
        dataLayerSummary = AndroidDataLayer.summary,
    )
}

