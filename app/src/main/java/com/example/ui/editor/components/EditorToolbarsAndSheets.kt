package com.example.ui.editor.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.local.entity.MediaType
import com.example.data.local.entity.TextAlignOption
import com.example.data.local.entity.TrackType
import com.example.data.local.entity.TransitionType
import com.example.domain.model.ClipEditToolSubmode
import com.example.domain.model.EditorBottomTab
import com.example.domain.model.ExportResolution
import com.example.domain.model.ExportState
import com.example.domain.model.TimeFormatUtils
import com.example.domain.model.TimelineClipUiModel
import com.example.ui.theme.NexaAccentGradient
import com.example.ui.theme.NexaBlack
import com.example.ui.theme.NexaBorderSubtle
import com.example.ui.theme.NexaCyan
import com.example.ui.theme.NexaPurple
import com.example.ui.theme.NexaRed
import com.example.ui.theme.NexaRedDim
import com.example.ui.theme.NexaSuccess
import com.example.ui.theme.NexaSurface
import com.example.ui.theme.NexaSurfaceElevated
import com.example.ui.theme.NexaTextPrimary
import com.example.ui.theme.NexaTextSecondary
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private val SPEED_OPTIONS = listOf(0.5f, 1.0f, 1.5f, 2.0f, 3.0f)

@Composable
fun EditorContextualPanel(
  activeTab: EditorBottomTab,
  activeSubmode: ClipEditToolSubmode,
  isInteractiveCropMode: Boolean,
  selectedClip: TimelineClipUiModel?,
  playheadMs: Long,
  isOriginalAudioMuted: Boolean,
  assets: List<MediaAssetEntity>,
  audioTrackClips: List<TimelineClipUiModel>,
  textTrackClips: List<TimelineClipUiModel>,
  isImportingMedia: Boolean,
  onSelectClip: (Long) -> Unit,
  onToggleSubmode: (ClipEditToolSubmode) -> Unit,
  onSplitClip: () -> Unit,
  onSelectSpeed: (Float) -> Unit,
  onLiveVolumeChange: (Float) -> Unit,
  onCommitVolume: () -> Unit,
  onSelectPresetVolume: (Float) -> Unit,
  onToggleKeyframes: () -> Unit,
  onAddOrRemoveKeyframeAtPlayhead: () -> Unit,
  onLiveLayerOpacityChange: (Float) -> Unit,
  onCommitLayerOpacity: () -> Unit,
  onResetLayerTransform: () -> Unit,
  onRotate90: () -> Unit,
  onFlipHorizontal: () -> Unit,
  onFlipVertical: () -> Unit,
  onToggleInteractiveCrop: () -> Unit,
  onResetCropAndTransform: () -> Unit,
  onLiveColorChange: (Float, Float, Float) -> Unit,
  onCommitColorChange: () -> Unit,
  onResetColorAdjustments: () -> Unit,
  onSelectTransition: (TransitionType, Long) -> Unit,
  onOpenAddTextDialog: () -> Unit,
  onOpenEditTextDialog: () -> Unit,
  onUpdateSelectedTextStyle: (
    fontFamily: String?,
    fontSize: Float?,
    color: Int?,
    backgroundColor: Int?,
    updateBackground: Boolean,
    alignment: TextAlignOption?,
    isBold: Boolean?,
    isItalic: Boolean?
  ) -> Unit,
  onDuplicateClip: () -> Unit,
  onDeleteClip: () -> Unit,
  onPickMainMedia: () -> Unit,
  onPickOverlayMedia: () -> Unit,
  onPickAudioFile: () -> Unit,
  onToggleOriginalAudioMute: () -> Unit,
  onReuseAsset: (assetId: Long, asOverlay: Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  val haptic = LocalHapticFeedback.current

  Surface(
    color = NexaSurface,
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      HorizontalDivider(color = NexaBorderSubtle)

      // Keyframes & Opacity Strip for Overlay & Text clips when Keyframe mode is enabled
      val showKeyframeBar = activeTab == EditorBottomTab.EDIT &&
        selectedClip != null &&
        (selectedClip.trackType == TrackType.OVERLAY || selectedClip.trackType == TrackType.TEXT) &&
        (selectedClip.hasKeyframesEnabled || selectedClip.keyframes.isNotEmpty())

      AnimatedVisibility(
        visible = showKeyframeBar,
        enter = expandVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
        exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
      ) {
        if (selectedClip != null) {
          KeyframesAndOpacityStrip(
            selectedClip = selectedClip,
            playheadMs = playheadMs,
            onAddOrRemoveKeyframeAtPlayhead = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onAddOrRemoveKeyframeAtPlayhead()
            },
            onLiveOpacityChange = onLiveLayerOpacityChange,
            onCommitOpacityChange = onCommitLayerOpacity,
            onResetLayerTransform = onResetLayerTransform
          )
        }
      }

      // Active Clip Inspector Sub-Panel (Speed, Volume, Crop/Flip, Color Adjust, Transition, Text Style)
      AnimatedVisibility(
        visible = selectedClip != null && activeTab == EditorBottomTab.EDIT && activeSubmode != ClipEditToolSubmode.NONE,
        enter = expandVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
        exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
      ) {
        if (selectedClip != null) {
          when (activeSubmode) {
            ClipEditToolSubmode.SPEED -> {
              SpeedPickerStrip(
                currentSpeed = selectedClip.speed,
                onSelectSpeed = { speed ->
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onSelectSpeed(speed)
                }
              )
            }

            ClipEditToolSubmode.VOLUME -> {
              VolumeInspectorStrip(
                currentVolume = selectedClip.volume,
                onLiveVolumeChange = onLiveVolumeChange,
                onCommitVolume = onCommitVolume,
                onSelectPresetVolume = { preset ->
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onSelectPresetVolume(preset)
                }
              )
            }

            ClipEditToolSubmode.CROP_TRANSFORM -> {
              CropRotateFlipStrip(
                selectedClip = selectedClip,
                isInteractiveCropMode = isInteractiveCropMode,
                onRotate90 = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onRotate90()
                },
                onFlipHorizontal = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onFlipHorizontal()
                },
                onFlipVertical = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onFlipVertical()
                },
                onToggleInteractiveCrop = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onToggleInteractiveCrop()
                },
                onResetAll = onResetCropAndTransform
              )
            }

            ClipEditToolSubmode.COLOR_ADJUST -> {
              ColorAdjustmentStrip(
                brightness = selectedClip.brightness,
                contrast = selectedClip.contrast,
                saturation = selectedClip.saturation,
                onLiveChange = onLiveColorChange,
                onCommitChange = onCommitColorChange,
                onReset = onResetColorAdjustments
              )
            }

            ClipEditToolSubmode.TRANSITION -> {
              TransitionInspectorStrip(
                selectedClip = selectedClip,
                onSelectTransition = { type, dur ->
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onSelectTransition(type, dur)
                }
              )
            }

            ClipEditToolSubmode.TEXT_STYLE -> {
              selectedClip.textOverlay?.let { tx ->
                InlineTextStyleStrip(
                  textOverlay = tx,
                  onEditContentClick = onOpenEditTextDialog,
                  onUpdateStyle = onUpdateSelectedTextStyle
                )
              }
            }

            ClipEditToolSubmode.NONE -> Unit
          }
        }
      }

      AnimatedContent(
        targetState = activeTab,
        transitionSpec = {
          fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith
            fadeOut(spring(stiffness = Spring.StiffnessMedium))
        },
        label = "editorTabContent"
      ) { tab ->
        when (tab) {
          EditorBottomTab.EDIT -> {
            if (selectedClip != null) {
              ClipContextualToolbar(
                selectedClip = selectedClip,
                activeSubmode = activeSubmode,
                onSplit = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onSplitClip()
                },
                onToggleSubmode = { sub ->
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onToggleSubmode(sub)
                },
                onToggleKeyframes = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onToggleKeyframes()
                },
                onEditTextContent = onOpenEditTextDialog,
                onDuplicate = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onDuplicateClip()
                },
                onDelete = {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  onDeleteClip()
                }
              )
            } else {
              EmptyEditHintBar()
            }
          }

          EditorBottomTab.MEDIA -> {
            MediaDrawerPanel(
              assets = assets.filter { it.type != MediaType.AUDIO },
              isImporting = isImportingMedia,
              onPickMainMedia = onPickMainMedia,
              onPickOverlayMedia = onPickOverlayMedia,
              onReuseAsset = onReuseAsset
            )
          }

          EditorBottomTab.AUDIO -> {
            AudioTabDrawerPanel(
              audioClips = audioTrackClips,
              isOriginalAudioMuted = isOriginalAudioMuted,
              isImporting = isImportingMedia,
              onPickAudioFile = onPickAudioFile,
              onToggleOriginalAudioMute = onToggleOriginalAudioMute,
              onSelectAudioClip = onSelectClip
            )
          }

          EditorBottomTab.TEXT -> {
            TextTabDrawerPanel(
              textClips = textTrackClips,
              onAddTextLayer = onOpenAddTextDialog,
              onSelectTextClip = onSelectClip
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ClipContextualToolbar(
  selectedClip: TimelineClipUiModel,
  activeSubmode: ClipEditToolSubmode,
  onSplit: () -> Unit,
  onToggleSubmode: (ClipEditToolSubmode) -> Unit,
  onToggleKeyframes: () -> Unit,
  onEditTextContent: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(62.dp)
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // 1. Split (all track types)
    ClipActionItem(
      icon = Icons.Outlined.ContentCut,
      label = stringResource(R.string.clip_split),
      isActive = false,
      tint = NexaTextPrimary,
      testTag = "action_split",
      onClick = onSplit
    )

    // 2. Keyframe Toggle (◆) for Overlay and Text tracks
    if (selectedClip.trackType == TrackType.OVERLAY || selectedClip.trackType == TrackType.TEXT) {
      val kfActive = selectedClip.hasKeyframesEnabled || selectedClip.keyframes.isNotEmpty()
      Surface(
        onClick = onToggleKeyframes,
        color = if (kfActive) Color(0x28FFD166) else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        border = if (kfActive) BorderStroke(1.dp, Color(0xFFFFD166)) else null,
        modifier = Modifier
          .height(50.dp)
          .width(74.dp)
          .testTag("action_keyframe")
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          DiamondIconGlyph(
            tint = if (kfActive) Color(0xFFFFD166) else NexaTextPrimary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = stringResource(R.string.clip_keyframe),
            style = MaterialTheme.typography.labelSmall,
            color = if (kfActive) Color(0xFFFFD166) else NexaTextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
          )
        }
      }
    }

    // 3. Text Content & Style Controls (Text clips)
    if (selectedClip.trackType == TrackType.TEXT) {
      ClipActionItem(
        icon = Icons.Outlined.Edit,
        label = stringResource(R.string.clip_edit_text),
        isActive = false,
        tint = NexaTextPrimary,
        testTag = "action_edit_text",
        onClick = onEditTextContent
      )
      ClipActionItem(
        icon = Icons.Outlined.TextFields,
        label = stringResource(R.string.clip_text_style),
        isActive = activeSubmode == ClipEditToolSubmode.TEXT_STYLE,
        tint = if (activeSubmode == ClipEditToolSubmode.TEXT_STYLE) NexaCyan else NexaTextPrimary,
        testTag = "action_text_style",
        onClick = { onToggleSubmode(ClipEditToolSubmode.TEXT_STYLE) }
      )
    }

    // 4. Speed Control (Video, Video Overlay, Audio)
    if (selectedClip.trackType != TrackType.TEXT && selectedClip.mediaType != MediaType.IMAGE) {
      val isSpeedOpen = activeSubmode == ClipEditToolSubmode.SPEED
      ClipActionItem(
        icon = Icons.Outlined.Speed,
        label = if (abs(selectedClip.speed - 1.0f) > 0.01f) "${selectedClip.speed}x" else stringResource(R.string.clip_speed),
        isActive = isSpeedOpen || abs(selectedClip.speed - 1.0f) > 0.01f,
        tint = if (isSpeedOpen) NexaCyan else NexaTextPrimary,
        testTag = "action_speed",
        onClick = { onToggleSubmode(ClipEditToolSubmode.SPEED) }
      )
    }

    // 5. Volume Control (Audio & Main Video clips)
    if (selectedClip.trackType == TrackType.AUDIO ||
      (selectedClip.trackType == TrackType.VIDEO && selectedClip.mediaType == MediaType.VIDEO)
    ) {
      val isVolOpen = activeSubmode == ClipEditToolSubmode.VOLUME
      val pct = (selectedClip.volume * 100f).roundToInt()
      ClipActionItem(
        icon = Icons.AutoMirrored.Outlined.VolumeUp,
        label = if (pct != 100) "$pct%" else stringResource(R.string.clip_volume),
        isActive = isVolOpen || pct != 100,
        tint = if (isVolOpen) NexaCyan else NexaTextPrimary,
        testTag = "action_volume",
        onClick = { onToggleSubmode(ClipEditToolSubmode.VOLUME) }
      )
    }

    // 6. Crop / Rotate 90° / Flip & Color Adjustments (Video & Overlay clips)
    if (selectedClip.trackType == TrackType.VIDEO || selectedClip.trackType == TrackType.OVERLAY) {
      val isCropOpen = activeSubmode == ClipEditToolSubmode.CROP_TRANSFORM
      val hasTransform = selectedClip.hasCustomCrop ||
        selectedClip.rotationDegrees % 360 != 0 ||
        selectedClip.flipHorizontal ||
        selectedClip.flipVertical

      ClipActionItem(
        icon = Icons.Outlined.Crop,
        label = stringResource(R.string.clip_transform),
        isActive = isCropOpen || hasTransform,
        tint = if (isCropOpen) NexaCyan else NexaTextPrimary,
        testTag = "action_crop_transform",
        onClick = { onToggleSubmode(ClipEditToolSubmode.CROP_TRANSFORM) }
      )

      val isAdjustOpen = activeSubmode == ClipEditToolSubmode.COLOR_ADJUST
      ClipActionItem(
        icon = Icons.Outlined.Tune,
        label = stringResource(R.string.clip_adjust),
        isActive = isAdjustOpen || selectedClip.hasColorAdjustments,
        tint = if (isAdjustOpen) NexaCyan else NexaTextPrimary,
        testTag = "action_color_adjust",
        onClick = { onToggleSubmode(ClipEditToolSubmode.COLOR_ADJUST) }
      )
    }

    // 7. Transition Picker (Main Video clips after the first clip)
    if (selectedClip.trackType == TrackType.VIDEO && selectedClip.trackIndex == 0 && selectedClip.orderIndex > 0) {
      val isTransOpen = activeSubmode == ClipEditToolSubmode.TRANSITION
      ClipActionItem(
        icon = Icons.Rounded.Animation,
        label = if (selectedClip.transitionType != TransitionType.NONE) {
          selectedClip.transitionType.label
        } else {
          stringResource(R.string.clip_transition)
        },
        isActive = isTransOpen || selectedClip.transitionType != TransitionType.NONE,
        tint = if (isTransOpen) NexaCyan else NexaTextPrimary,
        testTag = "action_transition",
        onClick = { onToggleSubmode(ClipEditToolSubmode.TRANSITION) }
      )
    }

    // 8. Duplicate & Delete
    ClipActionItem(
      icon = Icons.Outlined.ContentCopy,
      label = stringResource(R.string.clip_duplicate),
      isActive = false,
      tint = NexaTextPrimary,
      testTag = "action_duplicate",
      onClick = onDuplicate
    )

    ClipActionItem(
      icon = Icons.Outlined.DeleteOutline,
      label = stringResource(R.string.clip_delete),
      isActive = false,
      tint = NexaRed,
      backgroundTint = NexaRedDim,
      testTag = "action_delete",
      onClick = onDelete
    )
  }
}

@Composable
private fun ClipActionItem(
  icon: ImageVector,
  label: String,
  isActive: Boolean,
  tint: Color,
  testTag: String,
  backgroundTint: Color = Color.Transparent,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    color = if (isActive) Color(0x2400F0FF) else backgroundTint,
    shape = RoundedCornerShape(10.dp),
    border = if (isActive) BorderStroke(1.dp, NexaCyan) else null,
    modifier = Modifier
      .height(50.dp)
      .width(74.dp)
      .testTag(testTag)
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(horizontal = 4.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier.size(19.dp)
      )
      Spacer(modifier = Modifier.height(3.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun SpeedPickerStrip(
  currentSpeed: Float,
  onSelectSpeed: (Float) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(NexaSurfaceElevated)
      .padding(horizontal = 16.dp, vertical = 10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Playback Speed",
        style = MaterialTheme.typography.labelLarge,
        color = NexaTextPrimary
      )
      Text(
        text = "Current: ${currentSpeed}x",
        style = MaterialTheme.typography.labelSmall,
        color = NexaCyan
      )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      SPEED_OPTIONS.forEach { speedOpt ->
        val isSelected = abs(currentSpeed - speedOpt) < 0.02f
        val label = if (speedOpt == 1.0f || speedOpt == 2.0f || speedOpt == 3.0f) {
          "${speedOpt.toInt()}x"
        } else {
          "${speedOpt}x"
        }
        Box(
          modifier = Modifier
            .weight(1f)
            .height(38.dp)
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
            .clickable { onSelectSpeed(speedOpt) }
            .testTag("speed_option_$label"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) NexaBlack else NexaTextPrimary,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyEditHintBar() {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(58.dp)
      .padding(horizontal = 20.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = stringResource(R.string.edit_empty_hint),
      style = MaterialTheme.typography.bodySmall,
      color = NexaTextSecondary,
      textAlign = TextAlign.Center,
      maxLines = 2
    )
  }
}

@Composable
private fun MediaDrawerPanel(
  assets: List<MediaAssetEntity>,
  isImporting: Boolean,
  onPickMainMedia: () -> Unit,
  onPickOverlayMedia: () -> Unit,
  onReuseAsset: (assetId: Long, asOverlay: Boolean) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(NexaAccentGradient)
          .clickable(enabled = !isImporting, onClick = onPickMainMedia)
          .testTag("add_main_media_button"),
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
            text = stringResource(R.string.add_main_track),
            style = MaterialTheme.typography.labelLarge,
            color = NexaBlack,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Surface(
        onClick = onPickOverlayMedia,
        enabled = !isImporting,
        color = NexaSurfaceElevated,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, NexaPurple),
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("add_overlay_media_button")
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.Outlined.Layers,
            contentDescription = null,
            tint = NexaCyan,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.add_overlay_track),
            style = MaterialTheme.typography.labelLarge,
            color = NexaTextPrimary,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    if (assets.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
      ) {
        items(items = assets, key = { it.id }) { asset ->
          ProjectAssetChip(
            asset = asset,
            onAddMain = { onReuseAsset(asset.id, false) },
            onAddOverlay = { onReuseAsset(asset.id, true) }
          )
        }
      }
    }
  }
}

@Composable
private fun ProjectAssetChip(
  asset: MediaAssetEntity,
  onAddMain: () -> Unit,
  onAddOverlay: () -> Unit
) {
  Surface(
    color = NexaSurfaceElevated,
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(1.dp, NexaBorderSubtle),
    modifier = Modifier.height(42.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(end = 6.dp)
    ) {
      Box(
        modifier = Modifier
          .size(42.dp)
          .background(NexaBlack)
      ) {
        if (!asset.thumbnailPath.isNullOrBlank() && File(asset.thumbnailPath).exists()) {
          AsyncImage(
            model = File(asset.thumbnailPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        }
      }
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = TimeFormatUtils.formatDurationBadge(asset.durationMs),
        style = MaterialTheme.typography.labelSmall,
        color = NexaTextSecondary
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = "+Main",
        style = MaterialTheme.typography.labelSmall,
        color = NexaCyan,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
          .clip(RoundedCornerShape(4.dp))
          .clickable(onClick = onAddMain)
          .padding(horizontal = 5.dp, vertical = 4.dp)
      )
      Text(
        text = "+PIP",
        style = MaterialTheme.typography.labelSmall,
        color = NexaPurple,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
          .clip(RoundedCornerShape(4.dp))
          .clickable(onClick = onAddOverlay)
          .padding(horizontal = 5.dp, vertical = 4.dp)
      )
    }
  }
}

@Composable
fun EditorBottomNavigationBar(
  activeTab: EditorBottomTab,
  onSelectTab: (EditorBottomTab) -> Unit,
  modifier: Modifier = Modifier
) {
  val haptic = LocalHapticFeedback.current

  val items = listOf(
    BottomTabSpec(
      tab = EditorBottomTab.MEDIA,
      labelRes = R.string.tab_media,
      outlinedIcon = Icons.Outlined.VideoLibrary,
      filledIcon = Icons.Filled.VideoLibrary,
      testTag = "tab_media"
    ),
    BottomTabSpec(
      tab = EditorBottomTab.AUDIO,
      labelRes = R.string.tab_audio,
      outlinedIcon = Icons.Outlined.AudioFile,
      filledIcon = Icons.Filled.AudioFile,
      testTag = "tab_audio"
    ),
    BottomTabSpec(
      tab = EditorBottomTab.TEXT,
      labelRes = R.string.tab_text,
      outlinedIcon = Icons.Outlined.TextFields,
      filledIcon = Icons.Filled.TextFields,
      testTag = "tab_text"
    ),
    BottomTabSpec(
      tab = EditorBottomTab.EDIT,
      labelRes = R.string.tab_edit,
      outlinedIcon = Icons.Outlined.AutoFixHigh,
      filledIcon = Icons.Filled.AutoFixHigh,
      testTag = "tab_edit"
    )
  )

  Surface(
    color = NexaBlack,
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      HorizontalDivider(color = NexaBorderSubtle)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .height(56.dp)
          .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        items.forEach { spec ->
          val isSelected = activeTab == spec.tab
          val indicatorFraction by animateFloatAsState(
            targetValue = if (isSelected) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "tabUnderline"
          )

          Column(
            modifier = Modifier
              .weight(1f)
              .height(52.dp)
              .clip(RoundedCornerShape(8.dp))
              .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectTab(spec.tab)
              }
              .testTag(spec.testTag),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = if (isSelected) spec.filledIcon else spec.outlinedIcon,
              contentDescription = stringResource(spec.labelRes),
              tint = if (isSelected) NexaCyan else NexaTextSecondary,
              modifier = Modifier.size(21.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = stringResource(spec.labelRes),
              style = MaterialTheme.typography.labelSmall,
              color = if (isSelected) NexaTextPrimary else NexaTextSecondary,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
              modifier = Modifier
                .width(24.dp * indicatorFraction)
                .height(2.5.dp)
                .clip(CircleShape)
                .background(NexaAccentGradient)
            )
          }
        }
      }
    }
  }
}

private data class BottomTabSpec(
  val tab: EditorBottomTab,
  val labelRes: Int,
  val outlinedIcon: ImageVector,
  val filledIcon: ImageVector,
  val testTag: String
)

@Composable
fun ExportSheetContent(
  projectName: String,
  aspectRatio: AspectRatio,
  totalDurationMs: Long,
  clipCount: Int,
  exportState: ExportState,
  onSelectResolution: (ExportResolution) -> Unit,
  onStartExport: (ExportResolution) -> Unit,
  onCancelOrClose: () -> Unit,
  onViewExportedVideo: (String) -> Unit,
  onShareExportedVideo: (String) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 20.dp, vertical = 10.dp)
  ) {
    when (exportState) {
      is ExportState.Configuring -> {
        Text(
          text = stringResource(R.string.export_title),
          style = MaterialTheme.typography.headlineMedium,
          color = NexaTextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "$projectName • ${aspectRatio.rawValue} • $clipCount ${if (clipCount == 1) "layer" else "layers"} • ${
            TimeFormatUtils.formatDurationBadge(totalDurationMs)
          }",
          style = MaterialTheme.typography.bodySmall,
          color = NexaTextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text(
          text = stringResource(R.string.export_resolution_label),
          style = MaterialTheme.typography.titleMedium,
          color = NexaTextPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          ExportResolution.entries.forEach { res ->
            val isSelected = exportState.selectedResolution == res
            val (w, h) = res.dimensionsFor(aspectRatio)
            val desc = if (res == ExportResolution.RES_720P) {
              stringResource(R.string.export_720p_desc)
            } else {
              stringResource(R.string.export_1080p_desc)
            }

            Surface(
              onClick = { onSelectResolution(res) },
              color = if (isSelected) Color(0x1F00F0FF) else NexaSurfaceElevated,
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(
                width = if (isSelected) 1.8.dp else 1.dp,
                brush = if (isSelected) NexaAccentGradient else Brush.horizontalGradient(listOf(NexaBorderSubtle, NexaBorderSubtle))
              ),
              modifier = Modifier
                .weight(1f)
                .testTag("export_res_${res.shortSidePx}")
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Text(
                  text = res.label,
                  style = MaterialTheme.typography.titleMedium,
                  color = if (isSelected) NexaCyan else NexaTextPrimary,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "$w × $h MP4",
                  style = MaterialTheme.typography.labelMedium,
                  color = NexaTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = desc,
                  style = MaterialTheme.typography.labelSmall,
                  color = NexaTextSecondary
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(NexaAccentGradient)
            .clickable { onStartExport(exportState.selectedResolution) }
            .testTag("confirm_export_button"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "${stringResource(R.string.export_start)} (${exportState.selectedResolution.label})",
            style = MaterialTheme.typography.titleMedium,
            color = NexaBlack,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(16.dp))
      }

      is ExportState.Exporting -> {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
              progress = { exportState.progressPercent / 100f },
              color = NexaCyan,
              trackColor = NexaSurfaceElevated,
              strokeWidth = 5.dp,
              modifier = Modifier.size(84.dp)
            )
            Text(
              text = "${exportState.progressPercent}%",
              style = MaterialTheme.typography.titleLarge,
              color = NexaTextPrimary,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.testTag("export_progress_text")
            )
          }
          Spacer(modifier = Modifier.height(18.dp))
          Text(
            text = stringResource(R.string.export_in_progress),
            style = MaterialTheme.typography.titleMedium,
            color = NexaTextPrimary
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Encoding H.264 ${exportState.resolution.label} to Movies/PlayNexa…",
            style = MaterialTheme.typography.bodySmall,
            color = NexaTextSecondary
          )
          Spacer(modifier = Modifier.height(16.dp))
          LinearProgressIndicator(
            progress = { exportState.progressPercent / 100f },
            color = NexaCyan,
            trackColor = NexaSurfaceElevated,
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(CircleShape)
          )
          Spacer(modifier = Modifier.height(20.dp))
          Surface(
            onClick = onCancelOrClose,
            color = NexaRedDim,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .height(48.dp)
              .fillMaxWidth()
              .testTag("cancel_export_button")
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = stringResource(R.string.action_cancel),
                style = MaterialTheme.typography.labelLarge,
                color = NexaRed,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }

      is ExportState.Success -> {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = NexaSuccess,
            modifier = Modifier.size(56.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = stringResource(R.string.export_complete),
            style = MaterialTheme.typography.headlineMedium,
            color = NexaTextPrimary
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = exportState.displayFileName,
            style = MaterialTheme.typography.bodySmall,
            color = NexaTextSecondary,
            textAlign = TextAlign.Center
          )

          Spacer(modifier = Modifier.height(22.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NexaAccentGradient)
                .clickable { onViewExportedVideo(exportState.outputUri) }
                .testTag("view_exported_video_button"),
              contentAlignment = Alignment.Center
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Outlined.PlayCircle,
                  contentDescription = null,
                  tint = NexaBlack,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = stringResource(R.string.export_view),
                  style = MaterialTheme.typography.labelLarge,
                  color = NexaBlack,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            Surface(
              onClick = { onShareExportedVideo(exportState.outputUri) },
              color = NexaSurfaceElevated,
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, NexaCyan),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("share_exported_video_button")
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
              ) {
                Icon(
                  imageVector = Icons.Outlined.Share,
                  contentDescription = null,
                  tint = NexaCyan,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = stringResource(R.string.export_share),
                  style = MaterialTheme.typography.labelLarge,
                  color = NexaTextPrimary,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
          Surface(
            onClick = onCancelOrClose,
            color = Color.Transparent,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = stringResource(R.string.export_done),
                style = MaterialTheme.typography.labelLarge,
                color = NexaTextSecondary
              )
            }
          }
        }
      }

      is ExportState.Error -> {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = NexaRed,
            modifier = Modifier.size(48.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "Export Failed",
            style = MaterialTheme.typography.titleLarge,
            color = NexaTextPrimary
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = exportState.message,
            style = MaterialTheme.typography.bodySmall,
            color = NexaTextSecondary,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(20.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Surface(
              onClick = onCancelOrClose,
              color = NexaSurfaceElevated,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = stringResource(R.string.action_cancel),
                  style = MaterialTheme.typography.labelLarge,
                  color = NexaTextSecondary
                )
              }
            }
            Box(
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NexaAccentGradient)
                .clickable { onStartExport(ExportResolution.RES_720P) },
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "Retry (720p)",
                style = MaterialTheme.typography.labelLarge,
                color = NexaBlack,
                fontWeight = FontWeight.Bold
              )
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
        }
      }

      ExportState.Idle -> Unit
    }
  }
}
