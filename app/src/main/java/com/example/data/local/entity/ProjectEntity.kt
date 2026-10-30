package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AspectRatio(
  val rawValue: String,
  val label: String,
  val subtitle: String,
  val widthWeight: Float,
  val heightWeight: Float
) {
  RATIO_9_16("9:16", "9:16", "Reels / Shorts", 9f, 16f),
  RATIO_1_1("1:1", "1:1", "Square Feed", 1f, 1f),
  RATIO_16_9("16:9", "16:9", "Landscape HD", 16f, 9f);

  val ratioFloat: Float
    get() = widthWeight / heightWeight

  companion object {
    fun fromString(value: String): AspectRatio {
      return entries.firstOrNull { it.rawValue == value } ?: RATIO_9_16
    }
  }
}

@Entity(tableName = "projects")
data class ProjectEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val name: String,
  val thumbnailPath: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val aspectRatio: String = AspectRatio.RATIO_9_16.rawValue,
  val durationMs: Long = 0L,
  val isOriginalAudioMuted: Boolean = false
)
