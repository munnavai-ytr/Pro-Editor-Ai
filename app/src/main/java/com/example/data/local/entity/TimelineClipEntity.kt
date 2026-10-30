package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TrackType {
  VIDEO,
  OVERLAY,
  AUDIO,
  TEXT
}

enum class TransitionType(val label: String) {
  NONE("None"),
  FADE("Fade"),
  SLIDE_LEFT("Slide Left"),
  SLIDE_RIGHT("Slide Right"),
  ZOOM_IN("Zoom In")
}

@Entity(
  tableName = "timeline_clips",
  foreignKeys = [
    ForeignKey(
      entity = ProjectEntity::class,
      parentColumns = ["id"],
      childColumns = ["projectId"],
      onDelete = ForeignKey.CASCADE
    ),
    ForeignKey(
      entity = MediaAssetEntity::class,
      parentColumns = ["id"],
      childColumns = ["mediaAssetId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [
    Index(value = ["projectId"]),
    Index(value = ["mediaAssetId"])
  ]
)
data class TimelineClipEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val projectId: Long,
  val mediaAssetId: Long? = null,
  val trackIndex: Int,
  val trackType: TrackType,
  val startMs: Long,
  val trimStartMs: Long,
  val trimEndMs: Long,
  val orderIndex: Int,
  val speed: Float = 1.0f,
  val volume: Float = 1.0f, // 0.0f..2.0f (0%..200%)
  val rotationDegrees: Int = 0, // 0, 90, 180, 270
  val flipHorizontal: Boolean = false,
  val flipVertical: Boolean = false,
  val cropLeft: Float = 0f,
  val cropTop: Float = 0f,
  val cropRight: Float = 1f,
  val cropBottom: Float = 1f,
  val brightness: Float = 0f, // -1f..1f
  val contrast: Float = 0f,   // -0.9f..0.9f
  val saturation: Float = 0f, // -1f..1f
  val transitionType: TransitionType = TransitionType.NONE,
  val transitionDurationMs: Long = 300L
)
