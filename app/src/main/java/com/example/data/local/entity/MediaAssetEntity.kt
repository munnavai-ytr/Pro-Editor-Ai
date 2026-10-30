package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MediaType {
  VIDEO,
  IMAGE,
  AUDIO
}

@Entity(
  tableName = "media_assets",
  foreignKeys = [
    ForeignKey(
      entity = ProjectEntity::class,
      parentColumns = ["id"],
      childColumns = ["projectId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [Index(value = ["projectId"])]
)
data class MediaAssetEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val projectId: Long,
  val type: MediaType,
  val sourceUri: String,
  val durationMs: Long,
  val thumbnailPath: String? = null
)
