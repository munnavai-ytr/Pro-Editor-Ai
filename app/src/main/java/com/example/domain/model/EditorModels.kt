package com.example.domain.model

import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.MediaType
import com.example.data.local.entity.TrackType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class TimelineClipUiModel(
  val id: Long,
  val projectId: Long,
  val mediaAssetId: Long,
  val sourceUri: String,
  val mediaType: MediaType,
  val assetThumbnailPath: String?,
  val sourceDurationMs: Long,
  val trackIndex: Int,
  val trackType: TrackType,
  val startMs: Long,
  val trimStartMs: Long,
  val trimEndMs: Long,
  val orderIndex: Int,
  val speed: Float = 1.0f
) {
  val trimmedSourceDurationMs: Long
    get() = (trimEndMs - trimStartMs).coerceAtLeast(100L)

  val timelineDurationMs: Long
    get() = (trimmedSourceDurationMs / speed.coerceIn(0.25f, 4.0f)).toLong().coerceAtLeast(100L)

  val endMs: Long
    get() = startMs + timelineDurationMs
}

enum class ExportResolution(
  val shortSidePx: Int,
  val label: String,
  val badge: String
) {
  RES_720P(720, "720p HD", "HD"),
  RES_1080P(1080, "1080p Full HD", "FHD");

  fun dimensionsFor(aspectRatio: AspectRatio): Pair<Int, Int> {
    return when (aspectRatio) {
      AspectRatio.RATIO_9_16 -> {
        val w = shortSidePx
        val h = (shortSidePx * 16f / 9f).roundToInt()
        // Ensure even dimensions for H.264 hardware encoders
        (w and -2) to (h and -2)
      }
      AspectRatio.RATIO_1_1 -> {
        (shortSidePx and -2) to (shortSidePx and -2)
      }
      AspectRatio.RATIO_16_9 -> {
        val h = shortSidePx
        val w = (shortSidePx * 16f / 9f).roundToInt()
        (w and -2) to (h and -2)
      }
    }
  }
}

sealed interface ExportState {
  data object Idle : ExportState
  data class Configuring(val selectedResolution: ExportResolution = ExportResolution.RES_1080P) : ExportState
  data class Exporting(
    val progressPercent: Int,
    val resolution: ExportResolution
  ) : ExportState
  data class Success(
    val outputUri: String,
    val displayFileName: String,
    val resolution: ExportResolution
  ) : ExportState
  data class Error(val message: String) : ExportState
}

enum class EditorBottomTab {
  MEDIA,
  EDIT,
  AUDIO,
  TEXT
}

object TimeFormatUtils {
  fun formatDurationBadge(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
  }

  fun formatTimelinePrecision(positionMs: Long): String {
    val clamped = positionMs.coerceAtLeast(0L)
    val minutes = (clamped / 1000L) / 60L
    val seconds = (clamped / 1000L) % 60L
    val tenths = (clamped % 1000L) / 100L
    return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
  }

  fun formatRulerMarker(timeMs: Long, showHalfSeconds: Boolean): String {
    val clamped = timeMs.coerceAtLeast(0L)
    val minutes = (clamped / 1000L) / 60L
    val seconds = (clamped / 1000L) % 60L
    val tenths = (clamped % 1000L) / 100L
    return if (showHalfSeconds && tenths > 0L) {
      String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
    } else {
      String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
  }

  fun formatRelativeEdited(updatedAtMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val diffMs = (nowMs - updatedAtMs).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
      minutes < 1L -> "Edited just now"
      minutes < 60L -> "Edited ${minutes}m ago"
      hours < 24L -> "Edited ${hours}h ago"
      days < 7L -> "Edited ${days}d ago"
      else -> {
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
        "Edited ${formatter.format(Date(updatedAtMs))}"
      }
    }
  }
}
