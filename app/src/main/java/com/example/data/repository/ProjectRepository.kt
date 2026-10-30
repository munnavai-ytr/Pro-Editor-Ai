package com.example.data.repository

import android.net.Uri
import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.TextAlignOption
import com.example.data.local.entity.TransitionType
import com.example.domain.model.ProjectStateSnapshot
import com.example.domain.model.TimelineClipUiModel
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
  fun observeAllProjects(): Flow<List<ProjectEntity>>
  fun observeProject(projectId: Long): Flow<ProjectEntity?>
  fun observeAssets(projectId: Long): Flow<List<MediaAssetEntity>>
  fun observeTimelineClips(projectId: Long): Flow<List<TimelineClipUiModel>>

  suspend fun createProjectWithMedia(
    name: String,
    aspectRatio: AspectRatio,
    mediaUris: List<Uri>
  ): Long

  suspend fun renameProject(projectId: Long, newName: String)
  suspend fun updateProjectAspectRatio(projectId: Long, aspectRatio: AspectRatio)
  suspend fun setOriginalAudioMuted(projectId: Long, muted: Boolean)
  suspend fun duplicateProject(projectId: Long): Long?
  suspend fun deleteProject(projectId: Long)

  // --- Media & Audio Addition ---
  suspend fun addMediaToTrack(
    projectId: Long,
    uris: List<Uri>,
    asOverlay: Boolean,
    overlayStartMs: Long = 0L,
    overlayTrackIndex: Int = 1
  ): Long?

  suspend fun addExistingAssetToTrack(
    projectId: Long,
    assetId: Long,
    asOverlay: Boolean,
    overlayStartMs: Long = 0L,
    overlayTrackIndex: Int = 1
  ): Long?

  suspend fun addAudioClipToTrack(
    projectId: Long,
    uri: Uri,
    startMs: Long = 0L
  ): Long?

  // --- Text Overlay System ---
  suspend fun addTextOverlayClip(
    projectId: Long,
    startMs: Long,
    durationMs: Long = 3_500L,
    content: String,
    fontFamily: String = "Inter",
    fontSize: Float = 28f,
    color: Int = 0xFFFFFFFF.toInt(),
    backgroundColor: Int? = 0xCC0A0A0A.toInt(),
    alignment: TextAlignOption = TextAlignOption.CENTER,
    isBold: Boolean = true,
    isItalic: Boolean = false
  ): Long?

  suspend fun updateTextOverlayStyle(
    projectId: Long,
    clipId: Long,
    content: String,
    fontFamily: String,
    fontSize: Float,
    color: Int,
    backgroundColor: Int?,
    alignment: TextAlignOption,
    isBold: Boolean,
    isItalic: Boolean
  )

  // --- Canvas Transform & Keyframe Animation (Overlay & Text) ---
  suspend fun updateLayerTransformOrKeyframe(
    projectId: Long,
    clipId: Long,
    localTimeMs: Long,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    rotationDeg: Float,
    opacity: Float
  )

  suspend fun toggleKeyframesForClip(
    projectId: Long,
    clipId: Long,
    enabled: Boolean,
    initialLocalTimeMs: Long
  )

  suspend fun addOrUpdateExplicitKeyframe(
    projectId: Long,
    clipId: Long,
    localTimeMs: Long
  )

  suspend fun deleteKeyframe(
    projectId: Long,
    keyframeId: Long
  )

  // --- Timeline Clip Editing ---
  suspend fun trimClip(
    projectId: Long,
    clipId: Long,
    newTrimStartMs: Long,
    newTrimEndMs: Long,
    newStartMsForOverlay: Long? = null
  )

  suspend fun splitClipAtPosition(
    projectId: Long,
    clipId: Long,
    playheadMs: Long
  ): Pair<Long, Long>?

  suspend fun updateClipSpeed(
    projectId: Long,
    clipId: Long,
    speed: Float
  )

  suspend fun updateClipVolume(
    projectId: Long,
    clipId: Long,
    volume: Float
  )

  suspend fun duplicateClip(
    projectId: Long,
    clipId: Long
  ): Long?

  suspend fun deleteClip(
    projectId: Long,
    clipId: Long
  )

  suspend fun reorderMainTrackClip(
    projectId: Long,
    clipId: Long,
    newOrderIndex: Int
  )

  suspend fun moveNonMainClipPosition(
    projectId: Long,
    clipId: Long,
    newStartMs: Long,
    newTrackIndex: Int? = null
  )

  // --- Basic Transitions ---
  suspend fun updateClipTransition(
    projectId: Long,
    clipId: Long,
    transitionType: TransitionType,
    durationMs: Long = 300L
  )

  // --- Crop / Rotate / Flip ---
  suspend fun rotateClip90(projectId: Long, clipId: Long)
  suspend fun toggleClipFlipHorizontal(projectId: Long, clipId: Long)
  suspend fun toggleClipFlipVertical(projectId: Long, clipId: Long)
  suspend fun updateClipCrop(
    projectId: Long,
    clipId: Long,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float
  )

  // --- Color Adjustments ---
  suspend fun updateClipColorAdjustments(
    projectId: Long,
    clipId: Long,
    brightness: Float,
    contrast: Float,
    saturation: Float
  )

  // --- Command Snapshot Undo / Redo ---
  suspend fun captureProjectSnapshot(projectId: Long): ProjectStateSnapshot?
  suspend fun restoreProjectSnapshot(snapshot: ProjectStateSnapshot)
}
