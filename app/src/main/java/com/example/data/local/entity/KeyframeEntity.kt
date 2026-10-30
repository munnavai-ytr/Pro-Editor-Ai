package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "keyframes",
  foreignKeys = [
    ForeignKey(
      entity = TextOverlayEntity::class,
      parentColumns = ["id"],
      childColumns = ["textOverlayId"],
      onDelete = ForeignKey.CASCADE
    ),
    ForeignKey(
      entity = OverlayTransformEntity::class,
      parentColumns = ["id"],
      childColumns = ["overlayTransformId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [
    Index(value = ["textOverlayId"]),
    Index(value = ["overlayTransformId"])
  ]
)
data class KeyframeEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val textOverlayId: Long? = null,
  val overlayTransformId: Long? = null,
  val timeMs: Long, // Offset relative to clip startMs (0..timelineDurationMs)
  val positionX: Float,
  val positionY: Float,
  val scale: Float,
  val rotationDeg: Float,
  val opacity: Float = 1.0f
)
