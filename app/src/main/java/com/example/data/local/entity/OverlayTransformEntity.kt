package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "overlay_transforms",
  foreignKeys = [
    ForeignKey(
      entity = TimelineClipEntity::class,
      parentColumns = ["id"],
      childColumns = ["timelineClipId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [Index(value = ["timelineClipId"], unique = true)]
)
data class OverlayTransformEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val timelineClipId: Long,
  val offsetX: Float = 0.35f,     // Normalized stage coordinates -1f..1f (center = 0f)
  val offsetY: Float = 0.35f,     // Normalized stage coordinates -1f..1f (top = -1f, bottom = +1f)
  val scale: Float = 0.42f,       // 0.15f..2.5f
  val rotationDeg: Float = 0f,    // Clockwise degrees
  val opacity: Float = 1.0f,      // 0f..1f
  val hasKeyframesEnabled: Boolean = false
)
