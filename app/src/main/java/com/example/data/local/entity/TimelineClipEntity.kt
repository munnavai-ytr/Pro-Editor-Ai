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
  val mediaAssetId: Long,
  val trackIndex: Int,
  val trackType: TrackType,
  val startMs: Long,
  val trimStartMs: Long,
  val trimEndMs: Long,
  val orderIndex: Int,
  val speed: Float = 1.0f
)
