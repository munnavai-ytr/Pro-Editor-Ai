package com.example.ui.editor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.TextAlignOption
import com.example.data.local.entity.TextOverlayEntity
import com.example.data.local.entity.TrackType
import com.example.data.local.entity.TransitionType
import com.example.domain.model.KeyframeInterpolator
import com.example.domain.model.NexaFontOption
import com.example.domain.model.TimeFormatUtils
import com.example.domain.model.TimelineClipUiModel
import com.example.ui.theme.NexaAccentGradient
import com.example.ui.theme.NexaBlack
import com.example.ui.theme.NexaBorderSubtle
import com.example.ui.theme.NexaCyan
import com.example.ui.theme.NexaPurple
import com.example.ui.theme.NexaRed
import com.example.ui.theme.NexaSurface
import com.example.ui.theme.NexaSurfaceElevated
import com.example.ui.theme.NexaTextPrimary
import com.example.ui.theme.NexaTextSecondary
import kotlin.math.abs
import kotlin.math.roundToInt

private val TEXT_COLOR_SWATCHES = listOf(
  0xFFFFFFFF.toInt(), // White
  0xFF00F0FF.toInt(), // Nexa Cyan
  0xFFFFD166.toInt(), // Studio Gold
  0xFFFF4D4D.toInt(), // Coral Red
  0xFF06D6A0.toInt(), // Mint Emerald
  0xFFA78BFA.toInt(), // Soft Purple
  0xFFFF9F1C.toInt(), // Vivid Orange
  0xFFFF66C4.toInt(), // Neon Pink
  0xFF0A0A0A.toInt()  // Jet Black
)

private val PILL_BG_SWATCHES = listOf(
  0xCC0A0A0A.toInt(), // Translucent Black
  0xE61E1E28.toInt(), // Deep Slate
  0xCC7C3AED.toInt(), // Nexa Purple Pill
  0xCC00B4D8.toInt(), // Ocean Cyan Pill
  0xCCFF4D4D.toInt(), // Crimson Pill
  0xDDFFD166.toInt(), // Gold Pill
  0xE6FFFFFF.toInt()  // Crisp White Pill
)

private val KeyframeDiamondColor = Color(0xFFFFD166)
private val AudioEmeraldAccent = Color(0xFF06D6A0)

/**
 * Audio Tab Panel: Import Audio file from storage + Mute Original Video Audio toggle + Audio clips list.
 */
@Composable
fun AudioTabDrawerPanel(
  audioClips: List<TimelineClipUiModel>,
  isOriginalAudioMuted: Boolean,
  isImporting: Boolean,
  onPickAudioFile: () -> Unit,
  onToggleOriginalAudioMute: () -> Unit,
  onSelectAudioClip: (Long) -> Unit
) {
  val haptic = LocalHapticFeedback.current

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // + Import Audio Button
      Box(
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(NexaAccentGradient)
          .clickable(enabled = !isImporting, onClick = onPickAudioFile)
          .testTag("import_audio_button"),
        contentAlignment = Alignment.Center
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = NexaBlack,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.add_audio_track),
            style = MaterialTheme.typography.labelLarge,
            color = NexaBlack,
            fontWeight = FontWeight.Bold
          )
        }
      }

      // Mute Original Video Audio Card
      Surface(
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onToggleOriginalAudioMute()
        },
        color = NexaSurfaceElevated,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isOriginalAudioMuted) NexaRed else NexaBorderSubtle),
        modifier = Modifier
          .weight(1.15f)
          .height(48.dp)
          .testTag("mute_original_audio_card")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = if (isOriginalAudioMuted) {
                Icons.AutoMirrored.Outlined.VolumeOff
              } else {
                Icons.AutoMirrored.Outlined.VolumeUp
              },
              contentDescription = null,
              tint = if (isOriginalAudioMuted) NexaRed else NexaCyan,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Mute Video",
              style = MaterialTheme.typography.labelMedium,
              color = NexaTextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Switch(
            checked = isOriginalAudioMuted,
            onCheckedChange = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onToggleOriginalAudioMute()
            },
            colors = SwitchDefaults.colors(
              checkedThumbColor = NexaTextPrimary,
              checkedTrackColor = NexaRed,
              uncheckedThumbColor = NexaTextSecondary,
              uncheckedTrackColor = NexaSurface
            ),
            modifier = Modifier
              .graphicsLayer(scaleX = 0.78f, scaleY = 0.78f)
              .testTag("mute_original_audio_switch")
          )
        }
      }
    }

    if (audioClips.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
      ) {
        items(items = audioClips, key = { it.id }) { clip ->
          Surface(
            onClick = { onSelectAudioClip(clip.id) },
            color = NexaSurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AudioEmeraldAccent.copy(alpha = 0.55f)),
            modifier = Modifier.height(38.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 10.dp)
            ) {
              Icon(
                imageVector = Icons.Outlined.AudioFile,
                contentDescription = null,
                tint = AudioEmeraldAccent,
                modifier = Modifier.size(15.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${TimeFormatUtils.formatDurationBadge(clip.timelineDurationMs)} • ${(clip.volume * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = NexaTextPrimary
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Text Tab Panel: Add Text Layer CTA + quick jump chips for existing Text layers.
 */
@Composable
fun TextTabDrawerPanel(
  textClips: List<TimelineClipUiModel>,
  onAddTextLayer: () -> Unit,
  onSelectTextClip: (Long) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 10.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(NexaAccentGradient)
        .clickable(onClick = onAddTextLayer)
        .testTag("add_text_layer_button"),
      contentAlignment = Alignment.Center
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Outlined.TextFields,
          contentDescription = null,
          tint = NexaBlack,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.add_text_layer),
          style = MaterialTheme.typography.labelLarge,
          color = NexaBlack,
          fontWeight = FontWeight.Bold
        )
      }
    }

    if (textClips.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
      ) {
        items(items = textClips, key = { it.id }) { clip ->
          val tx = clip.textOverlay
          val fontOpt = NexaFontOption.fromName(tx?.fontFamily ?: "Inter")
          Surface(
            onClick = { onSelectTextClip(clip.id) },
            color = NexaSurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, KeyframeDiamondColor.copy(alpha = 0.5f)),
            modifier = Modifier.height(38.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 10.dp)
            ) {
              Text(
                text = tx?.content ?: "Text",
                fontFamily = fontOpt.composeFontFamily,
                fontSize = 12.sp,
                color = NexaTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(110.dp)
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Keyframes & Opacity Control Strip for selected Overlay & Text layers.
 */
@Composable
fun KeyframesAndOpacityStrip(
  selectedClip: TimelineClipUiModel,
  playheadMs: Long,
  onAddOrRemoveKeyframeAtPlayhead: () -> Unit,
  onLiveOpacityChange: (Float) -> Unit,
  onCommitOpacityChange: () -> Unit,
  onResetLayerTransform: () -> Unit
) {
  val localMs = (playheadMs - selectedClip.startMs).coerceIn(0L, selectedClip.timelineDurationMs)
  val hasKeyframeHere = selectedClip.keyframes.any { abs(it.timeMs - localMs) <= 65L }
  val evalOpacity = when (selectedClip.trackType) {
    TrackType.OVERLAY -> KeyframeInterpolator.evaluateOverlay(
      selectedClip.overlayTransform,
      selectedClip.keyframes,
      localMs
    ).opacity
    TrackType.TEXT -> selectedClip.textOverlay?.let {
      KeyframeInterpolator.evaluateText(it, selectedClip.keyframes, localMs).opacity
    } ?: 1f
    else -> 1f
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(NexaSurfaceElevated)
      .padding(horizontal = 14.dp, vertical = 8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Surface(
          onClick = onAddOrRemoveKeyframeAtPlayhead,
          color = if (hasKeyframeHere) Color(0x33FFD166) else NexaSurface,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, KeyframeDiamondColor),
          modifier = Modifier
            .height(34.dp)
            .testTag("add_remove_keyframe_button")
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp)
          ) {
            DiamondIconGlyph(tint = KeyframeDiamondColor)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (hasKeyframeHere) "Remove ◆" else "+ Keyframe ◆",
              style = MaterialTheme.typography.labelSmall,
              color = KeyframeDiamondColor,
              fontWeight = FontWeight.Bold
            )
          }
        }

        Text(
          text = "${selectedClip.keyframes.size} ${if (selectedClip.keyframes.size == 1) "keyframe" else "keyframes"}",
          style = MaterialTheme.typography.labelSmall,
          color = NexaTextSecondary
        )
      }

      Surface(
        onClick = onResetLayerTransform,
        color = NexaSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, NexaBorderSubtle),
        modifier = Modifier.height(30.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 8.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = "Reset layer transform",
            tint = NexaTextSecondary,
            modifier = Modifier.size(13.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Reset Layer",
            style = MaterialTheme.typography.labelSmall,
            color = NexaTextSecondary
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Opacity ${(evalOpacity * 100f).roundToInt()}%",
        style = MaterialTheme.typography.labelSmall,
        color = NexaTextSecondary,
        modifier = Modifier.width(86.dp)
      )
      Slider(
        value = evalOpacity,
        onValueChange = onLiveOpacityChange,
        onValueChangeFinished = onCommitOpacityChange,
        valueRange = 0f..1f,
        colors = nexaSliderColors(),
        modifier = Modifier
          .weight(1f)
          .height(32.dp)
          .testTag("layer_opacity_slider")
      )
    }
  }
}

/**
 * Volume Inspector Strip (0% to 200%) for Audio and Video clips.
 */
@Composable
fun VolumeInspectorStrip(
  currentVolume: Float,
  onLiveVolumeChange: (Float) -> Unit,
  onCommitVolume: () -> Unit,
  onSelectPresetVolume: (Float) -> Unit
) {
  val presets = listOf(0f, 0.5f, 1.0f, 1.5f, 2.0f)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(NexaSurfaceElevated)
      .padding(horizontal = 14.dp, vertical = 8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Clip Volume (0–200%)",
        style = MaterialTheme.typography.labelLarge,
        color = NexaTextPrimary
      )
      Text(
        text = "${(currentVolume * 100f).roundToInt()}%",
        style = MaterialTheme.typography.labelMedium,
        color = NexaCyan,
        fontWeight = FontWeight.Bold
      )
    }

    Slider(
      value = currentVolume.coerceIn(0f, 2.0f),
      onValueChange = onLiveVolumeChange,
      onValueChangeFinished = onCommitVolume,
      valueRange = 0f..2.0f,
      colors = nexaSliderColors(),
      modifier = Modifier
        .fillMaxWidth()
        .height(34.dp)
        .testTag("clip_volume_slider")
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      presets.forEach { preset ->
        val pct = (preset * 100f).roundToInt()
        val active = abs(currentVolume - preset) < 0.03f
        Surface(
          onClick = { onSelectPresetVolume(preset) },
          color = if (active) Color(0x2800F0FF) else NexaSurface,
          shape = RoundedCornerShape(6.dp),
          border = BorderStroke(1.dp, if (active) NexaCyan else NexaBorderSubtle),
          modifier = Modifier
            .weight(1f)
            .height(30.dp)
            .testTag("volume_preset_$pct")
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = "$pct%",
              style = MaterialTheme.typography.labelSmall,
              color = if (active) NexaCyan else NexaTextSecondary,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }
    }
  }
}

/**
 * Crop / Rotate 90° / Flip H / Flip V Inspector Strip.
 */
@Composable
fun CropRotateFlipStrip(
  selectedClip: TimelineClipUiModel,
  isInteractiveCropMode: Boolean,
  onRotate90: () -> Unit,
  onFlipHorizontal: () -> Unit,
  onFlipVertical: () -> Unit,
  onToggleInteractiveCrop: () -> Unit,
  onResetAll: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(NexaSurfaceElevated)
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 14.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TransformChipButton(
      icon = Icons.Outlined.Crop,
      label = if (isInteractiveCropMode) "Editing Crop" else "Crop Frame",
      isActive = isInteractiveCropMode || selectedClip.hasCustomCrop,
      testTag = "action_interactive_crop",
      onClick = onToggleInteractiveCrop
    )

    TransformChipButton(
      icon = Icons.Outlined.RotateRight,
      label = "Rotate (${selectedClip.rotationDegrees}°)",
      isActive = selectedClip.rotationDegrees % 360 != 0,
      testTag = "action_rotate_90",
      onClick = onRotate90
    )

    TransformChipButton(
      icon = Icons.Outlined.Flip,
      label = "Flip H",
      isActive = selectedClip.flipHorizontal,
      testTag = "action_flip_h",
      onClick = onFlipHorizontal
    )

    TransformChipButton(
      icon = Icons.Outlined.Flip,
      label = "Flip V",
      isActive = selectedClip.flipVertical,
      rotateIcon90 = true,
      testTag = "action_flip_v",
      onClick = onFlipVertical
    )

    TransformChipButton(
      icon = Icons.Rounded.Refresh,
      label = stringResource(R.string.action_reset),
      isActive = false,
      testTag = "action_reset_transform",
      onClick = onResetAll
    )
  }
}

@Composable
private fun TransformChipButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  isActive: Boolean,
  testTag: String,
  rotateIcon90: Boolean = false,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    color = if (isActive) Color(0x2800F0FF) else NexaSurface,
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(1.dp, if (isActive) NexaCyan else NexaBorderSubtle),
    modifier = Modifier
      .height(40.dp)
      .testTag(testTag)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 12.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isActive) NexaCyan else NexaTextPrimary,
        modifier = Modifier
          .size(16.dp)
          .graphicsLayer { rotationZ = if (rotateIcon90) 90f else 0f }
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (isActive) NexaCyan else NexaTextPrimary,
        fontWeight = FontWeight.SemiBold
      )
    }
  }
}

/**
 * Color Adjustment Strip: Brightness, Contrast, and Saturation sliders with live Media3 OpenGL preview.
 */
@Composable
fun ColorAdjustmentStrip(
  brightness: Float,
  contrast: Float,
  saturation: Float,
  onLiveChange: (brightness: Float, contrast: Float, saturation: Float) -> Unit,
  onCommitChange: () -> Unit,
  onReset: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(NexaSurfaceElevated)
      .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Color Grade (Media3 Live Effects)",
        style = MaterialTheme.typography.labelLarge,
        color = NexaTextPrimary
      )
      Text(
        text = stringResource(R.string.action_reset),
        style = MaterialTheme.typography.labelSmall,
        color = NexaCyan,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
          .clip(RoundedCornerShape(4.dp))
          .clickable(onClick = onReset)
          .padding(horizontal = 6.dp, vertical = 2.dp)
          .testTag("action_reset_color")
      )
    }

    CompactLabeledSlider(
      label = "Brightness",
      displayValue = formatSignedPercent(brightness),
      value = brightness,
      range = -1f..1f,
      testTag = "slider_brightness",
      onValueChange = { b -> onLiveChange(b, contrast, saturation) },
      onValueChangeFinished = onCommitChange
    )

    CompactLabeledSlider(
      label = "Contrast",
      displayValue = formatSignedPercent(contrast),
      value = contrast,
      range = -0.9f..0.9f,
      testTag = "slider_contrast",
      onValueChange = { c -> onLiveChange(brightness, c, saturation) },
      onValueChangeFinished = onCommitChange
    )

    CompactLabeledSlider(
      label = "Saturation",
      displayValue = formatSignedPercent(saturation),
      value = saturation,
      range = -1f..1f,
      testTag = "slider_saturation",
      onValueChange = { s -> onLiveChange(brightness, contrast, s) },
      onValueChangeFinished = onCommitChange
    )
  }
}

@Composable
private fun CompactLabeledSlider(
  label: String,
  displayValue: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  testTag: String,
  onValueChange: (Float) -> Unit,
  onValueChangeFinished: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = NexaTextSecondary,
      modifier = Modifier.width(68.dp)
    )
    Slider(
      value = value.coerceIn(range.start, range.endInclusive),
      onValueChange = onValueChange,
      onValueChangeFinished = onValueChangeFinished,
      valueRange = range,
      colors = nexaSliderColors(),
      modifier = Modifier
        .weight(1f)
        .height(28.dp)
        .testTag(testTag)
    )
    Text(
      text = displayValue,
      style = MaterialTheme.typography.labelSmall,
      color = NexaCyan,
      modifier = Modifier.width(42.dp)
    )
  }
}

private fun formatSignedPercent(v: Float): String {
  val pct = (v * 100f).roundToInt()
  return if (pct > 0) "+$pct" else "$pct"
}

/**
 * Basic Transitions Picker Strip: None, Fade, Slide Left, Slide Right, Zoom In + Duration (ms).
 */
@Composable
fun TransitionInspectorStrip(
  selectedClip: TimelineClipUiModel,
  onSelectTransition: (TransitionType, Long) -> Unit
) {
  var localDurMs by remember(selectedClip.id, selectedClip.transitionDurationMs) {
    mutableFloatStateOf(selectedClip.transitionDurationMs.toFloat())
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(NexaSurfaceElevated)
      .padding(horizontal = 14.dp, vertical = 8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Cut Transition",
        style = MaterialTheme.typography.labelLarge,
        color = NexaTextPrimary
      )
      Text(
        text = "${localDurMs.roundToInt()} ms",
        style = MaterialTheme.typography.labelSmall,
        color = NexaCyan,
        fontWeight = FontWeight.Bold
      )
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      TransitionType.entries.forEach { type ->
        val isSelected = selectedClip.transitionType == type
        Box(
          modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
              if (isSelected) NexaAccentGradient
              else Brush.horizontalGradient(listOf(NexaSurface, NexaSurface))
            )
            .border(
              1.dp,
              if (isSelected) Color.Transparent else NexaBorderSubtle,
              RoundedCornerShape(8.dp)
            )
            .clickable { onSelectTransition(type, localDurMs.roundToInt().toLong()) }
            .padding(horizontal = 12.dp)
            .testTag("transition_option_${type.name}"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = type.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) NexaBlack else NexaTextPrimary,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    if (selectedClip.transitionType != TransitionType.NONE) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Duration",
          style = MaterialTheme.typography.labelSmall,
          color = NexaTextSecondary,
          modifier = Modifier.width(56.dp)
        )
        Slider(
          value = localDurMs.coerceIn(200f, 1000f),
          onValueChange = { localDurMs = it },
          onValueChangeFinished = {
            onSelectTransition(selectedClip.transitionType, localDurMs.roundToInt().toLong())
          },
          valueRange = 200f..1000f,
          colors = nexaSliderColors(),
          modifier = Modifier
            .weight(1f)
            .height(28.dp)
            .testTag("transition_duration_slider")
        )
      }
    }
  }
}

/**
 * Inline Text Style Strip for selected Text Overlay clips.
 */
@Composable
fun InlineTextStyleStrip(
  textOverlay: TextOverlayEntity,
  onEditContentClick: () -> Unit,
  onUpdateStyle: (
    fontFamily: String?,
    fontSize: Float?,
    color: Int?,
    backgroundColor: Int?,
    updateBackground: Boolean,
    alignment: TextAlignOption?,
    isBold: Boolean?,
    isItalic: Boolean?
  ) -> Unit
) {
  var fontSizeSlider by remember(textOverlay.id, textOverlay.fontSize) {
    mutableFloatStateOf(textOverlay.fontSize)
  }
  val currentAlign = TextAlignOption.fromString(textOverlay.alignment)
  val hasPill = textOverlay.backgroundColor != null

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(NexaSurfaceElevated)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    // Row 1: 6 Bundled Google Fonts + Edit Text button
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Surface(
        onClick = onEditContentClick,
        color = Color(0x2800F0FF),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, NexaCyan),
        modifier = Modifier
          .height(32.dp)
          .testTag("inline_edit_text_content_button")
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
          Text(
            text = "Edit Text",
            style = MaterialTheme.typography.labelSmall,
            color = NexaCyan,
            fontWeight = FontWeight.Bold
          )
        }
      }

      NexaFontOption.entries.forEach { fontOpt ->
        val selected = fontOpt.familyName.equals(textOverlay.fontFamily, ignoreCase = true)
        Surface(
          onClick = {
            onUpdateStyle(fontOpt.familyName, null, null, null, false, null, null, null)
          },
          color = if (selected) NexaPurple.copy(alpha = 0.35f) else NexaSurface,
          shape = RoundedCornerShape(6.dp),
          border = BorderStroke(1.dp, if (selected) NexaCyan else NexaBorderSubtle),
          modifier = Modifier
            .height(32.dp)
            .testTag("font_chip_${fontOpt.name}")
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(
              text = fontOpt.displayLabel,
              fontFamily = fontOpt.composeFontFamily,
              fontSize = 12.sp,
              color = NexaTextPrimary,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
          }
        }
      }
    }

    // Row 2: Bold, Italic, Alignment, Pill Toggle + Font Size Slider
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      SmallToggleIconChip(
        icon = Icons.Outlined.FormatBold,
        contentDescription = "Bold",
        active = textOverlay.isBold,
        onClick = { onUpdateStyle(null, null, null, null, false, null, !textOverlay.isBold, null) }
      )
      SmallToggleIconChip(
        icon = Icons.Outlined.FormatItalic,
        contentDescription = "Italic",
        active = textOverlay.isItalic,
        onClick = { onUpdateStyle(null, null, null, null, false, null, null, !textOverlay.isItalic) }
      )
      SmallToggleIconChip(
        icon = when (currentAlign) {
          TextAlignOption.LEFT -> Icons.AutoMirrored.Outlined.FormatAlignLeft
          TextAlignOption.CENTER -> Icons.Outlined.FormatAlignCenter
          TextAlignOption.RIGHT -> Icons.AutoMirrored.Outlined.FormatAlignRight
        },
        contentDescription = "Cycle alignment",
        active = true,
        onClick = {
          val next = TextAlignOption.entries[(currentAlign.ordinal + 1) % TextAlignOption.entries.size]
          onUpdateStyle(null, null, null, null, false, next, null, null)
        }
      )

      Surface(
        onClick = {
          val nextBg = if (hasPill) null else PILL_BG_SWATCHES.first()
          onUpdateStyle(null, null, null, nextBg, true, null, null, null)
        },
        color = if (hasPill) Color(0x2800F0FF) else NexaSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (hasPill) NexaCyan else NexaBorderSubtle),
        modifier = Modifier.height(30.dp)
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
          Text(
            text = if (hasPill) "Pill ON" else "Pill OFF",
            style = MaterialTheme.typography.labelSmall,
            color = if (hasPill) NexaCyan else NexaTextSecondary
          )
        }
      }

      Slider(
        value = fontSizeSlider.coerceIn(14f, 72f),
        onValueChange = { fontSizeSlider = it },
        onValueChangeFinished = {
          onUpdateStyle(null, fontSizeSlider, null, null, false, null, null, null)
        },
        valueRange = 14f..72f,
        colors = nexaSliderColors(),
        modifier = Modifier
          .weight(1f)
          .height(28.dp)
          .testTag("text_size_slider")
      )
    }

    // Row 3: Text Color Swatches + Optional Pill Background Color Swatches
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Text",
        style = MaterialTheme.typography.labelSmall,
        color = NexaTextSecondary
      )
      TEXT_COLOR_SWATCHES.forEach { argb ->
        ColorSwatchDot(
          argb = argb,
          selected = textOverlay.color == argb,
          onClick = { onUpdateStyle(null, null, argb, null, false, null, null, null) }
        )
      }

      if (hasPill) {
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Pill",
          style = MaterialTheme.typography.labelSmall,
          color = NexaTextSecondary
        )
        PILL_BG_SWATCHES.forEach { bgArgb ->
          ColorSwatchDot(
            argb = bgArgb,
            selected = textOverlay.backgroundColor == bgArgb,
            onClick = { onUpdateStyle(null, null, null, bgArgb, true, null, null, null) }
          )
        }
      }
    }
  }
}

@Composable
private fun SmallToggleIconChip(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String,
  active: Boolean,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    color = if (active) Color(0x2800F0FF) else NexaSurface,
    shape = RoundedCornerShape(6.dp),
    border = BorderStroke(1.dp, if (active) NexaCyan else NexaBorderSubtle),
    modifier = Modifier.size(30.dp)
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = if (active) NexaCyan else NexaTextSecondary,
        modifier = Modifier.size(16.dp)
      )
    }
  }
}

@Composable
private fun ColorSwatchDot(
  argb: Int,
  selected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .size(24.dp)
      .clip(CircleShape)
      .background(Color(argb))
      .border(
        width = if (selected) 2.2.dp else 1.dp,
        color = if (selected) NexaCyan else NexaBorderSubtle,
        shape = CircleShape
      )
      .clickable(onClick = onClick)
  )
}

/**
 * Full Text Input & Initial Style Dialog when adding or editing a Text Overlay layer.
 */
@Composable
fun TextOverlayEditorDialog(
  initialOverlay: TextOverlayEntity?,
  onDismiss: () -> Unit,
  onConfirm: (
    content: String,
    fontFamily: String,
    fontSize: Float,
    color: Int,
    backgroundColor: Int?,
    alignment: TextAlignOption,
    isBold: Boolean,
    isItalic: Boolean
  ) -> Unit
) {
  var contentDraft by rememberSaveable(initialOverlay?.id) {
    mutableStateOf(initialOverlay?.content ?: "")
  }
  var selectedFont by rememberSaveable(initialOverlay?.id) {
    mutableStateOf(initialOverlay?.fontFamily ?: NexaFontOption.INTER.familyName)
  }
  var fontSize by rememberSaveable(initialOverlay?.id) {
    mutableFloatStateOf(initialOverlay?.fontSize ?: 28f)
  }
  var textColor by rememberSaveable(initialOverlay?.id) {
    mutableIntStateOf(initialOverlay?.color ?: 0xFFFFFFFF.toInt())
  }
  var pillEnabled by rememberSaveable(initialOverlay?.id) {
    mutableStateOf(initialOverlay == null || initialOverlay.backgroundColor != null)
  }
  var pillColor by rememberSaveable(initialOverlay?.id) {
    mutableIntStateOf(initialOverlay?.backgroundColor ?: PILL_BG_SWATCHES.first())
  }
  var alignmentName by rememberSaveable(initialOverlay?.id) {
    mutableStateOf(initialOverlay?.alignment ?: TextAlignOption.CENTER.raw)
  }
  var isBold by rememberSaveable(initialOverlay?.id) {
    mutableStateOf(initialOverlay?.isBold ?: true)
  }
  var isItalic by rememberSaveable(initialOverlay?.id) {
    mutableStateOf(initialOverlay?.isItalic ?: false)
  }

  val resolvedFontOpt = NexaFontOption.fromName(selectedFont)
  val resolvedAlign = TextAlignOption.fromString(alignmentName)

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NexaSurfaceElevated,
    shape = RoundedCornerShape(18.dp),
    title = {
      Text(
        text = if (initialOverlay == null) stringResource(R.string.add_text_layer) else stringResource(R.string.clip_edit_text),
        style = MaterialTheme.typography.titleLarge,
        color = NexaTextPrimary
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = contentDraft,
          onValueChange = { contentDraft = it },
          placeholder = { Text(stringResource(R.string.text_input_hint)) },
          textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = resolvedFontOpt.composeFontFamily,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
          ),
          maxLines = 4,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NexaCyan,
            unfocusedBorderColor = NexaBorderSubtle,
            focusedTextColor = NexaTextPrimary,
            unfocusedTextColor = NexaTextPrimary,
            cursorColor = NexaCyan
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("text_overlay_input")
        )

        // Font Family Strip
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          NexaFontOption.entries.forEach { opt ->
            val active = opt.familyName.equals(selectedFont, ignoreCase = true)
            Surface(
              onClick = { selectedFont = opt.familyName },
              color = if (active) Color(0x2800F0FF) else NexaSurface,
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, if (active) NexaCyan else NexaBorderSubtle),
              modifier = Modifier.height(34.dp)
            ) {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                Text(
                  text = opt.displayLabel,
                  fontFamily = opt.composeFontFamily,
                  fontSize = 13.sp,
                  color = NexaTextPrimary
                )
              }
            }
          }
        }

        // Style Toggles Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          SmallToggleIconChip(
            icon = Icons.Outlined.FormatBold,
            contentDescription = "Bold",
            active = isBold,
            onClick = { isBold = !isBold }
          )
          SmallToggleIconChip(
            icon = Icons.Outlined.FormatItalic,
            contentDescription = "Italic",
            active = isItalic,
            onClick = { isItalic = !isItalic }
          )
          TextAlignOption.entries.forEach { alignOpt ->
            val active = resolvedAlign == alignOpt
            Surface(
              onClick = { alignmentName = alignOpt.raw },
              color = if (active) Color(0x2800F0FF) else NexaSurface,
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(1.dp, if (active) NexaCyan else NexaBorderSubtle),
              modifier = Modifier.height(30.dp)
            ) {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                  text = alignOpt.label,
                  style = MaterialTheme.typography.labelSmall,
                  color = if (active) NexaCyan else NexaTextSecondary
                )
              }
            }
          }
        }

        // Size Slider
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "Size ${fontSize.roundToInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = NexaTextSecondary,
            modifier = Modifier.width(56.dp)
          )
          Slider(
            value = fontSize,
            onValueChange = { fontSize = it },
            valueRange = 16f..68f,
            colors = nexaSliderColors(),
            modifier = Modifier
              .weight(1f)
              .height(28.dp)
          )
        }

        // Text Color Swatches
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Color", style = MaterialTheme.typography.labelSmall, color = NexaTextSecondary)
          TEXT_COLOR_SWATCHES.forEach { c ->
            ColorSwatchDot(argb = c, selected = textColor == c, onClick = { textColor = c })
          }
        }

        // Background Pill Toggle + Swatches
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            onClick = { pillEnabled = !pillEnabled },
            color = if (pillEnabled) Color(0x2800F0FF) else NexaSurface,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, if (pillEnabled) NexaCyan else NexaBorderSubtle),
            modifier = Modifier.height(28.dp)
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
              Text(
                text = if (pillEnabled) "Pill BG: ON" else "Pill BG: OFF",
                style = MaterialTheme.typography.labelSmall,
                color = if (pillEnabled) NexaCyan else NexaTextSecondary
              )
            }
          }
          if (pillEnabled) {
            PILL_BG_SWATCHES.forEach { bg ->
              ColorSwatchDot(argb = bg, selected = pillColor == bg, onClick = { pillColor = bg })
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onConfirm(
            contentDraft,
            selectedFont,
            fontSize,
            textColor,
            if (pillEnabled) pillColor else null,
            resolvedAlign,
            isBold,
            isItalic
          )
        },
        enabled = contentDraft.isNotBlank(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = NexaCyan,
          contentColor = NexaBlack
        ),
        modifier = Modifier.testTag("confirm_text_overlay_button")
      ) {
        Text(
          text = if (initialOverlay == null) "Add Text" else stringResource(R.string.action_save),
          fontWeight = FontWeight.Bold
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.action_cancel), color = NexaTextSecondary)
      }
    }
  )
}

@Composable
fun DiamondIconGlyph(tint: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier.size(14.dp)) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
      moveTo(w / 2f, 0f)
      lineTo(w, h / 2f)
      lineTo(w / 2f, h)
      lineTo(0f, h / 2f)
      close()
    }
    drawPath(path, color = tint)
  }
}

@Composable
private fun nexaSliderColors() = SliderDefaults.colors(
  thumbColor = NexaCyan,
  activeTrackColor = NexaCyan,
  inactiveTrackColor = NexaBorderSubtle
)
