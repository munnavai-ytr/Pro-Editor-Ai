package com.example.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.PlayNexaDatabase
import com.example.data.local.dao.MediaAssetDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.TimelineClipDao
import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.local.entity.MediaType
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.TimelineClipEntity
import com.example.data.local.entity.TrackType
import com.example.data.media.MediaMetadataHelper
import com.example.domain.model.TimelineClipUiModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class ProjectRepositoryImpl @Inject constructor(
  private val database: PlayNexaDatabase,
  private val projectDao: ProjectDao,
  private val mediaAssetDao: MediaAssetDao,
  private val timelineClipDao: TimelineClipDao,
  private val metadataHelper: MediaMetadataHelper
) : ProjectRepository {

  override fun observeAllProjects(): Flow<List<ProjectEntity>> =
    projectDao.observeAllProjects()

  override fun observeProject(projectId: Long): Flow<ProjectEntity?> =
    projectDao.observeProjectById(projectId)

  override fun observeAssets(projectId: Long): Flow<List<MediaAssetEntity>> =
    mediaAssetDao.observeAssetsForProject(projectId)

  override fun observeTimelineClips(projectId: Long): Flow<List<TimelineClipUiModel>> {
    return combine(
      timelineClipDao.observeClipsForProject(projectId),
      mediaAssetDao.observeAssetsForProject(projectId)
    ) { clips, assets ->
      val assetMap = assets.associateBy { it.id }
      clips.mapNotNull { clip ->
        val asset = assetMap[clip.mediaAssetId] ?: return@mapNotNull null
        val maxSourceDuration = if (asset.type == MediaType.IMAGE) {
          maxOf(asset.durationMs, MediaMetadataHelper.MAX_IMAGE_SOURCE_DURATION_MS)
        } else {
          asset.durationMs
        }
        TimelineClipUiModel(
          id = clip.id,
          projectId = clip.projectId,
          mediaAssetId = clip.mediaAssetId,
          sourceUri = asset.sourceUri,
          mediaType = asset.type,
          assetThumbnailPath = asset.thumbnailPath,
          sourceDurationMs = maxSourceDuration,
          trackIndex = clip.trackIndex,
          trackType = clip.trackType,
          startMs = clip.startMs,
          trimStartMs = clip.trimStartMs,
          trimEndMs = clip.trimEndMs,
          orderIndex = clip.orderIndex,
          speed = clip.speed
        )
      }
    }
  }

  override suspend fun createProjectWithMedia(
    name: String,
    aspectRatio: AspectRatio,
    mediaUris: List<Uri>
  ): Long {
    val inspectedList = mediaUris.map { metadataHelper.inspectAndPrepareMedia(it) }
    val now = System.currentTimeMillis()
    val projectTitle = name.trim().ifBlank { "Untitled Project" }

    return database.withTransaction {
      val projectId = projectDao.insertProject(
        ProjectEntity(
          name = projectTitle,
          thumbnailPath = inspectedList.firstOrNull { it.thumbnailPath != null }?.thumbnailPath,
          createdAt = now,
          updatedAt = now,
          aspectRatio = aspectRatio.rawValue,
          durationMs = 0L
        )
      )

      var runningStartMs = 0L
      inspectedList.forEachIndexed { index, inspected ->
        val assetId = mediaAssetDao.insertAsset(
          MediaAssetEntity(
            projectId = projectId,
            type = inspected.mediaType,
            sourceUri = inspected.usableUri,
            durationMs = inspected.durationMs,
            thumbnailPath = inspected.thumbnailPath
          )
        )

        val clipDuration = inspected.durationMs.coerceAtLeast(200L)
        timelineClipDao.insertClip(
          TimelineClipEntity(
            projectId = projectId,
            mediaAssetId = assetId,
            trackIndex = 0,
            trackType = TrackType.VIDEO,
            startMs = runningStartMs,
            trimStartMs = 0L,
            trimEndMs = clipDuration,
            orderIndex = index,
            speed = 1.0f
          )
        )
        runningStartMs += clipDuration
      }

      syncMainTrackAndProjectMetadata(projectId)
      projectId
    }
  }

  override suspend fun renameProject(projectId: Long, newName: String) {
    val trimmed = newName.trim()
    if (trimmed.isEmpty()) return
    database.withTransaction {
      val existing = projectDao.getProjectById(projectId) ?: return@withTransaction
      projectDao.updateProject(
        existing.copy(
          name = trimmed,
          updatedAt = System.currentTimeMillis()
        )
      )
    }
  }

  override suspend fun updateProjectAspectRatio(projectId: Long, aspectRatio: AspectRatio) {
    database.withTransaction {
      val existing = projectDao.getProjectById(projectId) ?: return@withTransaction
      projectDao.updateProject(
        existing.copy(
          aspectRatio = aspectRatio.rawValue,
          updatedAt = System.currentTimeMillis()
        )
      )
    }
  }

  override suspend fun duplicateProject(projectId: Long): Long? {
    return database.withTransaction {
      val sourceProject = projectDao.getProjectById(projectId) ?: return@withTransaction null
      val sourceAssets = mediaAssetDao.getAssetsForProject(projectId)
      val sourceClips = timelineClipDao.getClipsForProject(projectId)
      val now = System.currentTimeMillis()

      val newProjectId = projectDao.insertProject(
        sourceProject.copy(
          id = 0L,
          name = "${sourceProject.name} (Copy)",
          createdAt = now,
          updatedAt = now
        )
      )

      val oldToNewAssetId = mutableMapOf<Long, Long>()
      for (asset in sourceAssets) {
        val newAssetId = mediaAssetDao.insertAsset(
          asset.copy(id = 0L, projectId = newProjectId)
        )
        oldToNewAssetId[asset.id] = newAssetId
      }

      val duplicatedClips = sourceClips.mapNotNull { clip ->
        val mappedAssetId = oldToNewAssetId[clip.mediaAssetId] ?: return@mapNotNull null
        clip.copy(
          id = 0L,
          projectId = newProjectId,
          mediaAssetId = mappedAssetId
        )
      }
      if (duplicatedClips.isNotEmpty()) {
        timelineClipDao.insertClips(duplicatedClips)
      }
      syncMainTrackAndProjectMetadata(newProjectId)
      newProjectId
    }
  }

  override suspend fun deleteProject(projectId: Long) {
    projectDao.deleteProjectById(projectId)
  }

  override suspend fun addMediaToTrack(
    projectId: Long,
    uris: List<Uri>,
    asOverlay: Boolean,
    overlayStartMs: Long,
    overlayTrackIndex: Int
  ): Long? {
    if (uris.isEmpty()) return null
    val inspectedList = uris.map { metadataHelper.inspectAndPrepareMedia(it) }

    return database.withTransaction {
      val existingClips = timelineClipDao.getClipsForProject(projectId)
      var lastInsertedClipId: Long? = null

      if (!asOverlay) {
        var nextOrder = (existingClips.filter { it.trackIndex == 0 }.maxOfOrNull { it.orderIndex } ?: -1) + 1
        var currentEnd = existingClips
          .filter { it.trackIndex == 0 }
          .sumOf { effectiveTimelineDuration(it) }

        for (inspected in inspectedList) {
          val assetId = mediaAssetDao.insertAsset(
            MediaAssetEntity(
              projectId = projectId,
              type = inspected.mediaType,
              sourceUri = inspected.usableUri,
              durationMs = inspected.durationMs,
              thumbnailPath = inspected.thumbnailPath
            )
          )
          val dur = inspected.durationMs.coerceAtLeast(200L)
          lastInsertedClipId = timelineClipDao.insertClip(
            TimelineClipEntity(
              projectId = projectId,
              mediaAssetId = assetId,
              trackIndex = 0,
              trackType = TrackType.VIDEO,
              startMs = currentEnd,
              trimStartMs = 0L,
              trimEndMs = dur,
              orderIndex = nextOrder++,
              speed = 1.0f
            )
          )
          currentEnd += dur
        }
      } else {
        val targetTrack = overlayTrackIndex.coerceAtLeast(1)
        var nextOrder = (existingClips.filter { it.trackIndex == targetTrack }.maxOfOrNull { it.orderIndex } ?: -1) + 1
        var cursorMs = overlayStartMs.coerceAtLeast(0L)

        for (inspected in inspectedList) {
          val assetId = mediaAssetDao.insertAsset(
            MediaAssetEntity(
              projectId = projectId,
              type = inspected.mediaType,
              sourceUri = inspected.usableUri,
              durationMs = inspected.durationMs,
              thumbnailPath = inspected.thumbnailPath
            )
          )
          val dur = inspected.durationMs.coerceAtLeast(200L)
          lastInsertedClipId = timelineClipDao.insertClip(
            TimelineClipEntity(
              projectId = projectId,
              mediaAssetId = assetId,
              trackIndex = targetTrack,
              trackType = TrackType.OVERLAY,
              startMs = cursorMs,
              trimStartMs = 0L,
              trimEndMs = dur,
              orderIndex = nextOrder++,
              speed = 1.0f
            )
          )
          cursorMs += dur
        }
      }

      syncMainTrackAndProjectMetadata(projectId)
      lastInsertedClipId
    }
  }

  override suspend fun addExistingAssetToTrack(
    projectId: Long,
    assetId: Long,
    asOverlay: Boolean,
    overlayStartMs: Long,
    overlayTrackIndex: Int
  ): Long? {
    return database.withTransaction {
      val asset = mediaAssetDao.getAssetById(assetId) ?: return@withTransaction null
      val existingClips = timelineClipDao.getClipsForProject(projectId)
      val dur = asset.durationMs.coerceAtLeast(200L)

      val insertedId = if (!asOverlay) {
        val nextOrder = (existingClips.filter { it.trackIndex == 0 }.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val startAt = existingClips.filter { it.trackIndex == 0 }.sumOf { effectiveTimelineDuration(it) }
        timelineClipDao.insertClip(
          TimelineClipEntity(
            projectId = projectId,
            mediaAssetId = asset.id,
            trackIndex = 0,
            trackType = TrackType.VIDEO,
            startMs = startAt,
            trimStartMs = 0L,
            trimEndMs = dur,
            orderIndex = nextOrder,
            speed = 1.0f
          )
        )
      } else {
        val targetTrack = overlayTrackIndex.coerceAtLeast(1)
        val nextOrder = (existingClips.filter { it.trackIndex == targetTrack }.maxOfOrNull { it.orderIndex } ?: -1) + 1
        timelineClipDao.insertClip(
          TimelineClipEntity(
            projectId = projectId,
            mediaAssetId = asset.id,
            trackIndex = targetTrack,
            trackType = TrackType.OVERLAY,
            startMs = overlayStartMs.coerceAtLeast(0L),
            trimStartMs = 0L,
            trimEndMs = dur,
            orderIndex = nextOrder,
            speed = 1.0f
          )
        )
      }

      syncMainTrackAndProjectMetadata(projectId)
      insertedId
    }
  }

  override suspend fun trimClip(
    projectId: Long,
    clipId: Long,
    newTrimStartMs: Long,
    newTrimEndMs: Long,
    newStartMsForOverlay: Long?
  ) {
    database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction
      val asset = mediaAssetDao.getAssetById(clip.mediaAssetId) ?: return@withTransaction

      val maxDuration = if (asset.type == MediaType.IMAGE) {
        maxOf(asset.durationMs, MediaMetadataHelper.MAX_IMAGE_SOURCE_DURATION_MS)
      } else {
        asset.durationMs
      }

      val safeTrimStart = newTrimStartMs.coerceIn(0L, (maxDuration - 200L).coerceAtLeast(0L))
      val safeTrimEnd = newTrimEndMs.coerceIn(safeTrimStart + 200L, maxDuration.coerceAtLeast(200L))

      val updatedStartMs = if (clip.trackIndex > 0 && newStartMsForOverlay != null) {
        newStartMsForOverlay.coerceAtLeast(0L)
      } else {
        clip.startMs
      }

      timelineClipDao.updateClip(
        clip.copy(
          trimStartMs = safeTrimStart,
          trimEndMs = safeTrimEnd,
          startMs = updatedStartMs
        )
      )
      syncMainTrackAndProjectMetadata(projectId)
    }
  }

  override suspend fun splitClipAtPosition(
    projectId: Long,
    clipId: Long,
    playheadMs: Long
  ): Pair<Long, Long>? {
    return database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction null
      val clipTimelineDuration = effectiveTimelineDuration(clip)
      val clipEndMs = clip.startMs + clipTimelineDuration

      // Require at least 150ms on each side of the split point
      if (playheadMs < clip.startMs + 150L || playheadMs > clipEndMs - 150L) {
        return@withTransaction null
      }

      val offsetInTimelineMs = playheadMs - clip.startMs
      val splitSourceMidMs = (clip.trimStartMs + (offsetInTimelineMs * clip.speed).toLong())
        .coerceIn(clip.trimStartMs + 100L, clip.trimEndMs - 100L)

      if (splitSourceMidMs <= clip.trimStartMs || splitSourceMidMs >= clip.trimEndMs) {
        return@withTransaction null
      }

      // Shift subsequent clips on the same track by +1 in orderIndex
      val allClips = timelineClipDao.getClipsForProject(projectId)
      val shifted = allClips
        .filter { it.trackIndex == clip.trackIndex && it.orderIndex > clip.orderIndex }
        .map { it.copy(orderIndex = it.orderIndex + 1) }
      if (shifted.isNotEmpty()) {
        timelineClipDao.updateClips(shifted)
      }

      // Update left segment
      val leftClip = clip.copy(trimEndMs = splitSourceMidMs)
      timelineClipDao.updateClip(leftClip)

      // Insert right segment
      val rightClipId = timelineClipDao.insertClip(
        TimelineClipEntity(
          projectId = projectId,
          mediaAssetId = clip.mediaAssetId,
          trackIndex = clip.trackIndex,
          trackType = clip.trackType,
          startMs = playheadMs,
          trimStartMs = splitSourceMidMs,
          trimEndMs = clip.trimEndMs,
          orderIndex = clip.orderIndex + 1,
          speed = clip.speed
        )
      )

      syncMainTrackAndProjectMetadata(projectId)
      clip.id to rightClipId
    }
  }

  override suspend fun updateClipSpeed(
    projectId: Long,
    clipId: Long,
    speed: Float
  ) {
    val safeSpeed = speed.coerceIn(0.25f, 4.0f)
    database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction
      timelineClipDao.updateClip(clip.copy(speed = safeSpeed))
      syncMainTrackAndProjectMetadata(projectId)
    }
  }

  override suspend fun duplicateClip(
    projectId: Long,
    clipId: Long
  ): Long? {
    return database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction null
      val allClips = timelineClipDao.getClipsForProject(projectId)

      val shifted = allClips
        .filter { it.trackIndex == clip.trackIndex && it.orderIndex > clip.orderIndex }
        .map { it.copy(orderIndex = it.orderIndex + 1) }
      if (shifted.isNotEmpty()) {
        timelineClipDao.updateClips(shifted)
      }

      val clipDur = effectiveTimelineDuration(clip)
      val newClipId = timelineClipDao.insertClip(
        clip.copy(
          id = 0L,
          startMs = clip.startMs + clipDur,
          orderIndex = clip.orderIndex + 1
        )
      )

      syncMainTrackAndProjectMetadata(projectId)
      newClipId
    }
  }

  override suspend fun deleteClip(
    projectId: Long,
    clipId: Long
  ) {
    database.withTransaction {
      timelineClipDao.deleteClipById(clipId)
      syncMainTrackAndProjectMetadata(projectId)
    }
  }

  override suspend fun reorderMainTrackClip(
    projectId: Long,
    clipId: Long,
    newOrderIndex: Int
  ) {
    database.withTransaction {
      val mainClips = timelineClipDao.getClipsForProject(projectId)
        .filter { it.trackIndex == 0 }
        .sortedWith(compareBy({ it.orderIndex }, { it.id }))
        .toMutableList()

      val fromIndex = mainClips.indexOfFirst { it.id == clipId }
      if (fromIndex == -1) return@withTransaction
      val targetIndex = newOrderIndex.coerceIn(0, mainClips.lastIndex)
      if (fromIndex == targetIndex) return@withTransaction

      val moved = mainClips.removeAt(fromIndex)
      mainClips.add(targetIndex, moved)

      var cursorMs = 0L
      val reindexed = mainClips.mapIndexed { idx, c ->
        val updated = c.copy(orderIndex = idx, startMs = cursorMs)
        cursorMs += effectiveTimelineDuration(updated)
        updated
      }
      timelineClipDao.updateClips(reindexed)
      syncMainTrackAndProjectMetadata(projectId)
    }
  }

  override suspend fun moveOverlayClipPosition(
    projectId: Long,
    clipId: Long,
    newStartMs: Long,
    newTrackIndex: Int?
  ) {
    database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction
      if (clip.trackIndex == 0) return@withTransaction
      val targetTrack = (newTrackIndex ?: clip.trackIndex).coerceAtLeast(1)
      timelineClipDao.updateClip(
        clip.copy(
          startMs = newStartMs.coerceAtLeast(0L),
          trackIndex = targetTrack,
          trackType = TrackType.OVERLAY
        )
      )
      syncMainTrackAndProjectMetadata(projectId)
    }
  }

  private fun effectiveTimelineDuration(clip: TimelineClipEntity): Long {
    val trimmed = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(100L)
    return (trimmed / clip.speed.coerceIn(0.25f, 4.0f)).toLong().coerceAtLeast(100L)
  }

  /**
   * Keeps main track (trackIndex == 0) contiguous with clean 0..N-1 orderIndex,
   * re-indexes overlay tracks cleanly, and updates ProjectEntity duration, thumbnail, and updatedAt.
   */
  private suspend fun syncMainTrackAndProjectMetadata(projectId: Long) {
    val allClips = timelineClipDao.getClipsForProject(projectId)
    val mainClips = allClips
      .filter { it.trackIndex == 0 }
      .sortedWith(compareBy({ it.orderIndex }, { it.id }))

    var runningMainMs = 0L
    val updatedMain = mainClips.mapIndexed { idx, clip ->
      val updated = clip.copy(orderIndex = idx, startMs = runningMainMs)
      runningMainMs += effectiveTimelineDuration(updated)
      updated
    }

    val overlayTracks = allClips
      .filter { it.trackIndex > 0 }
      .groupBy { it.trackIndex }

    val updatedOverlays = overlayTracks.flatMap { (_, clipsInTrack) ->
      clipsInTrack
        .sortedWith(compareBy({ it.startMs }, { it.orderIndex }, { it.id }))
        .mapIndexed { idx, clip -> clip.copy(orderIndex = idx) }
    }

    val toSave = updatedMain + updatedOverlays
    if (toSave.isNotEmpty()) {
      timelineClipDao.updateClips(toSave)
    }

    val maxOverlayEndMs = updatedOverlays.maxOfOrNull { it.startMs + effectiveTimelineDuration(it) } ?: 0L
    val totalDurationMs = maxOf(runningMainMs, maxOverlayEndMs)

    val project = projectDao.getProjectById(projectId) ?: return
    val assets = mediaAssetDao.getAssetsForProject(projectId).associateBy { it.id }
    val leadThumb = updatedMain.firstNotNullOfOrNull { assets[it.mediaAssetId]?.thumbnailPath }
      ?: assets.values.firstNotNullOfOrNull { it.thumbnailPath }
      ?: project.thumbnailPath

    projectDao.updateProject(
      project.copy(
        durationMs = totalDurationMs,
        thumbnailPath = leadThumb,
        updatedAt = System.currentTimeMillis()
      )
    )
  }
}
