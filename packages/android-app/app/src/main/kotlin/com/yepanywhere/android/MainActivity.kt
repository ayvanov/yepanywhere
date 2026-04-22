package com.yepanywhere.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.yepanywhere.android.ui.SupervisorShellScreen

class MainActivity : ComponentActivity() {
    private val appContainer: AndroidAppContainer
        get() = (application as YepAnywhereAndroidApplication).appContainer

    private val viewModel: SupervisorShellViewModel by viewModels {
        appContainer.createSupervisorShellViewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            YepAnywhereAndroidApp(viewModel = viewModel)
        }
    }
}

@Composable
private fun YepAnywhereAndroidApp(viewModel: SupervisorShellViewModel) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.ensureDemoSessionConnected()
    }

    SupervisorShellScreen(
        state = state,
        onSectionSelected = viewModel::selectSection,
    )
}

