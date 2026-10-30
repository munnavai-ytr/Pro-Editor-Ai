package com.example.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.ProjectEntity
import com.example.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
  val projects: List<ProjectEntity> = emptyList(),
  val isLoading: Boolean = true,
  val isCreatingProject: Boolean = false,
  val showNewProjectSheet: Boolean = false,
  val draftProjectName: String = "",
  val draftAspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
  val activeOptionsProject: ProjectEntity? = null,
  val renameTargetProject: ProjectEntity? = null,
  val errorMessage: String? = null
)

sealed interface HomeEvent {
  data class NavigateToEditor(val projectId: Long) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
  private val repository: ProjectRepository
) : ViewModel() {

  private val internalState = MutableStateFlow(InternalHomeState())
  private val _events = Channel<HomeEvent>(Channel.BUFFERED)
  val events = _events.receiveAsFlow()

  val uiState: StateFlow<HomeUiState> = combine(
    repository.observeAllProjects(),
    internalState
  ) { projects, local ->
    HomeUiState(
      projects = projects,
      isLoading = false,
      isCreatingProject = local.isCreatingProject,
      showNewProjectSheet = local.showNewProjectSheet,
      draftProjectName = local.draftProjectName,
      draftAspectRatio = local.draftAspectRatio,
      activeOptionsProject = local.activeOptionsProjectId?.let { id ->
        projects.firstOrNull { it.id == id }
      },
      renameTargetProject = local.renameTargetProjectId?.let { id ->
        projects.firstOrNull { it.id == id }
      },
      errorMessage = local.errorMessage
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = HomeUiState()
  )

  fun openNewProjectSheet() {
    val nextNumber = uiState.value.projects.size + 1
    internalState.update {
      it.copy(
        showNewProjectSheet = true,
        draftProjectName = "Project $nextNumber",
        draftAspectRatio = AspectRatio.RATIO_9_16,
        errorMessage = null
      )
    }
  }

  fun dismissNewProjectSheet() {
    if (internalState.value.isCreatingProject) return
    internalState.update { it.copy(showNewProjectSheet = false) }
  }

  fun updateDraftProjectName(name: String) {
    internalState.update { it.copy(draftProjectName = name) }
  }

  fun updateDraftAspectRatio(aspectRatio: AspectRatio) {
    internalState.update { it.copy(draftAspectRatio = aspectRatio) }
  }

  fun onMediaPickedForNewProject(uris: List<Uri>) {
    if (uris.isEmpty()) return
    val current = internalState.value
    if (current.isCreatingProject) return

    viewModelScope.launch {
      internalState.update { it.copy(isCreatingProject = true, errorMessage = null) }
      try {
        val fallbackName = "Project ${uiState.value.projects.size + 1}"
        val projectId = repository.createProjectWithMedia(
          name = current.draftProjectName.ifBlank { fallbackName },
          aspectRatio = current.draftAspectRatio,
          mediaUris = uris
        )
        internalState.update {
          it.copy(
            isCreatingProject = false,
            showNewProjectSheet = false
          )
        }
        _events.send(HomeEvent.NavigateToEditor(projectId))
      } catch (e: Exception) {
        internalState.update {
          it.copy(
            isCreatingProject = false,
            errorMessage = e.localizedMessage ?: "Unable to import selected media."
          )
        }
      }
    }
  }

  fun openProjectOptions(project: ProjectEntity) {
    internalState.update { it.copy(activeOptionsProjectId = project.id) }
  }

  fun dismissProjectOptions() {
    internalState.update { it.copy(activeOptionsProjectId = null) }
  }

  fun requestRenameProject(project: ProjectEntity) {
    internalState.update {
      it.copy(
        activeOptionsProjectId = null,
        renameTargetProjectId = project.id
      )
    }
  }

  fun dismissRenameDialog() {
    internalState.update { it.copy(renameTargetProjectId = null) }
  }

  fun confirmRenameProject(projectId: Long, newName: String) {
    viewModelScope.launch {
      repository.renameProject(projectId, newName)
      internalState.update { it.copy(renameTargetProjectId = null) }
    }
  }

  fun duplicateProject(projectId: Long) {
    viewModelScope.launch {
      internalState.update { it.copy(activeOptionsProjectId = null) }
      repository.duplicateProject(projectId)
    }
  }

  fun deleteProject(projectId: Long) {
    viewModelScope.launch {
      internalState.update { it.copy(activeOptionsProjectId = null) }
      repository.deleteProject(projectId)
    }
  }

  fun clearError() {
    internalState.update { it.copy(errorMessage = null) }
  }

  private data class InternalHomeState(
    val isCreatingProject: Boolean = false,
    val showNewProjectSheet: Boolean = false,
    val draftProjectName: String = "",
    val draftAspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val activeOptionsProjectId: Long? = null,
    val renameTargetProjectId: Long? = null,
    val errorMessage: String? = null
  )
}
