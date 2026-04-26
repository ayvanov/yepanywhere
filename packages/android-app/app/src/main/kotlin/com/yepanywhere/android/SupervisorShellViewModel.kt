package com.yepanywhere.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yepanywhere.android.data.SupervisorShellDataSource
import com.yepanywhere.android.ui.SupervisorShellScreenState
import com.yepanywhere.android.ui.SupervisorShellSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SupervisorShellViewModel(
    private val dataSource: SupervisorShellDataSource,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val coroutineScope = scope ?: viewModelScope
    private val selectedSection = MutableStateFlow(SupervisorShellSection.PROJECTS)
    private val selectedProjectId = MutableStateFlow<String?>(null)
    private val selectedSessionId = MutableStateFlow<String?>(null)
    private val selectedFilePath = MutableStateFlow<String?>(null)
    private val selectedDeviceId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SupervisorShellScreenState> = combine(
        combine(dataSource.shellState, selectedSection, selectedProjectId) { snapshot, section, projectId ->
            Triple(snapshot, section, projectId)
        },
        selectedSessionId,
        selectedFilePath,
        selectedDeviceId,
    ) { (snapshot, section, projectId), sessionId, filePath, deviceId ->
        SupervisorShellScreenState(
            title = "Yep Anywhere Android",
            subtitle = dataSource.summary,
            snapshot = snapshot,
            selectedSection = section,
            selectedProjectId = projectId,
            selectedSessionId = sessionId,
            selectedFilePath = filePath,
            selectedDeviceId = deviceId,
        )
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = SupervisorShellScreenState(
            title = "Yep Anywhere Android",
            subtitle = dataSource.summary,
            snapshot = dataSource.shellState.value,
            selectedSection = SupervisorShellSection.PROJECTS,
        ),
    )

    fun selectSection(section: SupervisorShellSection) {
        selectedSection.value = section
    }

    fun selectProject(projectId: String) {
        selectedProjectId.value = projectId
        selectedSessionId.value = null
        selectedFilePath.value = null
        selectedDeviceId.value = null
        selectedSection.value = SupervisorShellSection.SESSIONS
    }

    fun selectSession(projectId: String, sessionId: String) {
        selectedProjectId.value = projectId
        selectedSessionId.value = sessionId
        selectedFilePath.value = null
        selectedDeviceId.value = null
        selectedSection.value = SupervisorShellSection.ACTIVE
    }

    fun applyNotificationRoute(route: AndroidNotificationRoute) {
        selectedProjectId.value = route.projectId
        selectedSessionId.value = route.sessionId
        selectedFilePath.value = route.filePath
        selectedDeviceId.value = route.deviceId
        selectSection(route.section)
    }

    companion object {
        fun factory(dataSource: SupervisorShellDataSource): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == SupervisorShellViewModel::class.java)
                    @Suppress("UNCHECKED_CAST")
                    return SupervisorShellViewModel(dataSource = dataSource) as T
                }
            }
        }
    }
}
