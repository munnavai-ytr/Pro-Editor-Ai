package com.example.data.media

import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.Matrix as GlMatrix
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.OverlaySettings
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import com.example.data.local.entity.TransitionType
import com.example.domain.model.KeyframeInterpolator
import com.example.domain.model.TimelineClipUiModel
import kotlin.math.abs

data class TransitionVisualFrame(
  val translationFractionX: Float = 0f, // -1f..1f of stage width
  val scale: Float = 1f,
  val alpha: Float = 1f
)

@OptIn(UnstableApi::class)
object Media3EffectsFactory {

  /**
   * Builds per-clip Media3 OpenGL video effects for Crop, Rotate 90°, Flip H/V,
   * and Color Adjustments (Brightness, Contrast, Saturation).
   * Shared identically between ExoPlayer live preview and Media3 Transformer export.
   */
  fun buildPerClipVideoEffects(clip: TimelineClipUiModel): List<Effect> {
    val effects = mutableListOf<Effect>()

    // 1. Crop (convert UI [0..1] top-left space -> Media3 OpenGL NDC [-1..1] bottom-left space)
    if (clip.hasCustomCrop) {
      val left = (clip.cropLeft.coerceIn(0f, 0.9f) * 2f - 1f).coerceIn(-1f, 0.9f)
      val right = (clip.cropRight.coerceIn(0.1f, 1f) * 2f - 1f).coerceIn(left + 0.1f, 1f)
      val top = (1f - clip.cropTop.coerceIn(0f, 0.9f) * 2f).coerceIn(-0.9f, 1f)
      val bottom = (1f - clip.cropBottom.coerceIn(0.1f, 1f) * 2f).coerceIn(-1f, top - 0.1f)
      if (right > left && top > bottom) {
        effects.add(Crop(left, right, bottom, top))
      }
    }

    // 2. Rotate 90° & Horizontal/Vertical Flip
    val normalizedClockwiseDeg = ((clip.rotationDegrees % 360) + 360) % 360
    if (normalizedClockwiseDeg != 0 || clip.flipHorizontal || clip.flipVertical) {
      val sx = if (clip.flipHorizontal) -1f else 1f
      val sy = if (clip.flipVertical) -1f else 1f
      // Media3 ScaleAndRotateTransformation rotates counter-clockwise
      val ccwDegrees = ((360 - normalizedClockwiseDeg) % 360).toFloat()
      effects.add(
        ScaleAndRotateTransformation.Builder()
          .setScale(sx, sy)
          .setRotationDegrees(ccwDegrees)
          .build()
      )
    }

    // 3. Color Adjustments: Brightness, Contrast, Saturation
    if (abs(clip.brightness) > 0.01f) {
      val rgbGain = (1f + clip.brightness * 0.65f).coerceIn(0.08f, 2.2f)
      effects.add(
        RgbAdjustment.Builder()
          .setRedScale(rgbGain)
          .setGreenScale(rgbGain)
          .setBlueScale(rgbGain)
          .build()
      )
    }

    if (abs(clip.contrast) > 0.01f) {
      effects.add(Contrast(clip.contrast.coerceIn(-0.9f, 0.9f)))
    }

    if (abs(clip.saturation) > 0.01f || abs(clip.brightness) > 0.01f) {
      effects.add(
        HslAdjustment.Builder()
          .adjustSaturation((clip.saturation * 100f).coerceIn(-100f, 100f))
          .adjustLightness((clip.brightness * 25f).coerceIn(-50f, 50f))
          .build()
      )
    }

    return effects
  }

  /**
   * Builds a PCM gain audio processor scaling clip audio between 0% and 200% (0.0f..2.0f).
   */
  fun buildVolumeAudioProcessor(volume: Float): ChannelMixingAudioProcessor {
    val safeGain = volume.coerceIn(0f, 2.0f)
    return ChannelMixingAudioProcessor().apply {
      for (channels in 1..6) {
        putChannelMixingMatrix(
          ChannelMixingMatrix.createForConstantGain(channels, channels).scaleBy(safeGain)
        )
      }
    }
  }

  /**
   * Evaluates the active transition state (Fade, Slide Left, Slide Right, Zoom In)
   * at a global timeline timestamp [timelineMs] across adjacent Main Video Track clips.
   */
  fun evaluateTransitionAtMs(
    mainClips: List<TimelineClipUiModel>,
    timelineMs: Long
  ): TransitionVisualFrame {
    if (mainClips.size < 2) return TransitionVisualFrame()

    for (i in 1 until mainClips.size) {
      val prevClip = mainClips[i - 1]
      val nextClip = mainClips[i]
      val type = nextClip.transitionType
      if (type == TransitionType.NONE) continue

      val cutMs = nextClip.startMs
      val maxHalf = minOf(
        nextClip.transitionDurationMs / 2L,
        prevClip.timelineDurationMs / 2L,
        nextClip.timelineDurationMs / 2L
      ).coerceAtLeast(60L)

      val winStart = cutMs - maxHalf
      val winEnd = cutMs + maxHalf

      if (timelineMs in winStart..winEnd) {
        val isOutgoing = timelineMs < cutMs
        // Outgoing progress: 0f -> 1f approaching cutMs; Incoming progress: 0f -> 1f leaving cutMs
        val progress = if (isOutgoing) {
          ((timelineMs - winStart).toFloat() / maxHalf.toFloat()).coerceIn(0f, 1f)
        } else {
          ((timelineMs - cutMs).toFloat() / maxHalf.toFloat()).coerceIn(0f, 1f)
        }

        return when (type) {
          TransitionType.NONE -> TransitionVisualFrame()
          TransitionType.FADE -> {
            val alpha = if (isOutgoing) (1f - progress) else progress
            TransitionVisualFrame(alpha = alpha.coerceIn(0f, 1f))
          }
          TransitionType.SLIDE_LEFT -> {
            val tx = if (isOutgoing) -progress else (1f - progress)
            TransitionVisualFrame(translationFractionX = tx)
          }
          TransitionType.SLIDE_RIGHT -> {
            val tx = if (isOutgoing) progress else -(1f - progress)
            TransitionVisualFrame(translationFractionX = tx)
          }
          TransitionType.ZOOM_IN -> {
            val scale = if (isOutgoing) (1f + 0.75f * progress) else (0.65f + 0.35f * progress)
            val alpha = if (isOutgoing) (1f - progress * 0.85f) else (0.15f + progress * 0.85f)
            TransitionVisualFrame(scale = scale, alpha = alpha.coerceIn(0f, 1f))
          }
        }
      }
    }

    return TransitionVisualFrame()
  }

  /**
   * Builds Composition-level Media3 OpenGL transition effects (MatrixTransformation + RgbMatrix)
   * so exported MP4s render Fade, Slide Left, Slide Right, and Zoom In transitions natively.
   */
  fun buildCompositionTransitionEffects(mainClips: List<TimelineClipUiModel>): List<Effect> {
    val hasTransitions = mainClips.drop(1).any { it.transitionType != TransitionType.NONE }
    if (!hasTransitions) return emptyList()

    val clipsSnapshot = mainClips.toList()

    val spatialTransition = MatrixTransformation { presentationTimeUs ->
      val state = evaluateTransitionAtMs(clipsSnapshot, presentationTimeUs / 1_000L)
      val matrix = Matrix()
      if (abs(state.scale - 1f) > 0.002f) {
        matrix.postScale(state.scale, state.scale)
      }
      if (abs(state.translationFractionX) > 0.002f) {
        // In Media3 NDC [-1..1], full frame width is 2.0 units
        matrix.postTranslate(state.translationFractionX * 2f, 0f)
      }
      matrix
    }

    val opacityTransition = RgbMatrix { presentationTimeUs, _ ->
      val state = evaluateTransitionAtMs(clipsSnapshot, presentationTimeUs / 1_000L)
      val m = FloatArray(16)
      GlMatrix.setIdentityM(m, 0)
      val a = state.alpha.coerceIn(0f, 1f)
      m[0] = a
      m[5] = a
      m[10] = a
      m
    }

    return listOf(spatialTransition, opacityTransition)
  }

  /**
   * Builds a Media3 OverlayEffect containing keyframe-animated BitmapOverlays for all Text clips.
   */
  fun buildTextOverlayEffect(
    textClips: List<TimelineClipUiModel>,
    textRenderer: TextBitmapRenderer,
    outputHeightPx: Int
  ): OverlayEffect? {
    val validTextClips = textClips.filter { it.textOverlay != null }
    if (validTextClips.isEmpty()) return null

    val overlays: List<TextureOverlay> = validTextClips.map { clip ->
      val textEntity = clip.textOverlay!!
      val renderedBitmap: Bitmap = textRenderer.renderTextBitmap(textEntity, outputHeightPx)
      val startUs = clip.startMs * 1_000L
      val endUs = clip.endMs * 1_000L
      val keyframesSnapshot = clip.keyframes.toList()

      object : BitmapOverlay() {
        override fun getBitmap(presentationTimeUs: Long): Bitmap = renderedBitmap

        override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
          if (presentationTimeUs < startUs || presentationTimeUs >= endUs) {
            return StaticOverlaySettings.Builder()
              .setAlphaScale(0f)
              .build()
          }
          val localTimeMs = ((presentationTimeUs - startUs) / 1_000L)
            .coerceIn(0L, clip.timelineDurationMs)
          val eval = KeyframeInterpolator.evaluateText(
            textOverlay = textEntity,
            keyframes = keyframesSnapshot,
            localTimeMs = localTimeMs
          )
          return StaticOverlaySettings.Builder()
            .setScale(eval.scale, eval.scale)
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(eval.offsetX, -eval.offsetY)
            .setRotationDegrees(-eval.rotationDeg)
            .setAlphaScale(eval.opacity)
            .build()
        }
      }
    }

    return OverlayEffect(overlays)
  }
}
