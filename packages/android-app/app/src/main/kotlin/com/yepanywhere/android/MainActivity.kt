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
import androidx.compose.runtime.remember
import com.yepanywhere.android.ui.ActiveSessionCallbacks
import com.yepanywhere.android.ui.SupervisorShellScreen

class MainActivity : ComponentActivity() {
    private val appContainer: AndroidAppContainer
        get() = (application as YepAnywhereAndroidApplication).appContainer

    private val shellViewModel: SupervisorShellViewModel by viewModels {
        appContainer.createSupervisorShellViewModelFactory()
    }
    private val projectsViewModel: ProjectsScreenViewModel by viewModels {
        appContainer.createProjectsScreenViewModelFactory()
    }
    private val sessionsViewModel: SessionsScreenViewModel by viewModels {
        appContainer.createSessionsScreenViewModelFactory()
    }
    private val inboxViewModel: InboxScreenViewModel by viewModels {
        appContainer.createInboxScreenViewModelFactory()
    }
    private val activeSessionViewModel: ActiveSessionViewModel by viewModels {
        appContainer.createActiveSessionViewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            YepAnywhereAndroidApp(
                shellViewModel = shellViewModel,
                projectsViewModel = projectsViewModel,
                sessionsViewModel = sessionsViewModel,
                inboxViewModel = inboxViewModel,
                activeSessionViewModel = activeSessionViewModel,
            )
        }
    }
}

@Composable
private fun YepAnywhereAndroidApp(
    shellViewModel: SupervisorShellViewModel,
    projectsViewModel: ProjectsScreenViewModel,
    sessionsViewModel: SessionsScreenViewModel,
    inboxViewModel: InboxScreenViewModel,
    activeSessionViewModel: ActiveSessionViewModel,
) {
    val shellState by shellViewModel.uiState.collectAsState()
    val projectsState by projectsViewModel.uiState.collectAsState()
    val sessionsState by sessionsViewModel.uiState.collectAsState()
    val inboxState by inboxViewModel.uiState.collectAsState()
    val activeSessionState by activeSessionViewModel.uiState.collectAsState()
    val activeSessionCallbacks = remember(activeSessionViewModel) {
        createActiveSessionCallbacks(activeSessionViewModel)
    }

    LaunchedEffect(shellViewModel) {
        shellViewModel.ensureDemoSessionConnected()
    }

    SupervisorShellScreen(
        state = shellState,
        projectsState = projectsState,
        sessionsState = sessionsState,
        inboxState = inboxState,
        activeSessionState = activeSessionState,
        activeSessionCallbacks = activeSessionCallbacks,
        onSectionSelected = shellViewModel::selectSection,
    )
}

internal fun createActiveSessionCallbacks(handler: ActiveSessionCommandHandler): ActiveSessionCallbacks {
    return ActiveSessionCallbacks(
        onSendReply = handler::sendReply,
        onApproveRequest = handler::approve,
        onDenyRequest = handler::deny,
        onAnswerQuestion = handler::answerQuestion,
    )
}

