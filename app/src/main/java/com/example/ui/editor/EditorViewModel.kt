package com.example.ui.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.KeyframeEntity
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.local.entity.OverlayTransformEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.TextAlignOption
import com.example.data.local.entity.TrackType
import com.example.data.local.entity.TransitionType
import com.example.data.media.FilmstripGenerator
import com.example.data.media.TextBitmapRenderer
import com.example.data.media.VideoExportManager
import com.example.data.repository.ProjectRepository
import com.example.domain.model.ClipEditToolSubmode
import com.example.domain.model.EditorBottomTab
import com.example.domain.model.ExportResolution
import com.example.domain.model.ExportState
import com.example.domain.model.KeyframeInterpolator
import com.example.domain.model.ProjectStateSnapshot
import com.example.domain.model.TimelineClipUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
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

data class LiveLayerTransformOverride(
  val clipId: Long,
  val localTimeMs: Long,
  val offsetX: Float,
  val offsetY: Float,
  val scale: Float,
  val rotationDeg: Float,
  val opacity: Float
)

data class LiveColorOverride(
  val clipId: Long,
  val brightness: Float,
  val contrast: Float,
  val saturation: Float
)

data class LiveCropOverride(
  val clipId: Long,
  val cropLeft: Float,
  val cropTop: Float,
  val cropRight: Float,
  val cropBottom: Float
)

data class SeekCommand(
  val targetMs: Long,
  val version: Long
)

data class EditorUiState(
  val project: ProjectEntity? = null,
  val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
  val isOriginalAudioMuted: Boolean = false,
  val assets: List<MediaAssetEntity> = emptyList(),
  val clips: List<TimelineClipUiModel> = emptyList(),
  val mainTrackClips: List<TimelineClipUiModel> = emptyList(),
  val overlayTracks: Map<Int, List<TimelineClipUiModel>> = emptyMap(),
  val textTrackClips: List<TimelineClipUiModel> = emptyList(),
  val audioTrackClips: List<TimelineClipUiModel> = emptyList(),
  val totalDurationMs: Long = 0L,
  val playheadMs: Long = 0L,
  val isPlaying: Boolean = false,
  val seekCommand: SeekCommand = SeekCommand(0L, 0L),
  val zoomScale: Float = 1.0f, // 0.4f..3.2f
  val selectedClipId: Long? = null,
  val selectedClip: TimelineClipUiModel? = null,
  val activeTab: EditorBottomTab = EditorBottomTab.EDIT,
  val activeSubmode: ClipEditToolSubmode = ClipEditToolSubmode.NONE,
  val isInteractiveCropMode: Boolean = false,
  val showTextEditorDialog: Boolean = false,
  val editingTextClipId: Long? = null,
  val showRenameDialog: Boolean = false,
  val isImportingMedia: Boolean = false,
  val canUndo: Boolean = false,
  val canRedo: Boolean = false,
  val exportState: ExportState = ExportState.Idle,
  val snackbarMessage: String? = null
)

@HiltViewModel
class EditorViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val repository: ProjectRepository,
  private val exportManager: VideoExportManager,
  val filmstripGenerator: FilmstripGenerator,
  val textBitmapRenderer: TextBitmapRenderer
) : ViewModel() {

  companion object {
    private const val MAX_UNDO_HISTORY = 35
  }

  val projectId: Long = checkNotNull(savedStateHandle["projectId"])

  private val localState = MutableStateFlow(InternalEditorState())
  private var seekCounter = 0L

  private val undoStack = ArrayDeque<ProjectStateSnapshot>()
  private val redoStack = ArrayDeque<ProjectStateSnapshot>()
  private var gestureStartSnapshot: ProjectStateSnapshot? = null
  private var isCapturingGestureSnapshot = false

  val uiState: StateFlow<EditorUiState> = combine(
    repository.observeProject(projectId),
    repository.observeAssets(projectId),
    repository.observeTimelineClips(projectId),
    localState
  ) { project, assets, rawClips, local ->
    val mergedClips = applyLiveOverrides(rawClips, local)

    val mainClips = mergedClips
      .filter { it.trackType == TrackType.VIDEO && it.trackIndex == 0 }
      .sortedWith(compareBy({ it.orderIndex }, { it.id }))

    val overlays = mergedClips
      .filter { it.trackType == TrackType.OVERLAY && it.trackIndex > 0 }
      .groupBy { it.trackIndex }
      .toSortedMap(compareByDescending { it })

    val textClips = mergedClips
      .filter { it.trackType == TrackType.TEXT }
      .sortedWith(compareBy({ it.startMs }, { it.orderIndex }, { it.id }))

    val audioClips = mergedClips
      .filter { it.trackType == TrackType.AUDIO }
      .sortedWith(compareBy({ it.startMs }, { it.orderIndex }, { it.id }))

    val mainEnd = mainClips.lastOrNull()?.endMs ?: 0L
    val nonMainEnd = mergedClips
      .filter { !(it.trackType == TrackType.VIDEO && it.trackIndex == 0) }
      .maxOfOrNull { it.endMs } ?: 0L
    val computedTotalMs = maxOf(mainEnd, nonMainEnd)

    val clampedPlayhead = if (computedTotalMs > 0L) {
      local.playheadMs.coerceIn(0L, computedTotalMs)
    } else {
      0L
    }

    val selected = mergedClips.firstOrNull { it.id == local.selectedClipId }

    EditorUiState(
      project = project,
      aspectRatio = project?.let { AspectRatio.fromString(it.aspectRatio) } ?: AspectRatio.RATIO_9_16,
      isOriginalAudioMuted = project?.isOriginalAudioMuted == true,
      assets = assets,
      clips = mergedClips,
      mainTrackClips = mainClips,
      overlayTracks = overlays,
      textTrackClips = textClips,
      audioTrackClips = audioClips,
      totalDurationMs = computedTotalMs,
      playheadMs = clampedPlayhead,
      isPlaying = local.isPlaying && mainClips.isNotEmpty(),
      seekCommand = local.seekCommand,
      zoomScale = local.zoomScale,
      selectedClipId = selected?.id,
      selectedClip = selected,
      activeTab = local.activeTab,
      activeSubmode = if (selected != null) local.activeSubmode else ClipEditToolSubmode.NONE,
      isInteractiveCropMode = local.isInteractiveCropMode &&
        selected != null &&
        (selected.trackType == TrackType.VIDEO || selected.trackType == TrackType.OVERLAY),
      showTextEditorDialog = local.showTextEditorDialog,
      editingTextClipId = local.editingTextClipId,
      showRenameDialog = local.showRenameDialog,
      isImportingMedia = local.isImportingMedia,
      canUndo = local.canUndo,
      canRedo = local.canRedo,
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
    local: InternalEditorState
  ): List<TimelineClipUiModel> {
    val hasAnyOverride = local.liveTrim != null ||
      local.liveNonMainMove != null ||
      local.liveLayerTransform != null ||
      local.liveVolume != null ||
      local.liveColor != null ||
      local.liveCrop != null

    if (!hasAnyOverride) return rawClips

    val withOverrides = rawClips.map { clip ->
      var updated = clip

      local.liveTrim?.let { trim ->
        if (clip.id == trim.clipId) {
          updated = updated.copy(
            trimStartMs = trim.trimStartMs,
            trimEndMs = trim.trimEndMs,
            startMs = trim.overlayStartMs ?: updated.startMs
          )
        }
      }

      local.liveNonMainMove?.let { (moveId, targetStartMs) ->
        if (clip.id == moveId && !(clip.trackType == TrackType.VIDEO && clip.trackIndex == 0)) {
          updated = updated.copy(startMs = targetStartMs.coerceAtLeast(0L))
        }
      }

      local.liveVolume?.let { (volClipId, vol) ->
        if (clip.id == volClipId) {
          updated = updated.copy(volume = vol.coerceIn(0f, 2f))
        }
      }

      local.liveColor?.let { col ->
        if (clip.id == col.clipId) {
          updated = updated.copy(
            brightness = col.brightness,
            contrast = col.contrast,
            saturation = col.saturation
          )
        }
      }

      local.liveCrop?.let { crop ->
        if (clip.id == crop.clipId) {
          updated = updated.copy(
            cropLeft = crop.cropLeft,
            cropTop = crop.cropTop,
            cropRight = crop.cropRight,
            cropBottom = crop.cropBottom
          )
        }
      }

      local.liveLayerTransform?.let { lt ->
        if (clip.id == lt.clipId) {
          when (clip.trackType) {
            TrackType.OVERLAY -> {
              val baseOv = updated.overlayTransform ?: OverlayTransformEntity(timelineClipId = clip.id)
              val nextOv = baseOv.copy(
                offsetX = lt.offsetX,
                offsetY = lt.offsetY,
                scale = lt.scale,
                rotationDeg = lt.rotationDeg,
                opacity = lt.opacity
              )
              val nextKfs = mergeVirtualKeyframe(
                existing = updated.keyframes,
                localTimeMs = lt.localTimeMs,
                overlayTransformId = baseOv.id,
                textOverlayId = null,
                lt = lt
              )
              updated = updated.copy(overlayTransform = nextOv, keyframes = nextKfs)
            }
            TrackType.TEXT -> {
              val baseTx = updated.textOverlay
              if (baseTx != null) {
                val nextTx = baseTx.copy(
                  offsetX = lt.offsetX,
                  offsetY = lt.offsetY,
                  scale = lt.scale,
                  rotationDeg = lt.rotationDeg,
                  opacity = lt.opacity
                )
                val nextKfs = mergeVirtualKeyframe(
                  existing = updated.keyframes,
                  localTimeMs = lt.localTimeMs,
                  overlayTransformId = null,
                  textOverlayId = baseTx.id,
                  lt = lt
                )
                updated = updated.copy(textOverlay = nextTx, keyframes = nextKfs)
              }
            }
            else -> Unit
          }
        }
      }

      updated
    }

    var cursor = 0L
    return withOverrides.map { clip ->
      if (clip.trackType == TrackType.VIDEO && clip.trackIndex == 0) {
        val repositioned = clip.copy(startMs = cursor)
        cursor += repositioned.timelineDurationMs
        repositioned
      } else {
        clip
      }
    }
  }

  private fun mergeVirtualKeyframe(
    existing: List<KeyframeEntity>,
    localTimeMs: Long,
    overlayTransformId: Long?,
    textOverlayId: Long?,
    lt: LiveLayerTransformOverride
  ): List<KeyframeEntity> {
    if (existing.isEmpty()) return emptyList()
    val filtered = existing.filterNot { abs(it.timeMs - localTimeMs) <= 65L }
    val virtualKf = KeyframeEntity(
      id = -999L,
      overlayTransformId = overlayTransformId,
      textOverlayId = textOverlayId,
      timeMs = localTimeMs,
      positionX = lt.offsetX,
      positionY = lt.offsetY,
      scale = lt.scale,
      rotationDeg = lt.rotationDeg,
      opacity = lt.opacity
    )
    return (filtered + virtualKf).sortedBy { it.timeMs }
  }

  // --- Undo / Redo Command Infrastructure ---
  private fun pushSnapshotToUndo(snapshot: ProjectStateSnapshot) {
    if (undoStack.size >= MAX_UNDO_HISTORY) {
      undoStack.removeFirst()
    }
    undoStack.addLast(snapshot)
    redoStack.clear()
    syncHistoryFlags()
  }

  private fun syncHistoryFlags() {
    localState.update {
      it.copy(
        canUndo = undoStack.isNotEmpty(),
        canRedo = redoStack.isNotEmpty()
      )
    }
  }

  private suspend fun executeUndoableEdit(action: suspend () -> Unit) {
    val before = repository.captureProjectSnapshot(projectId)
    action()
    if (before != null) {
      pushSnapshotToUndo(before)
    }
  }

  private fun ensureGestureStartSnapshot() {
    if (gestureStartSnapshot != null || isCapturingGestureSnapshot) return
    isCapturingGestureSnapshot = true
    viewModelScope.launch {
      gestureStartSnapshot = repository.captureProjectSnapshot(projectId)
      isCapturingGestureSnapshot = false
    }
  }

  private suspend fun consumeOrCapturePreGestureSnapshot(): ProjectStateSnapshot? {
    val captured = gestureStartSnapshot
    gestureStartSnapshot = null
    return captured ?: repository.captureProjectSnapshot(projectId)
  }

  fun undo() {
    if (undoStack.isEmpty()) return
    viewModelScope.launch {
      pausePlayback()
      val current = repository.captureProjectSnapshot(projectId)
      val target = undoStack.removeLastOrNull() ?: return@launch
      if (current != null) {
        redoStack.addLast(current)
      }
      repository.restoreProjectSnapshot(target)
      syncHistoryFlags()
    }
  }

  fun redo() {
    if (redoStack.isEmpty()) return
    viewModelScope.launch {
      pausePlayback()
      val current = repository.captureProjectSnapshot(projectId)
      val target = redoStack.removeLastOrNull() ?: return@launch
      if (current != null) {
        undoStack.addLast(current)
      }
      repository.restoreProjectSnapshot(target)
      syncHistoryFlags()
    }
  }

  // --- Playback & Scrubbing ---
  fun togglePlayPause() {
    val current = uiState.value
    if (current.mainTrackClips.isEmpty()) return

    if (current.isPlaying) {
      localState.update { it.copy(isPlaying = false) }
    } else {
      if (current.playheadMs >= (current.totalDurationMs - 40L).coerceAtLeast(0L)) {
        val nextVersion = ++seekCounter
        localState.update {
          it.copy(
            playheadMs = 0L,
            seekCommand = SeekCommand(0L, nextVersion),
            isPlaying = true,
            isInteractiveCropMode = false
          )
        }
      } else {
        localState.update { it.copy(isPlaying = true, isInteractiveCropMode = false) }
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

  fun selectClip(clipId: Long?, openSubmode: ClipEditToolSubmode? = null) {
    val clip = uiState.value.clips.firstOrNull { it.id == clipId }
    val defaultSubmode = when {
      openSubmode != null -> openSubmode
      clip?.trackType == TrackType.TEXT -> ClipEditToolSubmode.TEXT_STYLE
      clip?.trackType == TrackType.AUDIO -> ClipEditToolSubmode.VOLUME
      else -> ClipEditToolSubmode.NONE
    }
    localState.update {
      it.copy(
        selectedClipId = clipId,
        activeTab = if (clipId != null) EditorBottomTab.EDIT else it.activeTab,
        activeSubmode = if (clipId == null) ClipEditToolSubmode.NONE else defaultSubmode,
        isInteractiveCropMode = false
      )
    }
  }

  fun openTransitionJunction(incomingClipId: Long) {
    val incoming = uiState.value.mainTrackClips.firstOrNull { it.id == incomingClipId } ?: return
    scrubTo(incoming.startMs, pauseIfPlaying = true)
    localState.update {
      it.copy(
        selectedClipId = incomingClipId,
        activeTab = EditorBottomTab.EDIT,
        activeSubmode = ClipEditToolSubmode.TRANSITION,
        isInteractiveCropMode = false
      )
    }
  }

  fun selectBottomTab(tab: EditorBottomTab) {
    localState.update {
      it.copy(
        activeTab = tab,
        activeSubmode = ClipEditToolSubmode.NONE,
        isInteractiveCropMode = false
      )
    }
  }

  fun toggleSubmode(submode: ClipEditToolSubmode) {
    if (uiState.value.selectedClip == null) return
    localState.update {
      val next = if (it.activeSubmode == submode) ClipEditToolSubmode.NONE else submode
      it.copy(
        activeSubmode = next,
        isInteractiveCropMode = if (next != ClipEditToolSubmode.CROP_TRANSFORM) false else it.isInteractiveCropMode
      )
    }
  }

  // --- Live Trim Gestures ---
  fun onLiveTrimUpdate(
    clipId: Long,
    newTrimStartMs: Long,
    newTrimEndMs: Long,
    isLeftHandle: Boolean,
    newOverlayStartMs: Long? = null
  ) {
    ensureGestureStartSnapshot()
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
      val tempState = state.copy(liveTrim = override)
      val previewEdgeMs = if (clip.trackType == TrackType.VIDEO && clip.trackIndex == 0) {
        val updatedMain = applyLiveOverrides(uiState.value.clips, tempState)
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

      tempState.copy(
        isPlaying = false,
        playheadMs = previewEdgeMs,
        seekCommand = SeekCommand(previewEdgeMs, nextVersion)
      )
    }
  }

  fun commitLiveTrim() {
    val activeTrim = localState.value.liveTrim ?: return
    viewModelScope.launch {
      val pre = consumeOrCapturePreGestureSnapshot()
      repository.trimClip(
        projectId = projectId,
        clipId = activeTrim.clipId,
        newTrimStartMs = activeTrim.trimStartMs,
        newTrimEndMs = activeTrim.trimEndMs,
        newStartMsForOverlay = activeTrim.overlayStartMs
      )
      if (pre != null) pushSnapshotToUndo(pre)
      localState.update { it.copy(liveTrim = null) }
    }
  }

  // --- Live Horizontal Drag Positioning for Overlay, Text, and Audio Tracks ---
  fun onLiveNonMainMove(clipId: Long, newStartMs: Long) {
    ensureGestureStartSnapshot()
    localState.update {
      it.copy(
        isPlaying = false,
        liveNonMainMove = clipId to newStartMs.coerceAtLeast(0L)
      )
    }
  }

  fun commitLiveNonMainMove() {
    val move = localState.value.liveNonMainMove ?: return
    viewModelScope.launch {
      val pre = consumeOrCapturePreGestureSnapshot()
      repository.moveNonMainClipPosition(
        projectId = projectId,
        clipId = move.first,
        newStartMs = move.second
      )
      if (pre != null) pushSnapshotToUndo(pre)
      localState.update { it.copy(liveNonMainMove = null) }
    }
  }

  fun reorderMainTrackClip(clipId: Long, targetOrderIndex: Int) {
    viewModelScope.launch {
      pausePlayback()
      executeUndoableEdit {
        repository.reorderMainTrackClip(projectId, clipId, targetOrderIndex)
      }
    }
  }

  // --- Interactive Canvas Layer Drag / Pinch-Scale / Two-Finger Rotate / Opacity (Overlay & Text) ---
  fun onLiveCanvasTransform(
    clipId: Long,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    rotationDeg: Float,
    opacity: Float? = null
  ) {
    ensureGestureStartSnapshot()
    val current = uiState.value
    val clip = current.clips.firstOrNull { it.id == clipId } ?: return
    val localTimeMs = (current.playheadMs - clip.startMs).coerceIn(0L, clip.timelineDurationMs)

    val currentOpacity = opacity ?: when (clip.trackType) {
      TrackType.OVERLAY -> KeyframeInterpolator.evaluateOverlay(
        clip.overlayTransform,
        clip.keyframes,
        localTimeMs
      ).opacity
      TrackType.TEXT -> clip.textOverlay?.let {
        KeyframeInterpolator.evaluateText(it, clip.keyframes, localTimeMs).opacity
      } ?: 1f
      else -> 1f
    }

    localState.update {
      it.copy(
        isPlaying = false,
        selectedClipId = clipId,
        liveLayerTransform = LiveLayerTransformOverride(
          clipId = clipId,
          localTimeMs = localTimeMs,
          offsetX = offsetX.coerceIn(-0.98f, 0.98f),
          offsetY = offsetY.coerceIn(-0.98f, 0.98f),
          scale = scale.coerceIn(0.12f, 3.0f),
          rotationDeg = rotationDeg,
          opacity = currentOpacity.coerceIn(0f, 1f)
        )
      )
    }
  }

  fun onLiveLayerOpacityChange(opacity: Float) {
    val current = uiState.value
    val clip = current.selectedClip ?: return
    val localTimeMs = (current.playheadMs - clip.startMs).coerceIn(0L, clip.timelineDurationMs)
    val eval = when (clip.trackType) {
      TrackType.OVERLAY -> KeyframeInterpolator.evaluateOverlay(clip.overlayTransform, clip.keyframes, localTimeMs)
      TrackType.TEXT -> clip.textOverlay?.let { KeyframeInterpolator.evaluateText(it, clip.keyframes, localTimeMs) }
        ?: return
      else -> return
    }
    onLiveCanvasTransform(
      clipId = clip.id,
      offsetX = eval.offsetX,
      offsetY = eval.offsetY,
      scale = eval.scale,
      rotationDeg = eval.rotationDeg,
      opacity = opacity
    )
  }

  fun commitLiveCanvasTransform() {
    val active = localState.value.liveLayerTransform ?: return
    viewModelScope.launch {
      val pre = consumeOrCapturePreGestureSnapshot()
      repository.updateLayerTransformOrKeyframe(
        projectId = projectId,
        clipId = active.clipId,
        localTimeMs = active.localTimeMs,
        offsetX = active.offsetX,
        offsetY = active.offsetY,
        scale = active.scale,
        rotationDeg = active.rotationDeg,
        opacity = active.opacity
      )
      if (pre != null) pushSnapshotToUndo(pre)
      localState.update { it.copy(liveLayerTransform = null) }
    }
  }

  fun resetSelectedLayerTransform() {
    val clip = uiState.value.selectedClip ?: return
    val localTimeMs = (uiState.value.playheadMs - clip.startMs).coerceIn(0L, clip.timelineDurationMs)
    val defaultScale = if (clip.trackType == TrackType.OVERLAY) 0.45f else 1.0f
    viewModelScope.launch {
      pausePlayback()
      executeUndoableEdit {
        repository.updateLayerTransformOrKeyframe(
          projectId = projectId,
          clipId = clip.id,
          localTimeMs = localTimeMs,
          offsetX = 0f,
          offsetY = 0f,
          scale = defaultScale,
          rotationDeg = 0f,
          opacity = 1.0f
        )
      }
    }
  }

  // --- Keyframe Animation System ---
  fun toggleSelectedClipKeyframes() {
    val current = uiState.value
    val selected = current.selectedClip ?: return
    if (selected.trackType != TrackType.OVERLAY && selected.trackType != TrackType.TEXT) return
    val nextEnabled = !selected.hasKeyframesEnabled
    val localMs = (current.playheadMs - selected.startMs).coerceIn(0L, selected.timelineDurationMs)

    viewModelScope.launch {
      pausePlayback()
      executeUndoableEdit {
        repository.toggleKeyframesForClip(
          projectId = projectId,
          clipId = selected.id,
          enabled = nextEnabled,
          initialLocalTimeMs = localMs
        )
      }
      if (nextEnabled) {
        showMessage("Keyframes ON — move playhead and drag/scale/rotate layer to animate.")
      }
    }
  }

  fun addOrRemoveKeyframeAtPlayhead() {
    val current = uiState.value
    val selected = current.selectedClip ?: return
    if (selected.trackType != TrackType.OVERLAY && selected.trackType != TrackType.TEXT) return
    val localMs = (current.playheadMs - selected.startMs).coerceIn(0L, selected.timelineDurationMs)
    val existingNearby = selected.keyframes.firstOrNull { abs(it.timeMs - localMs) <= 65L }

    viewModelScope.launch {
      pausePlayback()
      executeUndoableEdit {
        if (existingNearby != null) {
          repository.deleteKeyframe(projectId, existingNearby.id)
        } else {
          repository.addOrUpdateExplicitKeyframe(projectId, selected.id, localMs)
        }
      }
    }
  }

  // --- Text Overlay System ---
  fun openAddTextDialog() {
    pausePlayback()
    localState.update {
      it.copy(
        showTextEditorDialog = true,
        editingTextClipId = null
      )
    }
  }

  fun openEditSelectedTextDialog() {
    val selected = uiState.value.selectedClip ?: return
    if (selected.trackType != TrackType.TEXT) return
    pausePlayback()
    localState.update {
      it.copy(
        showTextEditorDialog = true,
        editingTextClipId = selected.id
      )
    }
  }

  fun dismissTextDialog() {
    localState.update {
      it.copy(
        showTextEditorDialog = false,
        editingTextClipId = null
      )
    }
  }

  fun confirmTextDialog(
    content: String,
    fontFamily: String,
    fontSize: Float,
    color: Int,
    backgroundColor: Int?,
    alignment: TextAlignOption,
    isBold: Boolean,
    isItalic: Boolean
  ) {
    val clean = content.trim()
    if (clean.isEmpty()) {
      dismissTextDialog()
      return
    }
    val editingClipId = localState.value.editingTextClipId
    val startAt = uiState.value.playheadMs

    viewModelScope.launch {
      pausePlayback()
      if (editingClipId == null) {
        var newId: Long? = null
        executeUndoableEdit {
          newId = repository.addTextOverlayClip(
            projectId = projectId,
            startMs = startAt,
            durationMs = 3_500L,
            content = clean,
            fontFamily = fontFamily,
            fontSize = fontSize,
            color = color,
            backgroundColor = backgroundColor,
            alignment = alignment,
            isBold = isBold,
            isItalic = isItalic
          )
        }
        localState.update {
          it.copy(
            showTextEditorDialog = false,
            editingTextClipId = null,
            selectedClipId = newId ?: it.selectedClipId,
            activeTab = EditorBottomTab.EDIT,
            activeSubmode = ClipEditToolSubmode.TEXT_STYLE
          )
        }
      } else {
        executeUndoableEdit {
          repository.updateTextOverlayStyle(
            projectId = projectId,
            clipId = editingClipId,
            content = clean,
            fontFamily = fontFamily,
            fontSize = fontSize,
            color = color,
            backgroundColor = backgroundColor,
            alignment = alignment,
            isBold = isBold,
            isItalic = isItalic
          )
        }
        localState.update {
          it.copy(
            showTextEditorDialog = false,
            editingTextClipId = null
          )
        }
      }
    }
  }

  fun updateSelectedTextStyle(
    fontFamily: String? = null,
    fontSize: Float? = null,
    color: Int? = null,
    backgroundColor: Int? = null,
    updateBackground: Boolean = false,
    alignment: TextAlignOption? = null,
    isBold: Boolean? = null,
    isItalic: Boolean? = null
  ) {
    val selected = uiState.value.selectedClip ?: return
    val tx = selected.textOverlay ?: return
    viewModelScope.launch {
      executeUndoableEdit {
        repository.updateTextOverlayStyle(
          projectId = projectId,
          clipId = selected.id,
          content = tx.content,
          fontFamily = fontFamily ?: tx.fontFamily,
          fontSize = fontSize ?: tx.fontSize,
          color = color ?: tx.color,
          backgroundColor = if (updateBackground) backgroundColor else tx.backgroundColor,
          alignment = alignment ?: TextAlignOption.fromString(tx.alignment),
          isBold = isBold ?: tx.isBold,
          isItalic = isItalic ?: tx.isItalic
        )
      }
    }
  }

  // --- Audio Track & Volume / Mute ---
  fun addPickedAudio(uri: Uri) {
    viewModelScope.launch {
      pausePlayback()
      localState.update { it.copy(isImportingMedia = true) }
      try {
        var insertedId: Long? = null
        executeUndoableEdit {
          insertedId = repository.addAudioClipToTrack(
            projectId = projectId,
            uri = uri,
            startMs = uiState.value.playheadMs
          )
        }
        localState.update {
          it.copy(
            isImportingMedia = false,
            selectedClipId = insertedId ?: it.selectedClipId,
            activeTab = EditorBottomTab.EDIT,
            activeSubmode = ClipEditToolSubmode.VOLUME
          )
        }
      } catch (e: Exception) {
        localState.update { it.copy(isImportingMedia = false) }
        showMessage(e.localizedMessage ?: "Failed to import audio file.")
      }
    }
  }

  fun toggleOriginalVideoAudioMute() {
    val nextMuted = !uiState.value.isOriginalAudioMuted
    viewModelScope.launch {
      executeUndoableEdit {
        repository.setOriginalAudioMuted(projectId, nextMuted)
      }
    }
  }

  fun onLiveVolumeChange(volume: Float) {
    ensureGestureStartSnapshot()
    val selected = uiState.value.selectedClip ?: return
    localState.update {
      it.copy(liveVolume = selected.id to volume.coerceIn(0f, 2.0f))
    }
  }

  fun commitLiveVolume() {
    val active = localState.value.liveVolume ?: return
    viewModelScope.launch {
      val pre = consumeOrCapturePreGestureSnapshot()
      repository.updateClipVolume(projectId, active.first, active.second)
      if (pre != null) pushSnapshotToUndo(pre)
      localState.update { it.copy(liveVolume = null) }
    }
  }

  // --- Basic Transitions ---
  fun setSelectedClipTransition(type: TransitionType, durationMs: Long? = null) {
    val selected = uiState.value.selectedClip ?: return
    if (selected.trackType != TrackType.VIDEO || selected.trackIndex != 0) return
    if (selected.orderIndex == 0) {
      showMessage("Select the second or later clip on the Video track to apply a transition.")
      return
    }
    val resolvedDur = durationMs ?: selected.transitionDurationMs
    viewModelScope.launch {
      pausePlayback()
      executeUndoableEdit {
        repository.updateClipTransition(projectId, selected.id, type, resolvedDur)
      }
    }
  }

  // --- Crop / Rotate / Flip ---
  fun rotateSelectedClip90() {
    val selected = uiState.value.selectedClip ?: return
    viewModelScope.launch {
      pausePlayback()
      executeUndoableEdit {
        repository.rotateClip90(projectId, selected.id)
      }
    }
  }

  fun flipSelectedClipHorizontal() {
    val selected = uiState.value.selectedClip ?: return
    viewModelScope.launch {
      pausePlayback()
      executeUndoableEdit {
        repository.toggleClipFlipHorizontal(projectId, selected.id)
      }
    }
  }

  fun flipSelectedClipVertical() {
    val selected = uiState.value.selectedClip ?: return
    viewModelScope.launch {
      pausePlayback()
      executeUndoableEdit {
        repository.toggleClipFlipVertical(projectId, selected.id)
      }
    }
  }

  fun toggleInteractiveCropMode() {
    val selected = uiState.value.selectedClip ?: return
    if (selected.trackType != TrackType.VIDEO && selected.trackType != TrackType.OVERLAY) return
    pausePlayback()
    localState.update {
      it.copy(
        isInteractiveCropMode = !it.isInteractiveCropMode,
        activeSubmode = ClipEditToolSubmode.CROP_TRANSFORM
      )
    }
  }

  fun onLiveCropChange(left: Float, top: Float, right: Float, bottom: Float) {
    ensureGestureStartSnapshot()
    val selected = uiState.value.selectedClip ?: return
    val l = left.coerceIn(0f, 0.85f)
    val t = top.coerceIn(0f, 0.85f)
    val r = right.coerceIn(l + 0.15f, 1f)
    val b = bottom.coerceIn(t + 0.15f, 1f)
    localState.update {
      it.copy(
        isPlaying = false,
        liveCrop = LiveCropOverride(selected.id, l, t, r, b)
      )
    }
  }

  fun commitInteractiveCrop(closeCropOverlay: Boolean = false) {
    val crop = localState.value.liveCrop
    if (crop == null) {
      if (closeCropOverlay) {
        localState.update { it.copy(isInteractiveCropMode = false) }
      }
      return
    }
    viewModelScope.launch {
      val pre = consumeOrCapturePreGestureSnapshot()
      repository.updateClipCrop(
        projectId = projectId,
        clipId = crop.clipId,
        cropLeft = crop.cropLeft,
        cropTop = crop.cropTop,
        cropRight = crop.cropRight,
        cropBottom = crop.cropBottom
      )
      if (pre != null) pushSnapshotToUndo(pre)
      localState.update {
        it.copy(
          liveCrop = null,
          isInteractiveCropMode = if (closeCropOverlay) false else it.isInteractiveCropMode
        )
      }
    }
  }

  fun resetSelectedClipCropAndTransform() {
    val selected = uiState.value.selectedClip ?: return
    viewModelScope.launch {
      pausePlayback()
      executeUndoableEdit {
        repository.updateClipCrop(projectId, selected.id, 0f, 0f, 1f, 1f)
        // Reset flip & rotation if non-default
        if (selected.flipHorizontal) repository.toggleClipFlipHorizontal(projectId, selected.id)
        if (selected.flipVertical) repository.toggleClipFlipVertical(projectId, selected.id)
        val stepsToZero = ((360 - (selected.rotationDegrees % 360)) % 360) / 90
        repeat(stepsToZero) { repository.rotateClip90(projectId, selected.id) }
      }
      localState.update { it.copy(liveCrop = null, isInteractiveCropMode = false) }
    }
  }

  // --- Color Adjustments (Brightness, Contrast, Saturation) ---
  fun onLiveColorAdjustChange(brightness: Float, contrast: Float, saturation: Float) {
    ensureGestureStartSnapshot()
    val selected = uiState.value.selectedClip ?: return
    localState.update {
      it.copy(
        liveColor = LiveColorOverride(
          clipId = selected.id,
          brightness = brightness.coerceIn(-1f, 1f),
          contrast = contrast.coerceIn(-0.9f, 0.9f),
          saturation = saturation.coerceIn(-1f, 1f)
        )
      )
    }
  }

  fun commitLiveColorAdjust() {
    val active = localState.value.liveColor ?: return
    viewModelScope.launch {
      val pre = consumeOrCapturePreGestureSnapshot()
      repository.updateClipColorAdjustments(
        projectId = projectId,
        clipId = active.clipId,
        brightness = active.brightness,
        contrast = active.contrast,
        saturation = active.saturation
      )
      if (pre != null) pushSnapshotToUndo(pre)
      localState.update { it.copy(liveColor = null) }
    }
  }

  fun resetSelectedClipColorAdjustments() {
    val selected = uiState.value.selectedClip ?: return
    viewModelScope.launch {
      executeUndoableEdit {
        repository.updateClipColorAdjustments(projectId, selected.id, 0f, 0f, 0f)
      }
      localState.update { it.copy(liveColor = null) }
    }
  }

  // --- Contextual Clip Actions (Split, Speed, Duplicate, Delete) ---
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
      val pre = repository.captureProjectSnapshot(projectId)
      val splitIds = repository.splitClipAtPosition(
        projectId = projectId,
        clipId = selected.id,
        playheadMs = playhead
      )
      if (splitIds != null) {
        if (pre != null) pushSnapshotToUndo(pre)
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
      var newClipId: Long? = null
      executeUndoableEdit {
        newClipId = repository.duplicateClip(projectId, selected.id)
      }
      if (newClipId != null) {
        localState.update { it.copy(selectedClipId = newClipId) }
      }
    }
  }

  fun deleteSelectedClip() {
    val selected = uiState.value.selectedClip ?: return
    viewModelScope.launch {
      pausePlayback()
      executeUndoableEdit {
        repository.deleteClip(projectId, selected.id)
      }
      localState.update {
        it.copy(
          selectedClipId = null,
          activeSubmode = ClipEditToolSubmode.NONE,
          isInteractiveCropMode = false
        )
      }
    }
  }

  fun setSelectedClipSpeed(speed: Float) {
    val selected = uiState.value.selectedClip ?: return
    viewModelScope.launch {
      pausePlayback()
      executeUndoableEdit {
        repository.updateClipSpeed(projectId, selected.id, speed)
      }
    }
  }

  // --- Media Addition (Main Track & Overlay Track) ---
  fun addPickedMedia(uris: List<Uri>, asOverlay: Boolean, overlayTrackIndex: Int = 1) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      pausePlayback()
      localState.update { it.copy(isImportingMedia = true) }
      try {
        var insertedClipId: Long? = null
        val resolvedOverlayIdx = if (asOverlay) {
          if (overlayTrackIndex > 1) overlayTrackIndex else findAvailableOverlayTrackAt(uiState.value.playheadMs)
        } else {
          1
        }
        executeUndoableEdit {
          insertedClipId = repository.addMediaToTrack(
            projectId = projectId,
            uris = uris,
            asOverlay = asOverlay,
            overlayStartMs = uiState.value.playheadMs,
            overlayTrackIndex = resolvedOverlayIdx
          )
        }
        localState.update {
          it.copy(
            isImportingMedia = false,
            selectedClipId = insertedClipId ?: it.selectedClipId,
            activeTab = EditorBottomTab.EDIT,
            activeSubmode = ClipEditToolSubmode.NONE
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
      var insertedId: Long? = null
      executeUndoableEdit {
        insertedId = repository.addExistingAssetToTrack(
          projectId = projectId,
          assetId = assetId,
          asOverlay = asOverlay,
          overlayStartMs = uiState.value.playheadMs,
          overlayTrackIndex = nextOverlayTrack
        )
      }
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

  // --- Project Title & Aspect Ratio Updates ---
  fun openRenameDialog() {
    localState.update { it.copy(showRenameDialog = true) }
  }

  fun dismissRenameDialog() {
    localState.update { it.copy(showRenameDialog = false) }
  }

  fun updateProjectName(newName: String) {
    viewModelScope.launch {
      executeUndoableEdit {
        repository.renameProject(projectId, newName)
      }
      localState.update { it.copy(showRenameDialog = false) }
    }
  }

  fun cycleAspectRatio() {
    val current = uiState.value.aspectRatio
    val entries = AspectRatio.entries
    val next = entries[(entries.indexOf(current) + 1) % entries.size]
    viewModelScope.launch {
      executeUndoableEdit {
        repository.updateProjectAspectRatio(projectId, next)
      }
    }
  }

  // --- Export Flow (Media3 Transformer) ---
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
      isOriginalAudioMuted = snapshot.isOriginalAudioMuted,
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
    val activeSubmode: ClipEditToolSubmode = ClipEditToolSubmode.NONE,
    val isInteractiveCropMode: Boolean = false,
    val showTextEditorDialog: Boolean = false,
    val editingTextClipId: Long? = null,
    val showRenameDialog: Boolean = false,
    val isImportingMedia: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val liveTrim: LiveTrimOverride? = null,
    val liveNonMainMove: Pair<Long, Long>? = null,
    val liveLayerTransform: LiveLayerTransformOverride? = null,
    val liveVolume: Pair<Long, Float>? = null,
    val liveColor: LiveColorOverride? = null,
    val liveCrop: LiveCropOverride? = null,
    val exportState: ExportState = ExportState.Idle,
    val snackbarMessage: String? = null
  )
}
