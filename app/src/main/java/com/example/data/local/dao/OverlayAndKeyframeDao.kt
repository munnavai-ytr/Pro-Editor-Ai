package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.KeyframeEntity
import com.example.data.local.entity.OverlayTransformEntity
import com.example.data.local.entity.TextOverlayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OverlayAndKeyframeDao {

  // --- Overlay Transforms ---
  @Query(
    """
    SELECT ot.* FROM overlay_transforms ot
    INNER JOIN timeline_clips tc ON tc.id = ot.timelineClipId
    WHERE tc.projectId = :projectId
    """
  )
  fun observeOverlayTransformsForProject(projectId: Long): Flow<List<OverlayTransformEntity>>

  @Query(
    """
    SELECT ot.* FROM overlay_transforms ot
    INNER JOIN timeline_clips tc ON tc.id = ot.timelineClipId
    WHERE tc.projectId = :projectId
    """
  )
  suspend fun getOverlayTransformsForProject(projectId: Long): List<OverlayTransformEntity>

  @Query("SELECT * FROM overlay_transforms WHERE timelineClipId = :clipId LIMIT 1")
  suspend fun getOverlayTransformByClipId(clipId: Long): OverlayTransformEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOverlayTransform(transform: OverlayTransformEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOverlayTransforms(transforms: List<OverlayTransformEntity>)

  @Update
  suspend fun updateOverlayTransform(transform: OverlayTransformEntity)

  // --- Text Overlays ---
  @Query(
    """
    SELECT tx.* FROM text_overlays tx
    INNER JOIN timeline_clips tc ON tc.id = tx.timelineClipId
    WHERE tc.projectId = :projectId
    """
  )
  fun observeTextOverlaysForProject(projectId: Long): Flow<List<TextOverlayEntity>>

  @Query(
    """
    SELECT tx.* FROM text_overlays tx
    INNER JOIN timeline_clips tc ON tc.id = tx.timelineClipId
    WHERE tc.projectId = :projectId
    """
  )
  suspend fun getTextOverlaysForProject(projectId: Long): List<TextOverlayEntity>

  @Query("SELECT * FROM text_overlays WHERE timelineClipId = :clipId LIMIT 1")
  suspend fun getTextOverlayByClipId(clipId: Long): TextOverlayEntity?

  @Query("SELECT * FROM text_overlays WHERE id = :textOverlayId LIMIT 1")
  suspend fun getTextOverlayById(textOverlayId: Long): TextOverlayEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTextOverlay(textOverlay: TextOverlayEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTextOverlays(textOverlays: List<TextOverlayEntity>)

  @Update
  suspend fun updateTextOverlay(textOverlay: TextOverlayEntity)

  // --- Keyframes ---
  @Query(
    """
    SELECT kf.* FROM keyframes kf
    LEFT JOIN overlay_transforms ot ON ot.id = kf.overlayTransformId
    LEFT JOIN text_overlays tx ON tx.id = kf.textOverlayId
    LEFT JOIN timeline_clips tc ON tc.id = COALESCE(ot.timelineClipId, tx.timelineClipId)
    WHERE tc.projectId = :projectId
    ORDER BY kf.timeMs ASC, kf.id ASC
    """
  )
  fun observeKeyframesForProject(projectId: Long): Flow<List<KeyframeEntity>>

  @Query(
    """
    SELECT kf.* FROM keyframes kf
    LEFT JOIN overlay_transforms ot ON ot.id = kf.overlayTransformId
    LEFT JOIN text_overlays tx ON tx.id = kf.textOverlayId
    LEFT JOIN timeline_clips tc ON tc.id = COALESCE(ot.timelineClipId, tx.timelineClipId)
    WHERE tc.projectId = :projectId
    ORDER BY kf.timeMs ASC, kf.id ASC
    """
  )
  suspend fun getKeyframesForProject(projectId: Long): List<KeyframeEntity>

  @Query("SELECT * FROM keyframes WHERE overlayTransformId = :overlayTransformId ORDER BY timeMs ASC")
  suspend fun getKeyframesForOverlay(overlayTransformId: Long): List<KeyframeEntity>

  @Query("SELECT * FROM keyframes WHERE textOverlayId = :textOverlayId ORDER BY timeMs ASC")
  suspend fun getKeyframesForText(textOverlayId: Long): List<KeyframeEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertKeyframe(keyframe: KeyframeEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertKeyframes(keyframes: List<KeyframeEntity>)

  @Update
  suspend fun updateKeyframe(keyframe: KeyframeEntity)

  @Query("DELETE FROM keyframes WHERE id = :keyframeId")
  suspend fun deleteKeyframeById(keyframeId: Long)
}
