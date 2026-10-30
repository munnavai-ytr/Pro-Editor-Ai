package com.example.data.repository

import android.net.Uri
import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.local.entity.ProjectEntity
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
  suspend fun duplicateProject(projectId: Long): Long?
  suspend fun deleteProject(projectId: Long)

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

  suspend fun moveOverlayClipPosition(
    projectId: Long,
    clipId: Long,
    newStartMs: Long,
    newTrackIndex: Int? = null
  )
}
