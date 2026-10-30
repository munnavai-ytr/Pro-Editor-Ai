package com.example.ui.editor.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.local.entity.KeyframeEntity
import com.example.data.local.entity.MediaType
import com.example.data.local.entity.TrackType
import com.example.data.local.entity.TransitionType
import com.example.data.media.FilmstripGenerator
import com.example.domain.model.NexaFontOption
import com.example.domain.model.TimeFormatUtils
import com.example.domain.model.TimelineClipUiModel
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.NexaAccentGradient
import com.example.ui.theme.NexaBlack
import com.example.ui.theme.NexaBorderSubtle
import com.example.ui.theme.NexaCyan
import com.example.ui.theme.NexaPurple
import com.example.ui.theme.NexaRed
import com.example.ui.theme.NexaSurface
import com.example.ui.theme.NexaSurfaceElevated
import com.example.ui.theme.NexaTextDisabled
import com.example.ui.theme.NexaTextPrimary
import com.example.ui.theme.NexaTextSecondary
import com.example.ui.theme.NexaTrackBg
import com.example.ui.theme.NexaTrackLane
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

private val BASE_DP_PER_SECOND = 68.dp
private val RULER_HEIGHT = 26.dp
private val TEXT_TRACK_HEIGHT = 40.dp
private val OVERLAY_TRACK_HEIGHT = 46.dp
private val MAIN_TRACK_HEIGHT = 58.dp
private val AUDIO_TRACK_HEIGHT = 44.dp
private val TRACK_HEADER_WIDTH = 62.dp

private val KeyframeGold = Color(0xFFFFD166)
private val AudioEmerald = Color(0xFF06D6A0)

@Composable
fun MultiTrackTimeline(
  mainTrackClips: List<TimelineClipUiModel>,
  overlayTracks: Map<Int, List<TimelineClipUiModel>>,
  textTrackClips: List<TimelineClipUiModel>,
  audioTrackClips: List<TimelineClipUiModel>,
  isOriginalAudioMuted: Boolean,
  totalDurationMs: Long,
  playheadMs: Long,
  isPlaying: Boolean,
  zoomScale: Float,
  selectedClipId: Long?,
  filmstripGenerator: FilmstripGenerator,
  onScrubTo: (Long) -> Unit,
  onPinchZoomDelta: (Float) -> Unit,
  onSelectClip: (Long?) -> Unit,
  onClickTransitionJunction: (incomingClipId: Long) -> Unit,
  onToggleOriginalAudioMute: () -> Unit,
  onLiveTrimUpdate: (clipId: Long, newTrimStartMs: Long, newTrimEndMs: Long, isLeft: Boolean, overlayStartMs: Long?) -> Unit,
  onCommitLiveTrim: () -> Unit,
  onReorderMainClip: (clipId: Long, targetOrderIndex: Int) -> Unit,
  onLiveNonMainMove: (clipId: Long, newStartMs: Long) -> Unit,
  onCommitNonMainMove: () -> Unit,
  onRequestAddText: () -> Unit,
  onRequestAddOverlay: (trackIndex: Int) -> Unit,
  onRequestAddMainMedia: () -> Unit,
  onRequestAddAudio: () -> Unit,
  modifier: Modifier = Modifier
) {
  val density = LocalDensity.current
  val haptic = LocalHapticFeedback.current
  val horizontalScrollState = rememberScrollState()
  val verticalTracksScrollState = rememberScrollState()

  val dpPerSecond: Dp = BASE_DP_PER_SECOND * zoomScale
  val pxPerSecond: Float = with(density) { dpPerSecond.toPx() }
  val pxPerMs: Float = (pxPerSecond / 1000f).coerceAtLeast(0.01f)

  val displayOverlayTrackIndices = remember(overlayTracks) {
    val keys = overlayTracks.keys.toMutableSet().apply { add(1) }
    keys.sortedDescending()
  }

  val snapPointsMs = remember(mainTrackClips, overlayTracks, textTrackClips, audioTrackClips, totalDurationMs) {
    buildSet {
      add(0L)
      add(totalDurationMs)
      (mainTrackClips + overlayTracks.values.flatten() + textTrackClips + audioTrackClips).forEach {
        add(it.startMs)
        add(it.endMs)
      }
    }
  }

  val latestZoomCallback by rememberUpdatedState(onPinchZoomDelta)

  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .background(NexaTrackBg)
      .pointerInput(Unit) {
        awaitEachGesture {
          do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount >= 2) {
              val zoomChange = event.calculateZoom()
              if (abs(zoomChange - 1f) > 0.004f) {
                latestZoomCallback(zoomChange)
                event.changes.forEach { it.consume() }
              }
            }
          } while (event.changes.any { it.pressed })
        }
      }
      .testTag("multi_track_timeline")
  ) {
    val availableWidthDp = (maxWidth - TRACK_HEADER_WIDTH).coerceAtLeast(200.dp)
    val availableWidthPx = with(density) { availableWidthDp.toPx() }

    val canvasDurationMs = maxOf(totalDurationMs + 4_000L, 12_000L)
    val timelineContentWidthDp = maxOf(
      with(density) { (canvasDurationMs * pxPerMs).toDp() } + 80.dp,
      availableWidthDp
    )

    LaunchedEffect(playheadMs, isPlaying, pxPerMs, availableWidthPx) {
      if (isPlaying) {
        val playheadX = playheadMs * pxPerMs
        val viewportStart = horizontalScrollState.value.toFloat()
        val viewportEnd = viewportStart + availableWidthPx
        if (playheadX > viewportEnd - availableWidthPx * 0.2f || playheadX < viewportStart) {
          val targetScroll = (playheadX - availableWidthPx * 0.25f).roundToInt().coerceAtLeast(0)
          horizontalScrollState.scrollTo(targetScroll)
        }
      }
    }

    Row(modifier = Modifier.fillMaxSize()) {
      // Left Fixed Track Header Gutter
      Column(
        modifier = Modifier
          .width(TRACK_HEADER_WIDTH)
          .fillMaxHeight()
          .background(NexaSurface)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT)
            .background(NexaSurfaceElevated),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = null,
            tint = NexaTextDisabled,
            modifier = Modifier.size(13.dp)
          )
        }

        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(verticalTracksScrollState)
            .padding(vertical = 6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // 1. Text Track Header
          TrackLaneHeader(
            icon = Icons.Outlined.TextFields,
            label = "Text",
            accentColor = KeyframeGold,
            height = TEXT_TRACK_HEIGHT,
            testTag = "header_text_track",
            onClick = onRequestAddText
          )

          // 2. Overlay Track Header(s)
          displayOverlayTrackIndices.forEach { trackIdx ->
            TrackLaneHeader(
              icon = Icons.Outlined.Layers,
              label = if (displayOverlayTrackIndices.size > 1) "PIP $trackIdx" else "Overlay",
              accentColor = NexaPurple,
              height = OVERLAY_TRACK_HEIGHT,
              testTag = "header_overlay_track_$trackIdx",
              onClick = { onRequestAddOverlay(trackIdx) }
            )
          }

          // 3. Base Video Track Header (with Original Audio Mute Quick Toggle)
          VideoTrackHeaderWithMute(
            isOriginalAudioMuted = isOriginalAudioMuted,
            height = MAIN_TRACK_HEIGHT,
            onToggleMute = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onToggleOriginalAudioMute()
            },
            onAddMainMedia = onRequestAddMainMedia
          )

          // 4. Audio Track Header (below Video Track)
          TrackLaneHeader(
            icon = Icons.Outlined.AudioFile,
            label = "Audio",
            accentColor = AudioEmerald,
            height = AUDIO_TRACK_HEIGHT,
            testTag = "header_audio_track",
            onClick = onRequestAddAudio
          )
        }
      }

      Box(
        modifier = Modifier
          .width(1.dp)
          .fillMaxHeight()
          .background(NexaBorderSubtle)
      )

      // Horizontally Scrollable Timeline Ruler + Stacked Tracks + Playhead
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .horizontalScroll(horizontalScrollState)
      ) {
        Box(
          modifier = Modifier
            .width(timelineContentWidthDp)
            .fillMaxHeight()
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null
            ) {
              onSelectClip(null)
            }
        ) {
          Column(modifier = Modifier.fillMaxSize()) {
            // Time Ruler
            TimelineRuler(
              canvasDurationMs = canvasDurationMs,
              totalDurationMs = totalDurationMs,
              pxPerMs = pxPerMs,
              zoomScale = zoomScale,
              snapPointsMs = snapPointsMs,
              onScrubTo = onScrubTo,
              modifier = Modifier
                .fillMaxWidth()
                .height(RULER_HEIGHT)
            )

            // Stacked Tracks Area: Text -> Overlay(s) -> Video -> Audio
            Column(
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalTracksScrollState)
                .padding(vertical = 6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              // 1. Text Track Lane
              NonMainTrackLane(
                laneTag = "text_track_lane",
                emptyTag = "empty_add_text_button",
                emptyLabel = "+ Add text caption",
                emptyAccent = KeyframeGold,
                trackHeight = TEXT_TRACK_HEIGHT,
                clips = textTrackClips,
                pxPerMs = pxPerMs,
                selectedClipId = selectedClipId,
                playheadMs = playheadMs,
                accentBorderBrush = Brush.horizontalGradient(listOf(KeyframeGold, NexaCyan)),
                filmstripGenerator = filmstripGenerator,
                onSelectClip = onSelectClip,
                onScrubTo = onScrubTo,
                onLiveTrimUpdate = onLiveTrimUpdate,
                onCommitLiveTrim = onCommitLiveTrim,
                onLiveMove = onLiveNonMainMove,
                onCommitMove = onCommitNonMainMove,
                onRequestAddEmpty = onRequestAddText
              )

              // 2. Overlay (PiP) Track Lane(s)
              displayOverlayTrackIndices.forEach { trackIdx ->
                NonMainTrackLane(
                  laneTag = "overlay_track_lane_$trackIdx",
                  emptyTag = "empty_add_overlay_button_$trackIdx",
                  emptyLabel = "+ Add PiP overlay",
                  emptyAccent = NexaPurple,
                  trackHeight = OVERLAY_TRACK_HEIGHT,
                  clips = overlayTracks[trackIdx].orEmpty(),
                  pxPerMs = pxPerMs,
                  selectedClipId = selectedClipId,
                  playheadMs = playheadMs,
                  accentBorderBrush = Brush.horizontalGradient(listOf(NexaPurple, NexaCyan)),
                  filmstripGenerator = filmstripGenerator,
                  onSelectClip = onSelectClip,
                  onScrubTo = onScrubTo,
                  onLiveTrimUpdate = onLiveTrimUpdate,
                  onCommitLiveTrim = onCommitLiveTrim,
                  onLiveMove = onLiveNonMainMove,
                  onCommitMove = onCommitNonMainMove,
                  onRequestAddEmpty = { onRequestAddOverlay(trackIdx) }
                )
              }

              // 3. Base Video Track Lane (with Transition Junction Badges!)
              MainVideoTrackLane(
                clips = mainTrackClips,
                isOriginalAudioMuted = isOriginalAudioMuted,
                pxPerMs = pxPerMs,
                selectedClipId = selectedClipId,
                filmstripGenerator = filmstripGenerator,
                onSelectClip = onSelectClip,
                onClickTransitionJunction = { incomingId ->
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onClickTransitionJunction(incomingId)
                },
                onLiveTrimUpdate = onLiveTrimUpdate,
                onCommitLiveTrim = onCommitLiveTrim,
                onReorderMainClip = onReorderMainClip,
                onRequestAddMainMedia = onRequestAddMainMedia
              )

              // 4. Dedicated Audio Track Lane (below Video Track)
              NonMainTrackLane(
                laneTag = "audio_track_lane",
                emptyTag = "empty_add_audio_button",
                emptyLabel = "+ Import music or audio track",
                emptyAccent = AudioEmerald,
                trackHeight = AUDIO_TRACK_HEIGHT,
                clips = audioTrackClips,
                pxPerMs = pxPerMs,
                selectedClipId = selectedClipId,
                playheadMs = playheadMs,
                accentBorderBrush = Brush.horizontalGradient(listOf(AudioEmerald, NexaCyan)),
                filmstripGenerator = filmstripGenerator,
                onSelectClip = onSelectClip,
                onScrubTo = onScrubTo,
                onLiveTrimUpdate = onLiveTrimUpdate,
                onCommitLiveTrim = onCommitLiveTrim,
                onLiveMove = onLiveNonMainMove,
                onCommitMove = onCommitNonMainMove,
                onRequestAddEmpty = onRequestAddAudio
              )
            }
          }

          // Draggable & Auto-Advancing Playhead Line Overlay
          PlayheadOverlay(
            playheadMs = playheadMs,
            totalDurationMs = totalDurationMs,
            pxPerMs = pxPerMs,
            snapPointsMs = snapPointsMs,
            onScrubTo = onScrubTo,
            onSnapFeedback = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
          )
        }
      }
    }
  }
}

@Composable
private fun TrackLaneHeader(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  accentColor: Color,
  height: Dp,
  testTag: String,
  onClick: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(height)
      .clickable(onClick = onClick)
      .padding(horizontal = 4.dp)
      .testTag(testTag),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = accentColor,
      modifier = Modifier.size(15.dp)
    )
    Spacer(modifier = Modifier.height(1.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = NexaTextSecondary,
      fontSize = 10.sp,
      maxLines = 1
    )
  }
}

@Composable
private fun VideoTrackHeaderWithMute(
  isOriginalAudioMuted: Boolean,
  height: Dp,
  onToggleMute: () -> Unit,
  onAddMainMedia: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(height)
      .padding(horizontal = 3.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(4.dp))
          .clickable(onClick = onAddMainMedia)
          .padding(3.dp),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Outlined.Movie,
          contentDescription = "Add video clip",
          tint = NexaCyan,
          modifier = Modifier.size(15.dp)
        )
      }

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(4.dp))
          .background(if (isOriginalAudioMuted) Color(0x33FF4D4D) else Color.Transparent)
          .clickable(onClick = onToggleMute)
          .padding(3.dp)
          .testTag("timeline_mute_original_audio_button"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (isOriginalAudioMuted) {
            Icons.AutoMirrored.Outlined.VolumeOff
          } else {
            Icons.AutoMirrored.Outlined.VolumeUp
          },
          contentDescription = if (isOriginalAudioMuted) "Unmute original video audio" else "Mute original video audio",
          tint = if (isOriginalAudioMuted) NexaRed else NexaTextSecondary,
          modifier = Modifier.size(15.dp)
        )
      }
    }
    Text(
      text = if (isOriginalAudioMuted) "Muted" else "Video",
      style = MaterialTheme.typography.labelSmall,
      color = if (isOriginalAudioMuted) NexaRed else NexaTextSecondary,
      fontSize = 10.sp,
      maxLines = 1
    )
  }
}

@Composable
private fun TimelineRuler(
  canvasDurationMs: Long,
  totalDurationMs: Long,
  pxPerMs: Float,
  zoomScale: Float,
  snapPointsMs: Set<Long>,
  onScrubTo: (Long) -> Unit,
  modifier: Modifier = Modifier
) {
  val haptic = LocalHapticFeedback.current
  val textMeasurer = rememberTextMeasurer()
  val latestPxPerMs by rememberUpdatedState(pxPerMs)
  val latestTotalDuration by rememberUpdatedState(totalDurationMs)
  val latestSnapPoints by rememberUpdatedState(snapPointsMs)
  val latestOnScrub by rememberUpdatedState(onScrubTo)

  var lastSnappedMs by remember { mutableLongStateOf(-1L) }

  fun resolveScrubPosition(xPx: Float): Long {
    val rawMs = (xPx / latestPxPerMs).toLong().coerceIn(0L, latestTotalDuration.coerceAtLeast(0L))
    val snapThresholdMs = (14f / latestPxPerMs).toLong().coerceIn(40L, 180L)
    val nearestSnap = latestSnapPoints.minByOrNull { abs(it - rawMs) }
    return if (nearestSnap != null && abs(nearestSnap - rawMs) <= snapThresholdMs) {
      if (lastSnappedMs != nearestSnap) {
        lastSnappedMs = nearestSnap
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      }
      nearestSnap.coerceIn(0L, latestTotalDuration.coerceAtLeast(0L))
    } else {
      lastSnappedMs = -1L
      rawMs
    }
  }

  val majorStepMs = when {
    zoomScale >= 2.0f -> 500L
    zoomScale >= 0.9f -> 1_000L
    zoomScale >= 0.55f -> 2_000L
    else -> 5_000L
  }
  val minorStepMs = (majorStepMs / 5L).coerceAtLeast(100L)
  val showHalfSec = majorStepMs == 500L

  val labelStyle = remember {
    TextStyle(
      fontFamily = InterFontFamily,
      fontSize = 10.sp,
      fontWeight = FontWeight.Medium,
      color = NexaTextSecondary
    )
  }

  Canvas(
    modifier = modifier
      .background(NexaSurfaceElevated)
      .pointerInput(Unit) {
        detectTapGestures { offset ->
          latestOnScrub(resolveScrubPosition(offset.x))
        }
      }
      .pointerInput(Unit) {
        detectHorizontalDragGestures { change, _ ->
          change.consume()
          latestOnScrub(resolveScrubPosition(change.position.x))
        }
      }
      .testTag("timeline_ruler")
  ) {
    val heightPx = size.height
    var t = 0L
    while (t <= canvasDurationMs) {
      val x = t * pxPerMs
      if (x <= size.width) {
        val isMajor = (t % majorStepMs == 0L)
        if (isMajor) {
          drawLine(
            color = NexaTextDisabled,
            start = Offset(x, heightPx - 8.dp.toPx()),
            end = Offset(x, heightPx),
            strokeWidth = 1.2.dp.toPx()
          )
          val text = TimeFormatUtils.formatRulerMarker(t, showHalfSec)
          val measured = textMeasurer.measure(text = text, style = labelStyle)
          if (x + measured.size.width + 4f < size.width) {
            drawText(
              textLayoutResult = measured,
              topLeft = Offset(x + 3.dp.toPx(), 3.dp.toPx())
            )
          }
        } else {
          drawLine(
            color = NexaBorderSubtle,
            start = Offset(x, heightPx - 4.dp.toPx()),
            end = Offset(x, heightPx),
            strokeWidth = 1.dp.toPx()
          )
        }
      }
      t += minorStepMs
    }

    val activeEndX = (totalDurationMs * pxPerMs).coerceAtMost(size.width)
    if (activeEndX > 0f) {
      drawLine(
        brush = Brush.horizontalGradient(listOf(NexaCyan, NexaPurple)),
        start = Offset(0f, heightPx - 1.dp.toPx()),
        end = Offset(activeEndX, heightPx - 1.dp.toPx()),
        strokeWidth = 2.dp.toPx()
      )
    }
  }
}

/**
 * Shared lane for free-positioned tracks: Text Track, Overlay (PiP) Track(s), and Audio Track.
 */
@Composable
private fun NonMainTrackLane(
  laneTag: String,
  emptyTag: String,
  emptyLabel: String,
  emptyAccent: Color,
  trackHeight: Dp,
  clips: List<TimelineClipUiModel>,
  pxPerMs: Float,
  selectedClipId: Long?,
  playheadMs: Long,
  accentBorderBrush: Brush,
  filmstripGenerator: FilmstripGenerator,
  onSelectClip: (Long?) -> Unit,
  onScrubTo: (Long) -> Unit,
  onLiveTrimUpdate: (clipId: Long, newTrimStartMs: Long, newTrimEndMs: Long, isLeft: Boolean, overlayStartMs: Long?) -> Unit,
  onCommitLiveTrim: () -> Unit,
  onLiveMove: (clipId: Long, newStartMs: Long) -> Unit,
  onCommitMove: () -> Unit,
  onRequestAddEmpty: () -> Unit
) {
  val density = LocalDensity.current
  val haptic = LocalHapticFeedback.current
  val latestPxPerMs by rememberUpdatedState(pxPerMs)
  val latestPlayheadMs by rememberUpdatedState(playheadMs)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(trackHeight)
      .padding(horizontal = 2.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(NexaTrackLane.copy(alpha = 0.65f))
      .testTag(laneTag)
  ) {
    if (clips.isEmpty()) {
      Row(
        modifier = Modifier
          .align(Alignment.CenterStart)
          .padding(start = 10.dp)
          .clip(RoundedCornerShape(6.dp))
          .clickable(onClick = onRequestAddEmpty)
          .padding(horizontal = 8.dp, vertical = 5.dp)
          .testTag(emptyTag),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = emptyLabel,
          style = MaterialTheme.typography.labelSmall,
          color = emptyAccent.copy(alpha = 0.85f)
        )
      }
    } else {
      clips.forEach { clip ->
        val isSelected = clip.id == selectedClipId
        var isDraggingMove by remember(clip.id) { mutableStateOf(false) }
        var moveInitialStartMs by remember(clip.id) { mutableLongStateOf(0L) }
        var moveAccumPx by remember(clip.id) { mutableFloatStateOf(0f) }

        val latestClip by rememberUpdatedState(clip)
        val xOffsetDp = with(density) { (clip.startMs * pxPerMs).toDp() }
        val widthDp = with(density) { (clip.timelineDurationMs * pxPerMs).toDp() }.coerceAtLeast(24.dp)

        TimelineClipBox(
          clip = clip,
          widthDp = widthDp,
          heightDp = trackHeight,
          pxPerMs = pxPerMs,
          isSelected = isSelected,
          isBeingDragged = isDraggingMove,
          accentBorderBrush = accentBorderBrush,
          filmstripGenerator = filmstripGenerator,
          onSelect = { onSelectClip(clip.id) },
          onTapKeyframe = { kfTimeMs -> onScrubTo(clip.startMs + kfTimeMs) },
          onLiveTrimUpdate = onLiveTrimUpdate,
          onCommitLiveTrim = onCommitLiveTrim,
          modifier = Modifier
            .offset(x = xOffsetDp)
            .zIndex(if (isSelected || isDraggingMove) 6f else 1f)
            .pointerInput(clip.id) {
              detectDragGesturesAfterLongPress(
                onDragStart = {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  onSelectClip(latestClip.id)
                  isDraggingMove = true
                  moveInitialStartMs = latestClip.startMs
                  moveAccumPx = 0f
                },
                onDragEnd = {
                  isDraggingMove = false
                  onCommitMove()
                },
                onDragCancel = {
                  isDraggingMove = false
                  onCommitMove()
                },
                onDrag = { change, dragAmount ->
                  change.consume()
                  moveAccumPx += dragAmount.x
                  val rawTargetMs = (moveInitialStartMs + (moveAccumPx / latestPxPerMs).toLong())
                    .coerceAtLeast(0L)
                  val snapThresholdMs = (12f / latestPxPerMs).toLong().coerceIn(40L, 160L)
                  val snappedMs = when {
                    abs(rawTargetMs - latestPlayheadMs) <= snapThresholdMs -> latestPlayheadMs
                    rawTargetMs <= snapThresholdMs -> 0L
                    else -> rawTargetMs
                  }
                  onLiveMove(latestClip.id, snappedMs)
                }
              )
            }
        )
      }
    }
  }
}

@Composable
private fun MainVideoTrackLane(
  clips: List<TimelineClipUiModel>,
  isOriginalAudioMuted: Boolean,
  pxPerMs: Float,
  selectedClipId: Long?,
  filmstripGenerator: FilmstripGenerator,
  onSelectClip: (Long?) -> Unit,
  onClickTransitionJunction: (incomingClipId: Long) -> Unit,
  onLiveTrimUpdate: (clipId: Long, newTrimStartMs: Long, newTrimEndMs: Long, isLeft: Boolean, overlayStartMs: Long?) -> Unit,
  onCommitLiveTrim: () -> Unit,
  onReorderMainClip: (clipId: Long, targetOrderIndex: Int) -> Unit,
  onRequestAddMainMedia: () -> Unit
) {
  val density = LocalDensity.current
  val haptic = LocalHapticFeedback.current
  val latestClips by rememberUpdatedState(clips)
  val latestPxPerMs by rememberUpdatedState(pxPerMs)

  var draggingClipId by remember { mutableStateOf<Long?>(null) }
  var dragOffsetPx by remember { mutableFloatStateOf(0f) }
  var hoverTargetOrderIndex by remember { mutableIntStateOf(-1) }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(MAIN_TRACK_HEIGHT)
      .padding(horizontal = 2.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(NexaTrackLane)
      .testTag("main_video_track_lane")
  ) {
    if (clips.isEmpty()) {
      Row(
        modifier = Modifier
          .align(Alignment.CenterStart)
          .padding(start = 12.dp)
          .clip(RoundedCornerShape(6.dp))
          .clickable(onClick = onRequestAddMainMedia)
          .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Rounded.Add,
          contentDescription = null,
          tint = NexaCyan,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Tap to add video or photo clips",
          style = MaterialTheme.typography.labelMedium,
          color = NexaTextSecondary
        )
      }
    } else {
      // 1. Render Base Video Clips
      clips.forEachIndexed { index, clip ->
        val isSelected = clip.id == selectedClipId
        val isBeingReordered = draggingClipId == clip.id
        val baseXOffsetPx = clip.startMs * pxPerMs
        val visualXOffsetPx = if (isBeingReordered) baseXOffsetPx + dragOffsetPx else baseXOffsetPx
        val widthDp = with(density) { (clip.timelineDurationMs * pxPerMs).toDp() }.coerceAtLeast(28.dp)

        TimelineClipBox(
          clip = if (isOriginalAudioMuted) clip.copy(volume = 0f) else clip,
          widthDp = widthDp,
          heightDp = MAIN_TRACK_HEIGHT,
          pxPerMs = pxPerMs,
          isSelected = isSelected,
          isBeingDragged = isBeingReordered,
          accentBorderBrush = NexaAccentGradient,
          filmstripGenerator = filmstripGenerator,
          onSelect = { onSelectClip(clip.id) },
          onTapKeyframe = {},
          onLiveTrimUpdate = onLiveTrimUpdate,
          onCommitLiveTrim = onCommitLiveTrim,
          modifier = Modifier
            .offset { IntOffset(visualXOffsetPx.roundToInt(), 0) }
            .zIndex(if (isBeingReordered) 12f else if (isSelected) 4f else 1f)
            .pointerInput(clip.id) {
              detectDragGesturesAfterLongPress(
                onDragStart = {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  onSelectClip(clip.id)
                  draggingClipId = clip.id
                  dragOffsetPx = 0f
                  hoverTargetOrderIndex = index
                },
                onDragEnd = {
                  val targetOrder = hoverTargetOrderIndex
                  val movedId = draggingClipId
                  draggingClipId = null
                  dragOffsetPx = 0f
                  if (movedId != null && targetOrder >= 0) {
                    onReorderMainClip(movedId, targetOrder)
                  }
                },
                onDragCancel = {
                  draggingClipId = null
                  dragOffsetPx = 0f
                },
                onDrag = { change, dragAmount ->
                  change.consume()
                  dragOffsetPx += dragAmount.x
                  val snapshot = latestClips
                  val currentClip = snapshot.firstOrNull { it.id == clip.id }
                    ?: return@detectDragGesturesAfterLongPress
                  val draggedCenterMs = currentClip.startMs +
                    (currentClip.timelineDurationMs / 2L) +
                    (dragOffsetPx / latestPxPerMs).toLong()

                  var computedTarget = snapshot.lastIndex
                  for (i in snapshot.indices) {
                    val c = snapshot[i]
                    val mid = c.startMs + c.timelineDurationMs / 2L
                    if (draggedCenterMs < mid) {
                      computedTarget = i
                      break
                    }
                  }
                  if (computedTarget != hoverTargetOrderIndex) {
                    hoverTargetOrderIndex = computedTarget
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  }
                }
              )
            }
        )
      }

      // 2. Render Clickable Transition Junction Badges between adjacent clips!
      if (draggingClipId == null && clips.size >= 2) {
        val halfHitPx = with(density) { 18.dp.toPx() }
        for (i in 1 until clips.size) {
          val incomingClip = clips[i]
          val junctionXpx = incomingClip.startMs * pxPerMs
          val hasActiveTransition = incomingClip.transitionType != TransitionType.NONE

          Box(
            modifier = Modifier
              .offset { IntOffset((junctionXpx - halfHitPx).roundToInt(), 0) }
              .width(36.dp)
              .fillMaxHeight()
              .zIndex(9f)
              .clickable(
                interactionSource = remember(incomingClip.id) { MutableInteractionSource() },
                indication = null
              ) {
                onClickTransitionJunction(incomingClip.id)
              }
              .testTag("transition_junction_${incomingClip.id}"),
            contentAlignment = Alignment.Center
          ) {
            Box(
              modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                  if (hasActiveTransition) NexaAccentGradient
                  else Brush.horizontalGradient(listOf(NexaSurfaceElevated, NexaSurfaceElevated))
                )
                .border(
                  1.dp,
                  if (hasActiveTransition) NexaTextPrimary else NexaBorderSubtle,
                  RoundedCornerShape(6.dp)
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (hasActiveTransition) Icons.Rounded.Animation else Icons.Rounded.SwapHoriz,
                contentDescription = "Clip transition: ${incomingClip.transitionType.label}",
                tint = if (hasActiveTransition) NexaBlack else NexaTextSecondary,
                modifier = Modifier.size(13.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TimelineClipBox(
  clip: TimelineClipUiModel,
  widthDp: Dp,
  heightDp: Dp,
  pxPerMs: Float,
  isSelected: Boolean,
  isBeingDragged: Boolean,
  accentBorderBrush: Brush,
  filmstripGenerator: FilmstripGenerator,
  onSelect: () -> Unit,
  onTapKeyframe: (localTimeMs: Long) -> Unit,
  onLiveTrimUpdate: (clipId: Long, newTrimStartMs: Long, newTrimEndMs: Long, isLeft: Boolean, overlayStartMs: Long?) -> Unit,
  onCommitLiveTrim: () -> Unit,
  modifier: Modifier = Modifier
) {
  val haptic = LocalHapticFeedback.current
  val density = LocalDensity.current
  val latestClip by rememberUpdatedState(clip)
  val scale by animateFloatAsState(
    targetValue = if (isBeingDragged) 1.04f else 1f,
    animationSpec = spring(),
    label = "clipDragScale"
  )

  val currentPxPerMs = with(density) {
    (widthDp.toPx() / clip.timelineDurationMs.coerceAtLeast(1L).toFloat()).coerceAtLeast(0.01f)
  }
  val latestPxPerMs by rememberUpdatedState(currentPxPerMs)

  Box(
    modifier = modifier
      .width(widthDp)
      .height(heightDp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .then(if (isBeingDragged) Modifier.shadow(10.dp, RoundedCornerShape(8.dp)) else Modifier)
      .testTag("timeline_clip_${clip.id}")
  ) {
    // Visual Body based on TrackType
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 1.dp, vertical = 2.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(
          when (clip.trackType) {
            TrackType.TEXT -> Color(0xFF282012)
            TrackType.AUDIO -> Color(0xFF0E2622)
            else -> NexaSurfaceElevated
          }
        )
        .border(
          width = if (isSelected) 2.2.dp else 1.dp,
          brush = if (isSelected) accentBorderBrush else Brush.horizontalGradient(listOf(NexaBorderSubtle, NexaBorderSubtle)),
          shape = RoundedCornerShape(8.dp)
        )
        .clickable(onClick = onSelect)
    ) {
      when (clip.trackType) {
        TrackType.TEXT -> {
          TextClipTimelineBody(clip = clip)
        }
        TrackType.AUDIO -> {
          AudioClipTimelineBody(clip = clip)
        }
        else -> {
          ClipFilmstripRow(
            clip = clip,
            widthDp = widthDp,
            filmstripGenerator = filmstripGenerator,
            modifier = Modifier.fillMaxSize()
          )

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(20.dp)
              .align(Alignment.BottomCenter)
              .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0A0A0A))))
          )

          Row(
            modifier = Modifier
              .align(Alignment.BottomStart)
              .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = String.format(Locale.US, "%.1fs", clip.timelineDurationMs / 1000f),
              style = MaterialTheme.typography.labelSmall,
              color = NexaTextPrimary,
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1
            )
            if (abs(clip.speed - 1.0f) > 0.01f) {
              Surface(color = Color(0xCC7C3AED), shape = RoundedCornerShape(4.dp)) {
                Text(
                  text = "${clip.speed}x",
                  style = MaterialTheme.typography.labelSmall,
                  color = NexaTextPrimary,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                )
              }
            }
          }
        }
      }

      // Render Keyframe Diamond Markers (◆) along the clip bar for Overlay & Text tracks!
      if (clip.keyframes.isNotEmpty() &&
        (clip.trackType == TrackType.OVERLAY || clip.trackType == TrackType.TEXT)
      ) {
        KeyframeDiamondsRow(
          keyframes = clip.keyframes,
          clipDurationMs = clip.timelineDurationMs,
          pxPerMs = pxPerMs,
          onTapKeyframe = onTapKeyframe
        )
      }
    }

    // Left & Right Trim Handles when Selected
    if (isSelected) {
      val isNonMainTrack = !(clip.trackType == TrackType.VIDEO && clip.trackIndex == 0)

      var leftInitialTrimStart by remember(clip.id) { mutableLongStateOf(0L) }
      var leftInitialTrimEnd by remember(clip.id) { mutableLongStateOf(0L) }
      var leftInitialOverlayStart by remember(clip.id) { mutableLongStateOf(0L) }
      var leftDragAccumPx by remember(clip.id) { mutableFloatStateOf(0f) }
      var leftLastTickBucket by remember(clip.id) { mutableLongStateOf(-1L) }

      Box(
        modifier = Modifier
          .align(Alignment.CenterStart)
          .width(22.dp)
          .fillMaxHeight()
          .zIndex(8f)
          .pointerInput(clip.id) {
            detectHorizontalDragGestures(
              onDragStart = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                leftInitialTrimStart = latestClip.trimStartMs
                leftInitialTrimEnd = latestClip.trimEndMs
                leftInitialOverlayStart = latestClip.startMs
                leftDragAccumPx = 0f
                leftLastTickBucket = leftInitialTrimStart / 250L
              },
              onDragEnd = { onCommitLiveTrim() },
              onDragCancel = { onCommitLiveTrim() },
              onHorizontalDrag = { change, dragAmount ->
                change.consume()
                leftDragAccumPx += dragAmount
                val timelineDeltaMs = (leftDragAccumPx / latestPxPerMs).toLong()
                val sourceDeltaMs = (timelineDeltaMs * latestClip.speed).toLong()
                val candidateStart = (leftInitialTrimStart + sourceDeltaMs)
                  .coerceIn(0L, (leftInitialTrimEnd - 200L).coerceAtLeast(0L))

                val bucket = candidateStart / 250L
                if (bucket != leftLastTickBucket) {
                  leftLastTickBucket = bucket
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }

                val newNonMainStart = if (isNonMainTrack) {
                  val actualTimelineShift = ((candidateStart - leftInitialTrimStart) / latestClip.speed).toLong()
                  (leftInitialOverlayStart + actualTimelineShift).coerceAtLeast(0L)
                } else {
                  null
                }

                onLiveTrimUpdate(
                  latestClip.id,
                  candidateStart,
                  leftInitialTrimEnd,
                  true,
                  newNonMainStart
                )
              }
            )
          }
          .testTag("trim_left_${clip.id}"),
        contentAlignment = Alignment.CenterStart
      ) {
        TrimEdgeHandleVisual(isLeft = true)
      }

      var rightInitialTrimStart by remember(clip.id) { mutableLongStateOf(0L) }
      var rightInitialTrimEnd by remember(clip.id) { mutableLongStateOf(0L) }
      var rightDragAccumPx by remember(clip.id) { mutableFloatStateOf(0f) }
      var rightLastTickBucket by remember(clip.id) { mutableLongStateOf(-1L) }

      Box(
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .width(22.dp)
          .fillMaxHeight()
          .zIndex(8f)
          .pointerInput(clip.id) {
            detectHorizontalDragGestures(
              onDragStart = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                rightInitialTrimStart = latestClip.trimStartMs
                rightInitialTrimEnd = latestClip.trimEndMs
                rightDragAccumPx = 0f
                rightLastTickBucket = rightInitialTrimEnd / 250L
              },
              onDragEnd = { onCommitLiveTrim() },
              onDragCancel = { onCommitLiveTrim() },
              onHorizontalDrag = { change, dragAmount ->
                change.consume()
                rightDragAccumPx += dragAmount
                val timelineDeltaMs = (rightDragAccumPx / latestPxPerMs).toLong()
                val sourceDeltaMs = (timelineDeltaMs * latestClip.speed).toLong()
                val candidateEnd = (rightInitialTrimEnd + sourceDeltaMs)
                  .coerceIn(
                    rightInitialTrimStart + 200L,
                    latestClip.sourceDurationMs.coerceAtLeast(200L)
                  )

                val bucket = candidateEnd / 250L
                if (bucket != rightLastTickBucket) {
                  rightLastTickBucket = bucket
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }

                onLiveTrimUpdate(
                  latestClip.id,
                  rightInitialTrimStart,
                  candidateEnd,
                  false,
                  if (isNonMainTrack) latestClip.startMs else null
                )
              }
            )
          }
          .testTag("trim_right_${clip.id}"),
        contentAlignment = Alignment.CenterEnd
      ) {
        TrimEdgeHandleVisual(isLeft = false)
      }
    }
  }
}

@Composable
private fun TextClipTimelineBody(clip: TimelineClipUiModel) {
  val tx = clip.textOverlay
  val fontOpt = NexaFontOption.fromName(tx?.fontFamily ?: "Inter")
  Row(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    Icon(
      imageVector = Icons.Outlined.TextFields,
      contentDescription = null,
      tint = KeyframeGold,
      modifier = Modifier.size(13.dp)
    )
    Text(
      text = tx?.content ?: "Text",
      fontFamily = fontOpt.composeFontFamily,
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
      color = NexaTextPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun AudioClipTimelineBody(clip: TimelineClipUiModel) {
  Box(modifier = Modifier.fillMaxSize()) {
    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp)) {
      val barWidth = 3.dp.toPx()
      val gap = 2.5.dp.toPx()
      val step = (barWidth + gap).coerceAtLeast(2f)
      val count = (size.width / step).toInt().coerceAtLeast(1)
      val centerY = size.height / 2f
      val volFactor = (clip.volume.coerceIn(0f, 2f) * 0.65f + 0.2f).coerceIn(0.15f, 1f)

      for (i in 0 until count) {
        val sampleSeed = ((clip.trimStartMs / 60L) + i * 7L + clip.id * 13L).toFloat()
        val wave = (abs(sin(sampleSeed * 0.43f)) * 0.65f + abs(sin(sampleSeed * 1.17f)) * 0.35f)
          .coerceIn(0.18f, 1f)
        val halfH = (size.height * 0.45f * wave * volFactor).coerceAtLeast(2f)
        val x = i * step + barWidth / 2f
        drawLine(
          color = AudioEmerald.copy(alpha = 0.78f),
          start = Offset(x, centerY - halfH),
          end = Offset(x, centerY + halfH),
          strokeWidth = barWidth
        )
      }
    }

    Surface(
      color = Color(0xBB0A0A0A),
      shape = RoundedCornerShape(4.dp),
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
      Text(
        text = "${(clip.volume * 100f).roundToInt()}%",
        style = MaterialTheme.typography.labelSmall,
        color = AudioEmerald,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
      )
    }
  }
}

@Composable
private fun KeyframeDiamondsRow(
  keyframes: List<KeyframeEntity>,
  clipDurationMs: Long,
  pxPerMs: Float,
  onTapKeyframe: (localTimeMs: Long) -> Unit
) {
  val density = LocalDensity.current
  val halfSizePx = with(density) { 8.dp.toPx() }

  Box(modifier = Modifier.fillMaxSize()) {
    keyframes.forEach { kf ->
      if (kf.timeMs in 0L..clipDurationMs) {
        val xPx = (kf.timeMs * pxPerMs) - halfSizePx
        Box(
          modifier = Modifier
            .offset { IntOffset(xPx.roundToInt(), 2) }
            .size(16.dp)
            .clickable { onTapKeyframe(kf.timeMs) }
            .testTag("keyframe_marker_${kf.id}"),
          contentAlignment = Alignment.Center
        ) {
          Canvas(modifier = Modifier.size(9.dp)) {
            val w = size.width
            val h = size.height
            val diamond = Path().apply {
              moveTo(w / 2f, 0f)
              lineTo(w, h / 2f)
              lineTo(w / 2f, h)
              lineTo(0f, h / 2f)
              close()
            }
            drawPath(diamond, color = KeyframeGold)
          }
        }
      }
    }
  }
}

@Composable
private fun TrimEdgeHandleVisual(isLeft: Boolean) {
  val shape = if (isLeft) {
    RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 3.dp, bottomEnd = 3.dp)
  } else {
    RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp)
  }

  Box(
    modifier = Modifier
      .width(11.dp)
      .fillMaxHeight()
      .padding(vertical = 2.dp)
      .clip(shape)
      .background(NexaCyan),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .width(2.dp)
        .height(14.dp)
        .clip(CircleShape)
        .background(NexaBlack)
    )
  }
}

@Composable
private fun ClipFilmstripRow(
  clip: TimelineClipUiModel,
  widthDp: Dp,
  filmstripGenerator: FilmstripGenerator,
  modifier: Modifier = Modifier
) {
  val assetId = clip.mediaAssetId ?: return
  val tileCount = ceil(widthDp.value / 44f).toInt().coerceIn(1, 24)
  val trimmedRangeMs = clip.trimmedSourceDurationMs

  Row(modifier = modifier) {
    for (tileIdx in 0 until tileCount) {
      val sampleTimeMs = clip.trimStartMs +
        (((tileIdx + 0.5f) / tileCount.toFloat()) * trimmedRangeMs).toLong()

      FilmstripTile(
        assetId = assetId,
        sourceUri = clip.sourceUri,
        mediaType = clip.mediaType,
        sampleTimeMs = sampleTimeMs,
        filmstripGenerator = filmstripGenerator,
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
      )
    }
  }
}

@Composable
private fun FilmstripTile(
  assetId: Long,
  sourceUri: String,
  mediaType: MediaType,
  sampleTimeMs: Long,
  filmstripGenerator: FilmstripGenerator,
  modifier: Modifier = Modifier
) {
  val bucketTimeMs = if (mediaType == MediaType.IMAGE) {
    0L
  } else {
    (sampleTimeMs / 250L) * 250L
  }

  val frameBitmap by produceState<Bitmap?>(
    initialValue = filmstripGenerator.getCachedFrameImmediate(assetId, mediaType, bucketTimeMs),
    key1 = assetId,
    key2 = bucketTimeMs,
    key3 = sourceUri
  ) {
    if (value == null || value?.isRecycled == true) {
      value = filmstripGenerator.loadFrame(
        assetId = assetId,
        sourceUri = sourceUri,
        mediaType = mediaType,
        timeMs = bucketTimeMs
      )
    }
  }

  val bmp = frameBitmap
  if (bmp != null && !bmp.isRecycled) {
    Image(
      bitmap = bmp.asImageBitmap(),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = modifier
    )
  } else {
    Box(modifier = modifier.background(NexaSurfaceElevated))
  }
}

@Composable
private fun PlayheadOverlay(
  playheadMs: Long,
  totalDurationMs: Long,
  pxPerMs: Float,
  snapPointsMs: Set<Long>,
  onScrubTo: (Long) -> Unit,
  onSnapFeedback: () -> Unit
) {
  val density = LocalDensity.current
  val latestPlayheadMs by rememberUpdatedState(playheadMs)
  val latestTotalDurationMs by rememberUpdatedState(totalDurationMs)
  val latestPxPerMs by rememberUpdatedState(pxPerMs)
  val latestSnapPoints by rememberUpdatedState(snapPointsMs)
  val latestOnScrub by rememberUpdatedState(onScrubTo)
  val latestOnSnapFeedback by rememberUpdatedState(onSnapFeedback)

  var dragStartPlayheadMs by remember { mutableLongStateOf(0L) }
  var dragAccumPx by remember { mutableFloatStateOf(0f) }
  var lastSnappedTarget by remember { mutableLongStateOf(-1L) }

  val xPx = playheadMs * pxPerMs
  val handleWidthDp = 48.dp
  val handleHalfWidthPx = with(density) { (handleWidthDp / 2).toPx() }

  Box(
    modifier = Modifier
      .offset { IntOffset((xPx - handleHalfWidthPx).roundToInt(), 0) }
      .width(handleWidthDp)
      .fillMaxHeight()
      .zIndex(20f),
    contentAlignment = Alignment.TopCenter
  ) {
    Box(
      modifier = Modifier
        .size(48.dp)
        .pointerInput(Unit) {
          detectDragGestures(
            onDragStart = {
              dragStartPlayheadMs = latestPlayheadMs
              dragAccumPx = 0f
              lastSnappedTarget = -1L
            },
            onDrag = { change, dragAmount ->
              change.consume()
              dragAccumPx += dragAmount.x
              val maxDur = latestTotalDurationMs.coerceAtLeast(0L)
              val rawTargetMs = (dragStartPlayheadMs + (dragAccumPx / latestPxPerMs).toLong())
                .coerceIn(0L, maxDur)

              val snapThresholdMs = (12f / latestPxPerMs).toLong().coerceIn(40L, 160L)
              val nearestSnap = latestSnapPoints.minByOrNull { abs(it - rawTargetMs) }
              val resolvedMs = if (nearestSnap != null && abs(nearestSnap - rawTargetMs) <= snapThresholdMs) {
                if (lastSnappedTarget != nearestSnap) {
                  lastSnappedTarget = nearestSnap
                  latestOnSnapFeedback()
                }
                nearestSnap.coerceIn(0L, maxDur)
              } else {
                lastSnappedTarget = -1L
                rawTargetMs
              }
              latestOnScrub(resolvedMs)
            }
          )
        }
        .testTag("timeline_playhead_handle"),
      contentAlignment = Alignment.TopCenter
    ) {
      Box(
        modifier = Modifier
          .padding(top = 2.dp)
          .size(width = 14.dp, height = 16.dp)
          .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
          .background(NexaAccentGradient)
          .border(
            1.dp,
            NexaTextPrimary,
            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
          )
      )
    }

    Box(
      modifier = Modifier
        .padding(top = 16.dp)
        .width(2.dp)
        .fillMaxHeight()
        .background(
          Brush.verticalGradient(
            listOf(NexaTextPrimary, NexaCyan, NexaPurple)
          )
        )
    )
  }
}
