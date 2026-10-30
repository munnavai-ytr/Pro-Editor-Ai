package com.example.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.PlayNexaDatabase
import com.example.data.local.dao.MediaAssetDao
import com.example.data.local.dao.OverlayAndKeyframeDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.TimelineClipDao
import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.KeyframeEntity
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.local.entity.MediaType
import com.example.data.local.entity.OverlayTransformEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.TextAlignOption
import com.example.data.local.entity.TextOverlayEntity
import com.example.data.local.entity.TimelineClipEntity
import com.example.data.local.entity.TrackType
import com.example.data.local.entity.TransitionType
import com.example.data.media.MediaMetadataHelper
import com.example.domain.model.KeyframeInterpolator
import com.example.domain.model.ProjectStateSnapshot
import com.example.domain.model.TimelineClipUiModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class ProjectRepositoryImpl @Inject constructor(
  private val database: PlayNexaDatabase,
  private val projectDao: ProjectDao,
  private val mediaAssetDao: MediaAssetDao,
  private val timelineClipDao: TimelineClipDao,
  private val overlayAndKeyframeDao: OverlayAndKeyframeDao,
  private val metadataHelper: MediaMetadataHelper
) : ProjectRepository {

  companion object {
    const val TEXT_TRACK_INDEX = 100
    const val AUDIO_TRACK_INDEX = -1
    const val MAX_TEXT_DURATION_MS = 60_000L
    private const val KEYFRAME_MERGE_WINDOW_MS = 65L
  }

  override fun observeAllProjects(): Flow<List<ProjectEntity>> =
    projectDao.observeAllProjects()

  override fun observeProject(projectId: Long): Flow<ProjectEntity?> =
    projectDao.observeProjectById(projectId)

  override fun observeAssets(projectId: Long): Flow<List<MediaAssetEntity>> =
    mediaAssetDao.observeAssetsForProject(projectId)

  override fun observeTimelineClips(projectId: Long): Flow<List<TimelineClipUiModel>> {
    return combine(
      timelineClipDao.observeClipsForProject(projectId),
      mediaAssetDao.observeAssetsForProject(projectId),
      overlayAndKeyframeDao.observeOverlayTransformsForProject(projectId),
      overlayAndKeyframeDao.observeTextOverlaysForProject(projectId),
      overlayAndKeyframeDao.observeKeyframesForProject(projectId)
    ) { clips, assets, overlayTransforms, textOverlays, keyframes ->
      val assetMap = assets.associateBy { it.id }
      val overlayTransformByClip = overlayTransforms.associateBy { it.timelineClipId }
      val textOverlayByClip = textOverlays.associateBy { it.timelineClipId }
      val keyframesByOverlayId = keyframes
        .filter { it.overlayTransformId != null }
        .groupBy { it.overlayTransformId!! }
      val keyframesByTextId = keyframes
        .filter { it.textOverlayId != null }
        .groupBy { it.textOverlayId!! }

      clips.mapNotNull { clip ->
        when (clip.trackType) {
          TrackType.TEXT -> {
            val textEntity = textOverlayByClip[clip.id] ?: return@mapNotNull null
            val textKfs = keyframesByTextId[textEntity.id].orEmpty()
            TimelineClipUiModel(
              id = clip.id,
              projectId = clip.projectId,
              mediaAssetId = null,
              sourceUri = "",
              mediaType = MediaType.IMAGE,
              assetThumbnailPath = null,
              sourceDurationMs = MAX_TEXT_DURATION_MS,
              trackIndex = clip.trackIndex,
              trackType = TrackType.TEXT,
              startMs = clip.startMs,
              trimStartMs = clip.trimStartMs,
              trimEndMs = clip.trimEndMs,
              orderIndex = clip.orderIndex,
              speed = 1.0f,
              volume = 0f,
              textOverlay = textEntity,
              keyframes = textKfs
            )
          }

          else -> {
            val asset = assetMap[clip.mediaAssetId] ?: return@mapNotNull null
            val maxSourceDuration = if (asset.type == MediaType.IMAGE) {
              maxOf(asset.durationMs, MediaMetadataHelper.MAX_IMAGE_SOURCE_DURATION_MS)
            } else {
              asset.durationMs
            }
            val ovTransform = if (clip.trackType == TrackType.OVERLAY) {
              overlayTransformByClip[clip.id]
            } else {
              null
            }
            val ovKeyframes = ovTransform?.let { keyframesByOverlayId[it.id].orEmpty() }.orEmpty()

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
              speed = clip.speed,
              volume = clip.volume,
              rotationDegrees = clip.rotationDegrees,
              flipHorizontal = clip.flipHorizontal,
              flipVertical = clip.flipVertical,
              cropLeft = clip.cropLeft,
              cropTop = clip.cropTop,
              cropRight = clip.cropRight,
              cropBottom = clip.cropBottom,
              brightness = clip.brightness,
              contrast = clip.contrast,
              saturation = clip.saturation,
              transitionType = clip.transitionType,
              transitionDurationMs = clip.transitionDurationMs,
              overlayTransform = ovTransform,
              keyframes = ovKeyframes
            )
          }
        }
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
          durationMs = 0L,
          isOriginalAudioMuted = false
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
            speed = 1.0f,
            volume = 1.0f
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

  override suspend fun setOriginalAudioMuted(projectId: Long, muted: Boolean) {
    database.withTransaction {
      val existing = projectDao.getProjectById(projectId) ?: return@withTransaction
      projectDao.updateProject(
        existing.copy(
          isOriginalAudioMuted = muted,
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
      val sourceOvTransforms = overlayAndKeyframeDao.getOverlayTransformsForProject(projectId)
      val sourceTextOverlays = overlayAndKeyframeDao.getTextOverlaysForProject(projectId)
      val sourceKeyframes = overlayAndKeyframeDao.getKeyframesForProject(projectId)
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

      val oldToNewClipId = mutableMapOf<Long, Long>()
      for (clip in sourceClips) {
        val mappedAssetId = if (clip.mediaAssetId != null) {
          oldToNewAssetId[clip.mediaAssetId] ?: continue
        } else {
          null
        }
        val newClipId = timelineClipDao.insertClip(
          clip.copy(
            id = 0L,
            projectId = newProjectId,
            mediaAssetId = mappedAssetId
          )
        )
        oldToNewClipId[clip.id] = newClipId
      }

      val oldToNewOvId = mutableMapOf<Long, Long>()
      for (ov in sourceOvTransforms) {
        val mappedClipId = oldToNewClipId[ov.timelineClipId] ?: continue
        val newOvId = overlayAndKeyframeDao.insertOverlayTransform(
          ov.copy(id = 0L, timelineClipId = mappedClipId)
        )
        oldToNewOvId[ov.id] = newOvId
      }

      val oldToNewTextId = mutableMapOf<Long, Long>()
      for (tx in sourceTextOverlays) {
        val mappedClipId = oldToNewClipId[tx.timelineClipId] ?: continue
        val newTxId = overlayAndKeyframeDao.insertTextOverlay(
          tx.copy(id = 0L, timelineClipId = mappedClipId)
        )
        oldToNewTextId[tx.id] = newTxId
      }

      for (kf in sourceKeyframes) {
        val mappedOvId = kf.overlayTransformId?.let { oldToNewOvId[it] }
        val mappedTxId = kf.textOverlayId?.let { oldToNewTextId[it] }
        if (mappedOvId != null || mappedTxId != null) {
          overlayAndKeyframeDao.insertKeyframe(
            kf.copy(
              id = 0L,
              overlayTransformId = mappedOvId,
              textOverlayId = mappedTxId
            )
          )
        }
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
        val mainClips = existingClips.filter { it.trackType == TrackType.VIDEO && it.trackIndex == 0 }
        var nextOrder = (mainClips.maxOfOrNull { it.orderIndex } ?: -1) + 1
        var currentEnd = mainClips.sumOf { effectiveTimelineDuration(it) }

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
              speed = 1.0f,
              volume = 1.0f
            )
          )
          currentEnd += dur
        }
      } else {
        val targetTrack = overlayTrackIndex.coerceIn(1, 9)
        var nextOrder = (
          existingClips
            .filter { it.trackType == TrackType.OVERLAY && it.trackIndex == targetTrack }
            .maxOfOrNull { it.orderIndex } ?: -1
          ) + 1
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
          val insertedClipId = timelineClipDao.insertClip(
            TimelineClipEntity(
              projectId = projectId,
              mediaAssetId = assetId,
              trackIndex = targetTrack,
              trackType = TrackType.OVERLAY,
              startMs = cursorMs,
              trimStartMs = 0L,
              trimEndMs = dur,
              orderIndex = nextOrder++,
              speed = 1.0f,
              volume = 0f
            )
          )
          overlayAndKeyframeDao.insertOverlayTransform(
            OverlayTransformEntity(
              timelineClipId = insertedClipId,
              offsetX = 0f,
              offsetY = 0f,
              scale = 0.45f,
              rotationDeg = 0f,
              opacity = 1.0f,
              hasKeyframesEnabled = false
            )
          )
          lastInsertedClipId = insertedClipId
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

      val insertedId = if (asset.type == MediaType.AUDIO) {
        val audioClips = existingClips.filter { it.trackType == TrackType.AUDIO }
        val nextOrder = (audioClips.maxOfOrNull { it.orderIndex } ?: -1) + 1
        timelineClipDao.insertClip(
          TimelineClipEntity(
            projectId = projectId,
            mediaAssetId = asset.id,
            trackIndex = AUDIO_TRACK_INDEX,
            trackType = TrackType.AUDIO,
            startMs = overlayStartMs.coerceAtLeast(0L),
            trimStartMs = 0L,
            trimEndMs = dur,
            orderIndex = nextOrder,
            speed = 1.0f,
            volume = 1.0f
          )
        )
      } else if (!asOverlay) {
        val mainClips = existingClips.filter { it.trackType == TrackType.VIDEO && it.trackIndex == 0 }
        val nextOrder = (mainClips.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val startAt = mainClips.sumOf { effectiveTimelineDuration(it) }
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
            speed = 1.0f,
            volume = 1.0f
          )
        )
      } else {
        val targetTrack = overlayTrackIndex.coerceIn(1, 9)
        val nextOrder = (
          existingClips
            .filter { it.trackType == TrackType.OVERLAY && it.trackIndex == targetTrack }
            .maxOfOrNull { it.orderIndex } ?: -1
          ) + 1
        val clipId = timelineClipDao.insertClip(
          TimelineClipEntity(
            projectId = projectId,
            mediaAssetId = asset.id,
            trackIndex = targetTrack,
            trackType = TrackType.OVERLAY,
            startMs = overlayStartMs.coerceAtLeast(0L),
            trimStartMs = 0L,
            trimEndMs = dur,
            orderIndex = nextOrder,
            speed = 1.0f,
            volume = 0f
          )
        )
        overlayAndKeyframeDao.insertOverlayTransform(
          OverlayTransformEntity(
            timelineClipId = clipId,
            offsetX = 0f,
            offsetY = 0f,
            scale = 0.45f,
            rotationDeg = 0f,
            opacity = 1.0f,
            hasKeyframesEnabled = false
          )
        )
        clipId
      }

      syncMainTrackAndProjectMetadata(projectId)
      insertedId
    }
  }

  override suspend fun addAudioClipToTrack(
    projectId: Long,
    uri: Uri,
    startMs: Long
  ): Long? {
    val inspected = metadataHelper.inspectAndPrepareMedia(uri)
    return database.withTransaction {
      val assetId = mediaAssetDao.insertAsset(
        MediaAssetEntity(
          projectId = projectId,
          type = MediaType.AUDIO,
          sourceUri = inspected.usableUri,
          durationMs = inspected.durationMs.coerceAtLeast(500L),
          thumbnailPath = null
        )
      )
      val existingAudio = timelineClipDao.getClipsForProject(projectId)
        .filter { it.trackType == TrackType.AUDIO }
      val nextOrder = (existingAudio.maxOfOrNull { it.orderIndex } ?: -1) + 1
      val dur = inspected.durationMs.coerceAtLeast(500L)

      val insertedClipId = timelineClipDao.insertClip(
        TimelineClipEntity(
          projectId = projectId,
          mediaAssetId = assetId,
          trackIndex = AUDIO_TRACK_INDEX,
          trackType = TrackType.AUDIO,
          startMs = startMs.coerceAtLeast(0L),
          trimStartMs = 0L,
          trimEndMs = dur,
          orderIndex = nextOrder,
          speed = 1.0f,
          volume = 1.0f
        )
      )
      syncMainTrackAndProjectMetadata(projectId)
      insertedClipId
    }
  }

  override suspend fun addTextOverlayClip(
    projectId: Long,
    startMs: Long,
    durationMs: Long,
    content: String,
    fontFamily: String,
    fontSize: Float,
    color: Int,
    backgroundColor: Int?,
    alignment: TextAlignOption,
    isBold: Boolean,
    isItalic: Boolean
  ): Long? {
    val cleanContent = content.trim()
    if (cleanContent.isEmpty()) return null
    val safeDur = durationMs.coerceIn(300L, MAX_TEXT_DURATION_MS)

    return database.withTransaction {
      val existingTextClips = timelineClipDao.getClipsForProject(projectId)
        .filter { it.trackType == TrackType.TEXT }
      val nextOrder = (existingTextClips.maxOfOrNull { it.orderIndex } ?: -1) + 1

      val clipId = timelineClipDao.insertClip(
        TimelineClipEntity(
          projectId = projectId,
          mediaAssetId = null,
          trackIndex = TEXT_TRACK_INDEX,
          trackType = TrackType.TEXT,
          startMs = startMs.coerceAtLeast(0L),
          trimStartMs = 0L,
          trimEndMs = safeDur,
          orderIndex = nextOrder,
          speed = 1.0f,
          volume = 0f
        )
      )

      overlayAndKeyframeDao.insertTextOverlay(
        TextOverlayEntity(
          timelineClipId = clipId,
          content = cleanContent,
          fontFamily = fontFamily,
          fontSize = fontSize.coerceIn(12f, 80f),
          color = color,
          backgroundColor = backgroundColor,
          alignment = alignment.raw,
          isBold = isBold,
          isItalic = isItalic,
          offsetX = 0f,
          offsetY = 0f,
          scale = 1.0f,
          rotationDeg = 0f,
          opacity = 1.0f,
          hasKeyframesEnabled = false
        )
      )

      syncMainTrackAndProjectMetadata(projectId)
      clipId
    }
  }

  override suspend fun updateTextOverlayStyle(
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
  ) {
    val cleanContent = content.trim().ifEmpty { "Text" }
    database.withTransaction {
      val existing = overlayAndKeyframeDao.getTextOverlayByClipId(clipId) ?: return@withTransaction
      overlayAndKeyframeDao.updateTextOverlay(
        existing.copy(
          content = cleanContent,
          fontFamily = fontFamily,
          fontSize = fontSize.coerceIn(12f, 80f),
          color = color,
          backgroundColor = backgroundColor,
          alignment = alignment.raw,
          isBold = isBold,
          isItalic = isItalic
        )
      )
      touchProjectTimestamp(projectId)
    }
  }

  override suspend fun updateLayerTransformOrKeyframe(
    projectId: Long,
    clipId: Long,
    localTimeMs: Long,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    rotationDeg: Float,
    opacity: Float
  ) {
    val safeOffsetX = offsetX.coerceIn(-0.98f, 0.98f)
    val safeOffsetY = offsetY.coerceIn(-0.98f, 0.98f)
    val safeScale = scale.coerceIn(0.12f, 3.0f)
    val safeOpacity = opacity.coerceIn(0f, 1.0f)

    database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction
      val clampedLocalMs = localTimeMs.coerceIn(0L, effectiveTimelineDuration(clip))

      when (clip.trackType) {
        TrackType.OVERLAY -> {
          val existing = overlayAndKeyframeDao.getOverlayTransformByClipId(clipId)
            ?: OverlayTransformEntity(timelineClipId = clipId).let { fresh ->
              val id = overlayAndKeyframeDao.insertOverlayTransform(fresh)
              fresh.copy(id = id)
            }

          overlayAndKeyframeDao.updateOverlayTransform(
            existing.copy(
              offsetX = safeOffsetX,
              offsetY = safeOffsetY,
              scale = safeScale,
              rotationDeg = rotationDeg,
              opacity = safeOpacity
            )
          )

          val existingKfs = overlayAndKeyframeDao.getKeyframesForOverlay(existing.id)
          if (existing.hasKeyframesEnabled || existingKfs.isNotEmpty()) {
            upsertOverlayKeyframeInternal(
              overlayTransformId = existing.id,
              existingKeyframes = existingKfs,
              timeMs = clampedLocalMs,
              posX = safeOffsetX,
              posY = safeOffsetY,
              scale = safeScale,
              rotationDeg = rotationDeg,
              opacity = safeOpacity
            )
          }
        }

        TrackType.TEXT -> {
          val existing = overlayAndKeyframeDao.getTextOverlayByClipId(clipId) ?: return@withTransaction
          overlayAndKeyframeDao.updateTextOverlay(
            existing.copy(
              offsetX = safeOffsetX,
              offsetY = safeOffsetY,
              scale = safeScale,
              rotationDeg = rotationDeg,
              opacity = safeOpacity
            )
          )

          val existingKfs = overlayAndKeyframeDao.getKeyframesForText(existing.id)
          if (existing.hasKeyframesEnabled || existingKfs.isNotEmpty()) {
            upsertTextKeyframeInternal(
              textOverlayId = existing.id,
              existingKeyframes = existingKfs,
              timeMs = clampedLocalMs,
              posX = safeOffsetX,
              posY = safeOffsetY,
              scale = safeScale,
              rotationDeg = rotationDeg,
              opacity = safeOpacity
            )
          }
        }

        else -> Unit
      }
      touchProjectTimestamp(projectId)
    }
  }

  override suspend fun toggleKeyframesForClip(
    projectId: Long,
    clipId: Long,
    enabled: Boolean,
    initialLocalTimeMs: Long
  ) {
    database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction
      val clampedLocalMs = initialLocalTimeMs.coerceIn(0L, effectiveTimelineDuration(clip))

      when (clip.trackType) {
        TrackType.OVERLAY -> {
          val existing = overlayAndKeyframeDao.getOverlayTransformByClipId(clipId)
            ?: OverlayTransformEntity(timelineClipId = clipId).let { fresh ->
              val id = overlayAndKeyframeDao.insertOverlayTransform(fresh)
              fresh.copy(id = id)
            }
          overlayAndKeyframeDao.updateOverlayTransform(existing.copy(hasKeyframesEnabled = enabled))
          if (enabled) {
            val kfs = overlayAndKeyframeDao.getKeyframesForOverlay(existing.id)
            if (kfs.isEmpty()) {
              overlayAndKeyframeDao.insertKeyframe(
                KeyframeEntity(
                  overlayTransformId = existing.id,
                  timeMs = clampedLocalMs,
                  positionX = existing.offsetX,
                  positionY = existing.offsetY,
                  scale = existing.scale,
                  rotationDeg = existing.rotationDeg,
                  opacity = existing.opacity
                )
              )
            }
          }
        }

        TrackType.TEXT -> {
          val existing = overlayAndKeyframeDao.getTextOverlayByClipId(clipId) ?: return@withTransaction
          overlayAndKeyframeDao.updateTextOverlay(existing.copy(hasKeyframesEnabled = enabled))
          if (enabled) {
            val kfs = overlayAndKeyframeDao.getKeyframesForText(existing.id)
            if (kfs.isEmpty()) {
              overlayAndKeyframeDao.insertKeyframe(
                KeyframeEntity(
                  textOverlayId = existing.id,
                  timeMs = clampedLocalMs,
                  positionX = existing.offsetX,
                  positionY = existing.offsetY,
                  scale = existing.scale,
                  rotationDeg = existing.rotationDeg,
                  opacity = existing.opacity
                )
              )
            }
          }
        }

        else -> Unit
      }
      touchProjectTimestamp(projectId)
    }
  }

  override suspend fun addOrUpdateExplicitKeyframe(
    projectId: Long,
    clipId: Long,
    localTimeMs: Long
  ) {
    database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction
      val clampedLocalMs = localTimeMs.coerceIn(0L, effectiveTimelineDuration(clip))

      when (clip.trackType) {
        TrackType.OVERLAY -> {
          val existing = overlayAndKeyframeDao.getOverlayTransformByClipId(clipId)
            ?: OverlayTransformEntity(timelineClipId = clipId).let { fresh ->
              val id = overlayAndKeyframeDao.insertOverlayTransform(fresh)
              fresh.copy(id = id)
            }
          if (!existing.hasKeyframesEnabled) {
            overlayAndKeyframeDao.updateOverlayTransform(existing.copy(hasKeyframesEnabled = true))
          }
          val kfs = overlayAndKeyframeDao.getKeyframesForOverlay(existing.id)
          val eval = KeyframeInterpolator.evaluateOverlay(existing, kfs, clampedLocalMs)
          upsertOverlayKeyframeInternal(
            overlayTransformId = existing.id,
            existingKeyframes = kfs,
            timeMs = clampedLocalMs,
            posX = eval.offsetX,
            posY = eval.offsetY,
            scale = eval.scale,
            rotationDeg = eval.rotationDeg,
            opacity = eval.opacity
          )
        }

        TrackType.TEXT -> {
          val existing = overlayAndKeyframeDao.getTextOverlayByClipId(clipId) ?: return@withTransaction
          if (!existing.hasKeyframesEnabled) {
            overlayAndKeyframeDao.updateTextOverlay(existing.copy(hasKeyframesEnabled = true))
          }
          val kfs = overlayAndKeyframeDao.getKeyframesForText(existing.id)
          val eval = KeyframeInterpolator.evaluateText(existing, kfs, clampedLocalMs)
          upsertTextKeyframeInternal(
            textOverlayId = existing.id,
            existingKeyframes = kfs,
            timeMs = clampedLocalMs,
            posX = eval.offsetX,
            posY = eval.offsetY,
            scale = eval.scale,
            rotationDeg = eval.rotationDeg,
            opacity = eval.opacity
          )
        }

        else -> Unit
      }
      touchProjectTimestamp(projectId)
    }
  }

  override suspend fun deleteKeyframe(projectId: Long, keyframeId: Long) {
    database.withTransaction {
      overlayAndKeyframeDao.deleteKeyframeById(keyframeId)
      touchProjectTimestamp(projectId)
    }
  }

  private suspend fun upsertOverlayKeyframeInternal(
    overlayTransformId: Long,
    existingKeyframes: List<KeyframeEntity>,
    timeMs: Long,
    posX: Float,
    posY: Float,
    scale: Float,
    rotationDeg: Float,
    opacity: Float
  ) {
    val nearby = existingKeyframes.minByOrNull { abs(it.timeMs - timeMs) }
    if (nearby != null && abs(nearby.timeMs - timeMs) <= KEYFRAME_MERGE_WINDOW_MS) {
      overlayAndKeyframeDao.updateKeyframe(
        nearby.copy(
          timeMs = timeMs,
          positionX = posX,
          positionY = posY,
          scale = scale,
          rotationDeg = rotationDeg,
          opacity = opacity
        )
      )
    } else {
      overlayAndKeyframeDao.insertKeyframe(
        KeyframeEntity(
          overlayTransformId = overlayTransformId,
          timeMs = timeMs,
          positionX = posX,
          positionY = posY,
          scale = scale,
          rotationDeg = rotationDeg,
          opacity = opacity
        )
      )
    }
  }

  private suspend fun upsertTextKeyframeInternal(
    textOverlayId: Long,
    existingKeyframes: List<KeyframeEntity>,
    timeMs: Long,
    posX: Float,
    posY: Float,
    scale: Float,
    rotationDeg: Float,
    opacity: Float
  ) {
    val nearby = existingKeyframes.minByOrNull { abs(it.timeMs - timeMs) }
    if (nearby != null && abs(nearby.timeMs - timeMs) <= KEYFRAME_MERGE_WINDOW_MS) {
      overlayAndKeyframeDao.updateKeyframe(
        nearby.copy(
          timeMs = timeMs,
          positionX = posX,
          positionY = posY,
          scale = scale,
          rotationDeg = rotationDeg,
          opacity = opacity
        )
      )
    } else {
      overlayAndKeyframeDao.insertKeyframe(
        KeyframeEntity(
          textOverlayId = textOverlayId,
          timeMs = timeMs,
          positionX = posX,
          positionY = posY,
          scale = scale,
          rotationDeg = rotationDeg,
          opacity = opacity
        )
      )
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

      val maxDuration = if (clip.trackType == TrackType.TEXT) {
        MAX_TEXT_DURATION_MS
      } else {
        val assetId = clip.mediaAssetId ?: return@withTransaction
        val asset = mediaAssetDao.getAssetById(assetId) ?: return@withTransaction
        if (asset.type == MediaType.IMAGE) {
          maxOf(asset.durationMs, MediaMetadataHelper.MAX_IMAGE_SOURCE_DURATION_MS)
        } else {
          asset.durationMs
        }
      }

      val safeTrimStart = newTrimStartMs.coerceIn(0L, (maxDuration - 200L).coerceAtLeast(0L))
      val safeTrimEnd = newTrimEndMs.coerceIn(safeTrimStart + 200L, maxDuration.coerceAtLeast(200L))

      val isNonMainTrack = !(clip.trackType == TrackType.VIDEO && clip.trackIndex == 0)
      val updatedStartMs = if (isNonMainTrack && newStartMsForOverlay != null) {
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

      if (playheadMs < clip.startMs + 150L || playheadMs > clipEndMs - 150L) {
        return@withTransaction null
      }

      val offsetInTimelineMs = playheadMs - clip.startMs
      val splitSourceMidMs = (clip.trimStartMs + (offsetInTimelineMs * clip.speed).toLong())
        .coerceIn(clip.trimStartMs + 100L, clip.trimEndMs - 100L)

      if (splitSourceMidMs <= clip.trimStartMs || splitSourceMidMs >= clip.trimEndMs) {
        return@withTransaction null
      }

      val allClips = timelineClipDao.getClipsForProject(projectId)
      val shifted = allClips
        .filter { it.trackIndex == clip.trackIndex && it.trackType == clip.trackType && it.orderIndex > clip.orderIndex }
        .map { it.copy(orderIndex = it.orderIndex + 1) }
      if (shifted.isNotEmpty()) {
        timelineClipDao.updateClips(shifted)
      }

      val leftClip = clip.copy(trimEndMs = splitSourceMidMs)
      timelineClipDao.updateClip(leftClip)

      val rightClipId = timelineClipDao.insertClip(
        clip.copy(
          id = 0L,
          startMs = playheadMs,
          trimStartMs = splitSourceMidMs,
          trimEndMs = clip.trimEndMs,
          orderIndex = clip.orderIndex + 1,
          transitionType = TransitionType.NONE
        )
      )

      // Clone OverlayTransform / TextOverlay & split keyframes cleanly
      if (clip.trackType == TrackType.OVERLAY) {
        overlayAndKeyframeDao.getOverlayTransformByClipId(clip.id)?.let { ov ->
          val newOvId = overlayAndKeyframeDao.insertOverlayTransform(
            ov.copy(id = 0L, timelineClipId = rightClipId)
          )
          val kfs = overlayAndKeyframeDao.getKeyframesForOverlay(ov.id)
          for (kf in kfs) {
            if (kf.timeMs > offsetInTimelineMs) {
              overlayAndKeyframeDao.insertKeyframe(
                kf.copy(
                  id = 0L,
                  overlayTransformId = newOvId,
                  timeMs = (kf.timeMs - offsetInTimelineMs).coerceAtLeast(0L)
                )
              )
              overlayAndKeyframeDao.deleteKeyframeById(kf.id)
            }
          }
        }
      } else if (clip.trackType == TrackType.TEXT) {
        overlayAndKeyframeDao.getTextOverlayByClipId(clip.id)?.let { tx ->
          val newTxId = overlayAndKeyframeDao.insertTextOverlay(
            tx.copy(id = 0L, timelineClipId = rightClipId)
          )
          val kfs = overlayAndKeyframeDao.getKeyframesForText(tx.id)
          for (kf in kfs) {
            if (kf.timeMs > offsetInTimelineMs) {
              overlayAndKeyframeDao.insertKeyframe(
                kf.copy(
                  id = 0L,
                  textOverlayId = newTxId,
                  timeMs = (kf.timeMs - offsetInTimelineMs).coerceAtLeast(0L)
                )
              )
              overlayAndKeyframeDao.deleteKeyframeById(kf.id)
            }
          }
        }
      }

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
      if (clip.trackType == TrackType.TEXT) return@withTransaction
      timelineClipDao.updateClip(clip.copy(speed = safeSpeed))
      syncMainTrackAndProjectMetadata(projectId)
    }
  }

  override suspend fun updateClipVolume(
    projectId: Long,
    clipId: Long,
    volume: Float
  ) {
    val safeVolume = volume.coerceIn(0f, 2.0f)
    database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction
      timelineClipDao.updateClip(clip.copy(volume = safeVolume))
      touchProjectTimestamp(projectId)
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
        .filter { it.trackIndex == clip.trackIndex && it.trackType == clip.trackType && it.orderIndex > clip.orderIndex }
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

      if (clip.trackType == TrackType.OVERLAY) {
        overlayAndKeyframeDao.getOverlayTransformByClipId(clip.id)?.let { ov ->
          val newOvId = overlayAndKeyframeDao.insertOverlayTransform(
            ov.copy(id = 0L, timelineClipId = newClipId)
          )
          val kfs = overlayAndKeyframeDao.getKeyframesForOverlay(ov.id)
          if (kfs.isNotEmpty()) {
            overlayAndKeyframeDao.insertKeyframes(
              kfs.map { it.copy(id = 0L, overlayTransformId = newOvId) }
            )
          }
        }
      } else if (clip.trackType == TrackType.TEXT) {
        overlayAndKeyframeDao.getTextOverlayByClipId(clip.id)?.let { tx ->
          val newTxId = overlayAndKeyframeDao.insertTextOverlay(
            tx.copy(id = 0L, timelineClipId = newClipId)
          )
          val kfs = overlayAndKeyframeDao.getKeyframesForText(tx.id)
          if (kfs.isNotEmpty()) {
            overlayAndKeyframeDao.insertKeyframes(
              kfs.map { it.copy(id = 0L, textOverlayId = newTxId) }
            )
          }
        }
      }

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
        .filter { it.trackType == TrackType.VIDEO && it.trackIndex == 0 }
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

  override suspend fun moveNonMainClipPosition(
    projectId: Long,
    clipId: Long,
    newStartMs: Long,
    newTrackIndex: Int?
  ) {
    database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction
      if (clip.trackType == TrackType.VIDEO && clip.trackIndex == 0) return@withTransaction

      val resolvedTrackIndex = when (clip.trackType) {
        TrackType.OVERLAY -> (newTrackIndex ?: clip.trackIndex).coerceIn(1, 9)
        TrackType.TEXT -> TEXT_TRACK_INDEX
        TrackType.AUDIO -> AUDIO_TRACK_INDEX
        TrackType.VIDEO -> clip.trackIndex
      }

      timelineClipDao.updateClip(
        clip.copy(
          startMs = newStartMs.coerceAtLeast(0L),
          trackIndex = resolvedTrackIndex
        )
      )
      syncMainTrackAndProjectMetadata(projectId)
    }
  }

  override suspend fun updateClipTransition(
    projectId: Long,
    clipId: Long,
    transitionType: TransitionType,
    durationMs: Long
  ) {
    val safeDur = durationMs.coerceIn(150L, 1_500L)
    database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction
      timelineClipDao.updateClip(
        clip.copy(
          transitionType = transitionType,
          transitionDurationMs = safeDur
        )
      )
      touchProjectTimestamp(projectId)
    }
  }

  override suspend fun rotateClip90(projectId: Long, clipId: Long) {
    database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction
      val nextRot = (clip.rotationDegrees + 90) % 360
      timelineClipDao.updateClip(clip.copy(rotationDegrees = nextRot))
      touchProjectTimestamp(projectId)
    }
  }

  override suspend fun toggleClipFlipHorizontal(projectId: Long, clipId: Long) {
    database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction
      timelineClipDao.updateClip(clip.copy(flipHorizontal = !clip.flipHorizontal))
      touchProjectTimestamp(projectId)
    }
  }

  override suspend fun toggleClipFlipVertical(projectId: Long, clipId: Long) {
    database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction
      timelineClipDao.updateClip(clip.copy(flipVertical = !clip.flipVertical))
      touchProjectTimestamp(projectId)
    }
  }

  override suspend fun updateClipCrop(
    projectId: Long,
    clipId: Long,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float
  ) {
    val l = cropLeft.coerceIn(0f, 0.85f)
    val t = cropTop.coerceIn(0f, 0.85f)
    val r = cropRight.coerceIn(l + 0.15f, 1f)
    val b = cropBottom.coerceIn(t + 0.15f, 1f)

    database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction
      timelineClipDao.updateClip(
        clip.copy(
          cropLeft = l,
          cropTop = t,
          cropRight = r,
          cropBottom = b
        )
      )
      touchProjectTimestamp(projectId)
    }
  }

  override suspend fun updateClipColorAdjustments(
    projectId: Long,
    clipId: Long,
    brightness: Float,
    contrast: Float,
    saturation: Float
  ) {
    database.withTransaction {
      val clip = timelineClipDao.getClipById(clipId) ?: return@withTransaction
      timelineClipDao.updateClip(
        clip.copy(
          brightness = brightness.coerceIn(-1f, 1f),
          contrast = contrast.coerceIn(-0.9f, 0.9f),
          saturation = saturation.coerceIn(-1f, 1f)
        )
      )
      touchProjectTimestamp(projectId)
    }
  }

  override suspend fun captureProjectSnapshot(projectId: Long): ProjectStateSnapshot? {
    return database.withTransaction {
      val project = projectDao.getProjectById(projectId) ?: return@withTransaction null
      val assets = mediaAssetDao.getAssetsForProject(projectId)
      val clips = timelineClipDao.getClipsForProject(projectId)
      val ovTransforms = overlayAndKeyframeDao.getOverlayTransformsForProject(projectId)
      val textOverlays = overlayAndKeyframeDao.getTextOverlaysForProject(projectId)
      val keyframes = overlayAndKeyframeDao.getKeyframesForProject(projectId)
      ProjectStateSnapshot(
        project = project,
        assets = assets,
        clips = clips,
        overlayTransforms = ovTransforms,
        textOverlays = textOverlays,
        keyframes = keyframes
      )
    }
  }

  override suspend fun restoreProjectSnapshot(snapshot: ProjectStateSnapshot) {
    database.withTransaction {
      val projectId = snapshot.project.id
      // Cascading delete on timeline_clips automatically clears overlay_transforms, text_overlays, and keyframes
      timelineClipDao.deleteClipsForProject(projectId)
      mediaAssetDao.deleteAssetsForProject(projectId)

      projectDao.updateProject(
        snapshot.project.copy(updatedAt = System.currentTimeMillis())
      )
      if (snapshot.assets.isNotEmpty()) {
        mediaAssetDao.insertAssets(snapshot.assets)
      }
      if (snapshot.clips.isNotEmpty()) {
        timelineClipDao.insertClips(snapshot.clips)
      }
      if (snapshot.overlayTransforms.isNotEmpty()) {
        overlayAndKeyframeDao.insertOverlayTransforms(snapshot.overlayTransforms)
      }
      if (snapshot.textOverlays.isNotEmpty()) {
        overlayAndKeyframeDao.insertTextOverlays(snapshot.textOverlays)
      }
      if (snapshot.keyframes.isNotEmpty()) {
        overlayAndKeyframeDao.insertKeyframes(snapshot.keyframes)
      }
    }
  }

  private fun effectiveTimelineDuration(clip: TimelineClipEntity): Long {
    val trimmed = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(100L)
    return (trimmed / clip.speed.coerceIn(0.25f, 4.0f)).toLong().coerceAtLeast(100L)
  }

  private suspend fun touchProjectTimestamp(projectId: Long) {
    val existing = projectDao.getProjectById(projectId) ?: return
    projectDao.updateProject(existing.copy(updatedAt = System.currentTimeMillis()))
  }

  /**
   * Keeps main track (VIDEO, trackIndex == 0) contiguous with clean 0..N-1 orderIndex,
   * re-indexes non-main tracks (OVERLAY, TEXT, AUDIO) cleanly without altering their startMs,
   * and updates ProjectEntity duration, thumbnail, and updatedAt.
   */
  private suspend fun syncMainTrackAndProjectMetadata(projectId: Long) {
    val allClips = timelineClipDao.getClipsForProject(projectId)
    val mainClips = allClips
      .filter { it.trackType == TrackType.VIDEO && it.trackIndex == 0 }
      .sortedWith(compareBy({ it.orderIndex }, { it.id }))

    var runningMainMs = 0L
    val updatedMain = mainClips.mapIndexed { idx, clip ->
      // First clip on main track cannot have an incoming transition
      val cleanedTransition = if (idx == 0) TransitionType.NONE else clip.transitionType
      val updated = clip.copy(
        orderIndex = idx,
        startMs = runningMainMs,
        transitionType = cleanedTransition
      )
      runningMainMs += effectiveTimelineDuration(updated)
      updated
    }

    val nonMainGroups = allClips
      .filter { !(it.trackType == TrackType.VIDEO && it.trackIndex == 0) }
      .groupBy { it.trackType to it.trackIndex }

    val updatedNonMain = nonMainGroups.flatMap { (_, clipsInLane) ->
      clipsInLane
        .sortedWith(compareBy({ it.startMs }, { it.orderIndex }, { it.id }))
        .mapIndexed { idx, clip -> clip.copy(orderIndex = idx) }
    }

    val toSave = updatedMain + updatedNonMain
    if (toSave.isNotEmpty()) {
      timelineClipDao.updateClips(toSave)
    }

    val maxNonMainEndMs = updatedNonMain.maxOfOrNull { it.startMs + effectiveTimelineDuration(it) } ?: 0L
    val totalDurationMs = maxOf(runningMainMs, maxNonMainEndMs)

    val project = projectDao.getProjectById(projectId) ?: return
    val assets = mediaAssetDao.getAssetsForProject(projectId).associateBy { it.id }
    val leadThumb = updatedMain.firstNotNullOfOrNull { it.mediaAssetId?.let { id -> assets[id]?.thumbnailPath } }
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
