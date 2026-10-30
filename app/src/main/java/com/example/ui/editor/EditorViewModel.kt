package com.example.ui.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.media.FilmstripGenerator
import com.example.data.media.VideoExportManager
import com.example.data.repository.ProjectRepository
import com.example.domain.model.EditorBottomTab
import com.example.domain.model.ExportResolution
import com.example.domain.model.ExportState
import com.example.domain.model.TimelineClipUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveTrimOverride(
  val clipId: Long,
  val trimStartMs: Long,
  val trimEndMs: Long,
  val overlayStartMs: Long? = null
)

data class SeekCommand(
  val targetMs: Long,
  val version: Long
)

data class EditorUiState(
  val project: ProjectEntity? = null,
  val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
  val assets: List<MediaAssetEntity> = emptyList(),
  val clips: List<TimelineClipUiModel> = emptyList(),
  val mainTrackClips: List<TimelineClipUiModel> = emptyList(),
  val overlayTracks: Map<Int, List<TimelineClipUiModel>> = emptyMap(),
  val totalDurationMs: Long = 0L,
  val playheadMs: Long = 0L,
  val isPlaying: Boolean = false,
  val seekCommand: SeekCommand = SeekCommand(0L, 0L),
  val zoomScale: Float = 1.0f, // 0.4f..3.2f
  val selectedClipId: Long? = null,
  val selectedClip: TimelineClipUiModel? = null,
  val activeTab: EditorBottomTab = EditorBottomTab.EDIT,
  val showSpeedPicker: Boolean = false,
  val showRenameDialog: Boolean = false,
  val isImportingMedia: Boolean = false,
  val exportState: ExportState = ExportState.Idle,
  val snackbarMessage: String? = null
)

@HiltViewModel
class EditorViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val repository: ProjectRepository,
  private val exportManager: VideoExportManager,
  val filmstripGenerator: FilmstripGenerator
) : ViewModel() {

  val projectId: Long = checkNotNull(savedStateHandle["projectId"])

  private val localState = MutableStateFlow(InternalEditorState())
  private var seekCounter = 0L

  val uiState: StateFlow<EditorUiState> = combine(
    repository.observeProject(projectId),
    repository.observeAssets(projectId),
    repository.observeTimelineClips(projectId),
    localState
  ) { project, assets, rawClips, local ->
    val mergedClips = applyLiveOverrides(rawClips, local.liveTrim, local.liveOverlayMove)
    val mainClips = mergedClips
      .filter { it.trackIndex == 0 }
      .sortedWith(compareBy({ it.orderIndex }, { it.id }))
    val overlays = mergedClips
      .filter { it.trackIndex > 0 }
      .groupBy { it.trackIndex }
      .toSortedMap(compareByDescending { it }) // Higher track indices rendered higher visually

    val mainEnd = mainClips.lastOrNull()?.endMs ?: 0L
    val overlayEnd = mergedClips.filter { it.trackIndex > 0 }.maxOfOrNull { it.endMs } ?: 0L
    val computedTotalMs = maxOf(mainEnd, overlayEnd)

    val clampedPlayhead = if (computedTotalMs > 0L) {
      local.playheadMs.coerceIn(0L, computedTotalMs)
    } else {
      0L
    }

    val selected = mergedClips.firstOrNull { it.id == local.selectedClipId }

    EditorUiState(
      project = project,
      aspectRatio = project?.let { AspectRatio.fromString(it.aspectRatio) } ?: AspectRatio.RATIO_9_16,
      assets = assets,
      clips = mergedClips,
      mainTrackClips = mainClips,
      overlayTracks = overlays,
      totalDurationMs = computedTotalMs,
      playheadMs = clampedPlayhead,
      isPlaying = local.isPlaying && mainClips.isNotEmpty(),
      seekCommand = local.seekCommand,
      zoomScale = local.zoomScale,
      selectedClipId = selected?.id,
      selectedClip = selected,
      activeTab = local.activeTab,
      showSpeedPicker = local.showSpeedPicker && selected != null,
      showRenameDialog = local.showRenameDialog,
      isImportingMedia = local.isImportingMedia,
      exportState = local.exportState,
      snackbarMessage = local.snackbarMessage
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = EditorUiState()
  )

  private fun applyLiveOverrides(
    rawClips: List<TimelineClipUiModel>,
    liveTrim: LiveTrimOverride?,
    liveOverlayMove: Pair<Long, Long>?
  ): List<TimelineClipUiModel> {
    if (liveTrim == null && liveOverlayMove == null) return rawClips

    val withOverrides = rawClips.map { clip ->
      var updated = clip
      if (liveTrim != null && clip.id == liveTrim.clipId) {
        updated = updated.copy(
          trimStartMs = liveTrim.trimStartMs,
          trimEndMs = liveTrim.trimEndMs,
          startMs = liveTrim.overlayStartMs ?: updated.startMs
        )
      }
      if (liveOverlayMove != null && clip.id == liveOverlayMove.first && clip.trackIndex > 0) {
        updated = updated.copy(startMs = liveOverlayMove.second.coerceAtLeast(0L))
      }
      updated
    }

    // Recompute contiguous startMs on main track (trackIndex == 0)
    var cursor = 0L
    return withOverrides.map { clip ->
      if (clip.trackIndex == 0) {
        val repositioned = clip.copy(startMs = cursor)
        cursor += repositioned.timelineDurationMs
        repositioned
      } else {
        clip
      }
    }
  }

  fun togglePlayPause() {
    val current = uiState.value
    if (current.mainTrackClips.isEmpty()) return

    if (current.isPlaying) {
      localState.update { it.copy(isPlaying = false) }
    } else {
      // If playhead is at the very end, restart from 0
      if (current.playheadMs >= (current.totalDurationMs - 40L).coerceAtLeast(0L)) {
        val nextVersion = ++seekCounter
        localState.update {
          it.copy(
            playheadMs = 0L,
            seekCommand = SeekCommand(0L, nextVersion),
            isPlaying = true
          )
        }
      } else {
        localState.update { it.copy(isPlaying = true) }
      }
    }
  }

  fun pausePlayback() {
    if (localState.value.isPlaying) {
      localState.update { it.copy(isPlaying = false) }
    }
  }

  fun onExoPlayerPositionUpdated(timelineMs: Long) {
    val maxDur = uiState.value.totalDurationMs
    val clamped = if (maxDur > 0L) timelineMs.coerceIn(0L, maxDur) else 0L
    localState.update { it.copy(playheadMs = clamped) }
  }

  fun onExoPlayerPlaybackEnded() {
    val endMs = uiState.value.totalDurationMs
    localState.update {
      it.copy(
        isPlaying = false,
        playheadMs = endMs
      )
    }
  }

  fun scrubTo(targetMs: Long, pauseIfPlaying: Boolean = true) {
    val maxDur = uiState.value.totalDurationMs
    val clamped = if (maxDur > 0L) targetMs.coerceIn(0L, maxDur) else 0L
    val nextVersion = ++seekCounter
    localState.update {
      it.copy(
        isPlaying = if (pauseIfPlaying) false else it.isPlaying,
        playheadMs = clamped,
        seekCommand = SeekCommand(clamped, nextVersion)
      )
    }
  }

  fun updateZoom(multiplierDelta: Float) {
    localState.update {
      val nextZoom = (it.zoomScale * multiplierDelta).coerceIn(0.4f, 3.2f)
      it.copy(zoomScale = nextZoom)
    }
  }

  fun setZoomExact(scale: Float) {
    localState.update { it.copy(zoomScale = scale.coerceIn(0.4f, 3.2f)) }
  }

  fun selectClip(clipId: Long?) {
    localState.update {
      it.copy(
        selectedClipId = clipId,
        activeTab = if (clipId != null) EditorBottomTab.EDIT else it.activeTab,
        showSpeedPicker = if (clipId == null) false else it.showSpeedPicker
      )
    }
  }

  fun selectBottomTab(tab: EditorBottomTab) {
    localState.update {
      it.copy(
        activeTab = tab,
        showSpeedPicker = false
      )
    }
  }

  // Live Trim Gestures
  fun onLiveTrimUpdate(
    clipId: Long,
    newTrimStartMs: Long,
    newTrimEndMs: Long,
    isLeftHandle: Boolean,
    newOverlayStartMs: Long? = null
  ) {
    val clip = uiState.value.clips.firstOrNull { it.id == clipId } ?: return
    val safeStart = newTrimStartMs.coerceIn(0L, (clip.sourceDurationMs - 200L).coerceAtLeast(0L))
    val safeEnd = newTrimEndMs.coerceIn(safeStart + 200L, clip.sourceDurationMs.coerceAtLeast(200L))

    val nextVersion = ++seekCounter
    localState.update { state ->
      val override = LiveTrimOverride(
        clipId = clipId,
        trimStartMs = safeStart,
        trimEndMs = safeEnd,
        overlayStartMs = newOverlayStartMs
      )
      val previewEdgeMs = if (clip.trackIndex == 0) {
        // Compute updated clip start/end on main track so preview shows exact trimmed edge
        val updatedMain = applyLiveOverrides(uiState.value.clips, override, null)
          .firstOrNull { it.id == clipId }
        if (updatedMain != null) {
          if (isLeftHandle) updatedMain.startMs else (updatedMain.endMs - 20L).coerceAtLeast(updatedMain.startMs)
        } else {
          state.playheadMs
        }
      } else {
        val ovStart = newOverlayStartMs ?: clip.startMs
        val ovDur = ((safeEnd - safeStart) / clip.speed).toLong().coerceAtLeast(100L)
        if (isLeftHandle) ovStart else (ovStart + ovDur - 20L).coerceAtLeast(ovStart)
      }

      state.copy(
        isPlaying = false,
        liveTrim = override,
        playheadMs = previewEdgeMs,
        seekCommand = SeekCommand(previewEdgeMs, nextVersion)
      )
    }
  }

  fun commitLiveTrim() {
    val activeTrim = localState.value.liveTrim ?: return
    viewModelScope.launch {
      repository.trimClip(
        projectId = projectId,
        clipId = activeTrim.clipId,
        newTrimStartMs = activeTrim.trimStartMs,
        newTrimEndMs = activeTrim.trimEndMs,
        newStartMsForOverlay = activeTrim.overlayStartMs
      )
      localState.update { it.copy(liveTrim = null) }
    }
  }

  // Live Overlay Horizontal Drag Positioning
  fun onLiveOverlayMove(clipId: Long, newStartMs: Long) {
    localState.update {
      it.copy(
        isPlaying = false,
        liveOverlayMove = clipId to newStartMs.coerceAtLeast(0L)
      )
    }
  }

  fun commitLiveOverlayMove() {
    val move = localState.value.liveOverlayMove ?: return
    viewModelScope.launch {
      repository.moveOverlayClipPosition(
        projectId = projectId,
        clipId = move.first,
        newStartMs = move.second
      )
      localState.update { it.copy(liveOverlayMove = null) }
    }
  }

  fun reorderMainTrackClip(clipId: Long, targetOrderIndex: Int) {
    viewModelScope.launch {
      pausePlayback()
      repository.reorderMainTrackClip(projectId, clipId, targetOrderIndex)
    }
  }

  // Contextual Clip Actions
  fun splitSelectedClipAtPlayhead() {
    val current = uiState.value
    val selected = current.selectedClip ?: return
    val playhead = current.playheadMs

    if (playhead < selected.startMs + 150L || playhead > selected.endMs - 150L) {
      showMessage("Move the playhead inside the selected clip to split.")
      return
    }

    viewModelScope.launch {
      pausePlayback()
      val splitIds = repository.splitClipAtPosition(
        projectId = projectId,
        clipId = selected.id,
        playheadMs = playhead
      )
      if (splitIds != null) {
        localState.update { it.copy(selectedClipId = splitIds.second) }
      } else {
        showMessage("Clip is too short at playhead to split further.")
      }
    }
  }

  fun duplicateSelectedClip() {
    val selected = uiState.value.selectedClip ?: return
    viewModelScope.launch {
      pausePlayback()
      val newClipId = repository.duplicateClip(projectId, selected.id)
      if (newClipId != null) {
        localState.update { it.copy(selectedClipId = newClipId) }
      }
    }
  }

  fun deleteSelectedClip() {
    val selected = uiState.value.selectedClip ?: return
    viewModelScope.launch {
      pausePlayback()
      repository.deleteClip(projectId, selected.id)
      localState.update {
        it.copy(
          selectedClipId = null,
          showSpeedPicker = false
        )
      }
    }
  }

  fun toggleSpeedPicker() {
    if (uiState.value.selectedClip == null) return
    localState.update { it.copy(showSpeedPicker = !it.showSpeedPicker) }
  }

  fun setSelectedClipSpeed(speed: Float) {
    val selected = uiState.value.selectedClip ?: return
    viewModelScope.launch {
      pausePlayback()
      repository.updateClipSpeed(projectId, selected.id, speed)
    }
  }

  // Media Addition (Main Track & Overlay Track)
  fun addPickedMedia(uris: List<Uri>, asOverlay: Boolean, overlayTrackIndex: Int = 1) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      pausePlayback()
      localState.update { it.copy(isImportingMedia = true) }
      try {
        val insertedClipId = repository.addMediaToTrack(
          projectId = projectId,
          uris = uris,
          asOverlay = asOverlay,
          overlayStartMs = uiState.value.playheadMs,
          overlayTrackIndex = overlayTrackIndex
        )
        localState.update {
          it.copy(
            isImportingMedia = false,
            selectedClipId = insertedClipId ?: it.selectedClipId,
            activeTab = EditorBottomTab.EDIT
          )
        }
      } catch (e: Exception) {
        localState.update { it.copy(isImportingMedia = false) }
        showMessage(e.localizedMessage ?: "Failed to add media.")
      }
    }
  }

  fun addExistingAsset(assetId: Long, asOverlay: Boolean) {
    viewModelScope.launch {
      pausePlayback()
      val nextOverlayTrack = if (asOverlay) {
        findAvailableOverlayTrackAt(uiState.value.playheadMs)
      } else {
        1
      }
      val insertedId = repository.addExistingAssetToTrack(
        projectId = projectId,
        assetId = assetId,
        asOverlay = asOverlay,
        overlayStartMs = uiState.value.playheadMs,
        overlayTrackIndex = nextOverlayTrack
      )
      if (insertedId != null) {
        localState.update {
          it.copy(
            selectedClipId = insertedId,
            activeTab = EditorBottomTab.EDIT
          )
        }
      }
    }
  }

  private fun findAvailableOverlayTrackAt(playheadMs: Long): Int {
    val overlays = uiState.value.overlayTracks
    var candidate = 1
    while (candidate <= 4) {
      val clipsInLane = overlays[candidate].orEmpty()
      val overlaps = clipsInLane.any { playheadMs in it.startMs until it.endMs }
      if (!overlaps) return candidate
      candidate++
    }
    return 1
  }

  // Project Title & Aspect Ratio Updates
  fun openRenameDialog() {
    localState.update { it.copy(showRenameDialog = true) }
  }

  fun dismissRenameDialog() {
    localState.update { it.copy(showRenameDialog = false) }
  }

  fun updateProjectName(newName: String) {
    viewModelScope.launch {
      repository.renameProject(projectId, newName)
      localState.update { it.copy(showRenameDialog = false) }
    }
  }

  fun cycleAspectRatio() {
    val current = uiState.value.aspectRatio
    val entries = AspectRatio.entries
    val next = entries[(entries.indexOf(current) + 1) % entries.size]
    viewModelScope.launch {
      repository.updateProjectAspectRatio(projectId, next)
    }
  }

  // Export Flow (Media3 Transformer)
  fun openExportSheet() {
    if (uiState.value.mainTrackClips.isEmpty()) {
      showMessage("Add at least one clip to the main video track before exporting.")
      return
    }
    pausePlayback()
    localState.update {
      it.copy(exportState = ExportState.Configuring(ExportResolution.RES_1080P))
    }
  }

  fun selectExportResolution(resolution: ExportResolution) {
    val cur = localState.value.exportState
    if (cur is ExportState.Configuring) {
      localState.update { it.copy(exportState = ExportState.Configuring(resolution)) }
    }
  }

  fun startExport(resolution: ExportResolution) {
    val snapshot = uiState.value
    val projectName = snapshot.project?.name ?: "PlayNexa"
    pausePlayback()

    localState.update {
      it.copy(exportState = ExportState.Exporting(progressPercent = 0, resolution = resolution))
    }

    exportManager.startExport(
      projectName = projectName,
      aspectRatio = snapshot.aspectRatio,
      resolution = resolution,
      clips = snapshot.clips,
      onProgress = { pct ->
        localState.update { state ->
          if (state.exportState is ExportState.Exporting) {
            state.copy(exportState = ExportState.Exporting(pct, resolution))
          } else {
            state
          }
        }
      },
      onCompleted = { outputUri, displayFileName ->
        localState.update {
          it.copy(
            exportState = ExportState.Success(
              outputUri = outputUri,
              displayFileName = displayFileName,
              resolution = resolution
            )
          )
        }
      },
      onError = { errorMsg ->
        localState.update {
          it.copy(exportState = ExportState.Error(errorMsg))
        }
      }
    )
  }

  fun cancelOrCloseExport() {
    if (localState.value.exportState is ExportState.Exporting) {
      exportManager.cancelExport()
    }
    localState.update { it.copy(exportState = ExportState.Idle) }
  }

  fun showMessage(msg: String) {
    localState.update { it.copy(snackbarMessage = msg) }
  }

  fun clearMessage() {
    localState.update { it.copy(snackbarMessage = null) }
  }

  override fun onCleared() {
    exportManager.cancelExport()
    super.onCleared()
  }

  private data class InternalEditorState(
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val seekCommand: SeekCommand = SeekCommand(0L, 0L),
    val zoomScale: Float = 1.0f,
    val selectedClipId: Long? = null,
    val activeTab: EditorBottomTab = EditorBottomTab.EDIT,
    val showSpeedPicker: Boolean = false,
    val showRenameDialog: Boolean = false,
    val isImportingMedia: Boolean = false,
    val liveTrim: LiveTrimOverride? = null,
    val liveOverlayMove: Pair<Long, Long>? = null,
    val exportState: ExportState = ExportState.Idle,
    val snackbarMessage: String? = null
  )
}
