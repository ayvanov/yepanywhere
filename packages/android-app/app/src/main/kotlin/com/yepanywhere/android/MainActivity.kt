package com.yepanywhere.android

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.yepanywhere.android.ui.ActiveSessionCallbacks
import com.yepanywhere.android.ui.AndroidAppTheme
import com.yepanywhere.android.ui.SupervisorShellScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    private val relayLoginViewModel: RelayLoginViewModel by viewModels {
        appContainer.createRelayLoginViewModelFactory()
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
                relayLoginViewModel = relayLoginViewModel,
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
        AndroidNotificationRoute.fromIntent(intent)?.let { route ->
            shellViewModel.applyNotificationRoute(route)
            lifecycleScope.launch {
                appContainer.routeResyncOrchestrator.resync(route)
            }
        }
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
    relayLoginViewModel: RelayLoginViewModel,
) {
    val loginState by relayLoginViewModel.uiState.collectAsState()
    val shellState by shellViewModel.uiState.collectAsState()
    val projectsState by projectsViewModel.uiState.collectAsState()
    val sessionsState by sessionsViewModel.uiState.collectAsState()
    val inboxState by inboxViewModel.uiState.collectAsState()
    val activeSessionState by activeSessionViewModel.uiState.collectAsState()
    val activeSessionCallbacks = remember(activeSessionViewModel) {
        createActiveSessionCallbacks(activeSessionViewModel)
    }

    LaunchedEffect(relayLoginViewModel) {
        relayLoginViewModel.initialize()
    }

    if (loginState.isAuthenticated) {
        SupervisorShellScreen(
            state = shellState,
            projectsState = projectsState,
            sessionsState = sessionsState,
            inboxState = inboxState,
            activeSessionState = activeSessionState,
            activeSessionCallbacks = activeSessionCallbacks,
            onSectionSelected = shellViewModel::selectSection,
            onProjectSelected = shellViewModel::selectProject,
            onSessionSelected = shellViewModel::selectSession,
            onLogout = relayLoginViewModel::logout,
        )
    } else {
        RelayLoginScreen(
            state = loginState,
            onRelayUrlChanged = relayLoginViewModel::updateRelayUrl,
            onUsernameChanged = relayLoginViewModel::updateUsername,
            onPasswordChanged = relayLoginViewModel::updatePassword,
            onSubmit = relayLoginViewModel::submitLogin,
        )
    }
}

internal fun createActiveSessionCallbacks(handler: ActiveSessionCommandHandler): ActiveSessionCallbacks {
    return ActiveSessionCallbacks(
        onSendReply = handler::sendReply,
        onApproveRequest = handler::approve,
        onDenyRequest = handler::deny,
        onAnswerQuestion = handler::answerQuestion,
    )
}

@Composable
internal fun RelayLoginScreen(
    state: RelayLoginUiState,
    onRelayUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val inputsEnabled = !state.isSubmitting && !state.isInitializing

    AndroidAppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Relay login",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "Sign in to restore or start a secure relay session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = state.relayUrl,
                        onValueChange = onRelayUrlChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("relay-url-input"),
                        label = { Text("Relay URL") },
                        singleLine = true,
                        enabled = inputsEnabled,
                    )
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = onUsernameChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("identity-input"),
                        label = { Text("Identity") },
                        singleLine = true,
                        enabled = inputsEnabled,
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPasswordChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password-input"),
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = inputsEnabled,
                    )

                    if (state.isInitializing) {
                        CircularProgressIndicator()
                        Text(
                            text = "Trying saved relay session...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    state.errorMessage?.let { errorMessage ->
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onSubmit,
                        enabled = !state.isSubmitting && !state.isInitializing,
                    ) {
                        Text(if (state.isSubmitting) "Signing in..." else "Sign in")
                    }
                }
            }
        }
    }
}

