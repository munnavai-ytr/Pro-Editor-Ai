package com.example.domain.model

import androidx.annotation.FontRes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.KeyframeEntity
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.local.entity.MediaType
import com.example.data.local.entity.OverlayTransformEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.TextOverlayEntity
import com.example.data.local.entity.TimelineClipEntity
import com.example.data.local.entity.TrackType
import com.example.data.local.entity.TransitionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class NexaFontOption(
  val familyName: String,
  val displayLabel: String,
  @FontRes val fontResId: Int
) {
  INTER("Inter", "Inter", R.font.inter),
  BEBAS_NEUE("Bebas Neue", "Bebas", R.font.bebas_neue),
  PLAYFAIR("Playfair Display", "Playfair", R.font.playfair_display),
  PACIFICO("Pacifico", "Pacifico", R.font.pacifico),
  SPACE_MONO("Space Mono", "Mono", R.font.space_mono),
  MONTSERRAT("Montserrat", "Montserrat", R.font.montserrat);

  val composeFontFamily: FontFamily by lazy {
    FontFamily(
      Font(fontResId, FontWeight.Normal),
      Font(fontResId, FontWeight.Bold)
    )
  }

  companion object {
    fun fromName(name: String): NexaFontOption =
      entries.firstOrNull { it.familyName.equals(name, ignoreCase = true) } ?: INTER
  }
}

data class EvaluatedLayerTransform(
  val offsetX: Float,      // -1f..1f (left to right)
  val offsetY: Float,      // -1f..1f (top to bottom)
  val scale: Float,        // 0.1f..3f
  val rotationDeg: Float,  // clockwise degrees
  val opacity: Float       // 0f..1f
)

object KeyframeInterpolator {
  fun evaluateOverlay(
    transform: OverlayTransformEntity?,
    keyframes: List<KeyframeEntity>,
    localTimeMs: Long
  ): EvaluatedLayerTransform {
    val base = EvaluatedLayerTransform(
      offsetX = transform?.offsetX ?: 0.35f,
      offsetY = transform?.offsetY ?: 0.35f,
      scale = transform?.scale ?: 0.42f,
      rotationDeg = transform?.rotationDeg ?: 0f,
      opacity = transform?.opacity ?: 1f
    )
    if (keyframes.isEmpty()) return base
    return interpolateSortedKeyframes(keyframes, localTimeMs)
  }

  fun evaluateText(
    textOverlay: TextOverlayEntity,
    keyframes: List<KeyframeEntity>,
    localTimeMs: Long
  ): EvaluatedLayerTransform {
    val base = EvaluatedLayerTransform(
      offsetX = textOverlay.offsetX,
      offsetY = textOverlay.offsetY,
      scale = textOverlay.scale,
      rotationDeg = textOverlay.rotationDeg,
      opacity = textOverlay.opacity
    )
    if (keyframes.isEmpty()) return base
    return interpolateSortedKeyframes(keyframes, localTimeMs)
  }

  private fun interpolateSortedKeyframes(
    rawKeyframes: List<KeyframeEntity>,
    localTimeMs: Long
  ): EvaluatedLayerTransform {
    val sorted = rawKeyframes.sortedBy { it.timeMs }
    val first = sorted.first()
    if (localTimeMs <= first.timeMs) {
      return first.toEvaluated()
    }
    val last = sorted.last()
    if (localTimeMs >= last.timeMs) {
      return last.toEvaluated()
    }

    for (i in 0 until sorted.lastIndex) {
      val k0 = sorted[i]
      val k1 = sorted[i + 1]
      if (localTimeMs in k0.timeMs..k1.timeMs) {
        val span = (k1.timeMs - k0.timeMs).coerceAtLeast(1L).toFloat()
        val fraction = ((localTimeMs - k0.timeMs).toFloat() / span).coerceIn(0f, 1f)
        return EvaluatedLayerTransform(
          offsetX = lerp(k0.positionX, k1.positionX, fraction),
          offsetY = lerp(k0.positionY, k1.positionY, fraction),
          scale = lerp(k0.scale, k1.scale, fraction).coerceAtLeast(0.08f),
          rotationDeg = lerp(k0.rotationDeg, k1.rotationDeg, fraction),
          opacity = lerp(k0.opacity, k1.opacity, fraction).coerceIn(0f, 1f)
        )
      }
    }
    return last.toEvaluated()
  }

  private fun KeyframeEntity.toEvaluated() = EvaluatedLayerTransform(
    offsetX = positionX,
    offsetY = positionY,
    scale = scale.coerceAtLeast(0.08f),
    rotationDeg = rotationDeg,
    opacity = opacity.coerceIn(0f, 1f)
  )

  private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
}

data class TimelineClipUiModel(
  val id: Long,
  val projectId: Long,
  val mediaAssetId: Long?,
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
  val speed: Float = 1.0f,
  val volume: Float = 1.0f,
  val rotationDegrees: Int = 0,
  val flipHorizontal: Boolean = false,
  val flipVertical: Boolean = false,
  val cropLeft: Float = 0f,
  val cropTop: Float = 0f,
  val cropRight: Float = 1f,
  val cropBottom: Float = 1f,
  val brightness: Float = 0f,
  val contrast: Float = 0f,
  val saturation: Float = 0f,
  val transitionType: TransitionType = TransitionType.NONE,
  val transitionDurationMs: Long = 300L,
  val overlayTransform: OverlayTransformEntity? = null,
  val textOverlay: TextOverlayEntity? = null,
  val keyframes: List<KeyframeEntity> = emptyList()
) {
  val trimmedSourceDurationMs: Long
    get() = (trimEndMs - trimStartMs).coerceAtLeast(100L)

  val timelineDurationMs: Long
    get() = (trimmedSourceDurationMs / speed.coerceIn(0.25f, 4.0f)).toLong().coerceAtLeast(100L)

  val endMs: Long
    get() = startMs + timelineDurationMs

  val hasCustomCrop: Boolean
    get() = cropLeft > 0.005f || cropTop > 0.005f || cropRight < 0.995f || cropBottom < 0.995f

  val hasColorAdjustments: Boolean
    get() = abs(brightness) > 0.01f || abs(contrast) > 0.01f || abs(saturation) > 0.01f

  val hasKeyframesEnabled: Boolean
    get() = when (trackType) {
      TrackType.OVERLAY -> overlayTransform?.hasKeyframesEnabled == true
      TrackType.TEXT -> textOverlay?.hasKeyframesEnabled == true
      else -> false
    }
}

data class ProjectStateSnapshot(
  val project: ProjectEntity,
  val assets: List<MediaAssetEntity>,
  val clips: List<TimelineClipEntity>,
  val overlayTransforms: List<OverlayTransformEntity>,
  val textOverlays: List<TextOverlayEntity>,
  val keyframes: List<KeyframeEntity>
)

enum class ClipEditToolSubmode {
  NONE,
  SPEED,
  VOLUME,
  CROP_TRANSFORM,
  COLOR_ADJUST,
  TRANSITION,
  TEXT_STYLE
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
  AUDIO,
  TEXT,
  EDIT
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
