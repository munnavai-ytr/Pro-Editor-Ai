package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TextAlignOption(val raw: String, val label: String) {
  LEFT("LEFT", "Left"),
  CENTER("CENTER", "Center"),
  RIGHT("RIGHT", "Right");

  companion object {
    fun fromString(value: String): TextAlignOption =
      entries.firstOrNull { it.raw == value } ?: CENTER
  }
}

@Entity(
  tableName = "text_overlays",
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
data class TextOverlayEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val timelineClipId: Long,
  val content: String,
  val fontFamily: String = "Inter",
  val fontSize: Float = 28f,
  val color: Int = 0xFFFFFFFF.toInt(),
  val backgroundColor: Int? = 0xCC0A0A0A.toInt(),
  val alignment: String = TextAlignOption.CENTER.raw,
  val isBold: Boolean = true,
  val isItalic: Boolean = false,
  val offsetX: Float = 0f,
  val offsetY: Float = 0f,
  val scale: Float = 1.0f,
  val rotationDeg: Float = 0f,
  val opacity: Float = 1.0f,
  val hasKeyframesEnabled: Boolean = false
)
