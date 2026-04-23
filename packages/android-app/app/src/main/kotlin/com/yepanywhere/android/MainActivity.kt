package com.yepanywhere.android

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.yepanywhere.android.ui.ActiveSessionCallbacks
import com.yepanywhere.android.ui.SupervisorShellScreen
import kotlinx.coroutines.Job

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // The poster re-checks permission before notifying, so no state is needed here.
    }

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
    private var foregroundPushEventJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyNotificationRoute(intent)
        requestNotificationPermissionIfNeeded()

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

    override fun onStart() {
        super.onStart()
        if (foregroundPushEventJob?.isActive != true) {
            foregroundPushEventJob = appContainer.supervisorPushEventCollector.start(lifecycleScope)
        }
    }

    override fun onStop() {
        foregroundPushEventJob?.cancel()
        foregroundPushEventJob = null
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyNotificationRoute(intent)
    }

    private fun applyNotificationRoute(intent: Intent?) {
        AndroidNotificationRoute.fromIntent(intent)?.let(shellViewModel::applyNotificationRoute)
    }

    private fun requestNotificationPermissionIfNeeded() {
        AndroidNotificationPermissionRequester(
            checkPermission = { checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) },
            requestPermission = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        ).requestIfNeeded()
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

