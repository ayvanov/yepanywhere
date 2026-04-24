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

    val uiState: StateFlow<SupervisorShellScreenState> = combine(
        dataSource.shellState,
        selectedSection,
        selectedProjectId,
    ) { snapshot, section, projectId ->
        SupervisorShellScreenState(
            title = "Yep Anywhere Android",
            subtitle = dataSource.summary,
            snapshot = snapshot,
            selectedSection = section,
            selectedProjectId = projectId,
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
        selectedSection.value = SupervisorShellSection.SESSIONS
    }

    fun applyNotificationRoute(route: AndroidNotificationRoute) {
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
