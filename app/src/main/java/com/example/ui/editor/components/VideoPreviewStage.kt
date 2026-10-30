package com.example.ui.editor.components

import android.graphics.Color as AndroidColor
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.MediaType
import com.example.data.local.entity.TrackType
import com.example.data.media.Media3EffectsFactory
import com.example.data.media.TextBitmapRenderer
import com.example.domain.model.KeyframeInterpolator
import com.example.domain.model.TimeFormatUtils
import com.example.domain.model.TimelineClipUiModel
import com.example.ui.editor.SeekCommand
import com.example.ui.theme.NexaBlack
import com.example.ui.theme.NexaBorderSubtle
import com.example.ui.theme.NexaCyan
import com.example.ui.theme.NexaSurface
import com.example.ui.theme.NexaSurfaceElevated
import com.example.ui.theme.NexaTextDisabled
import com.example.ui.theme.NexaTextPrimary
import com.example.ui.theme.NexaTextSecondary
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewStage(
  aspectRatio: AspectRatio,
  isOriginalAudioMuted: Boolean,
  mainTrackClips: List<TimelineClipUiModel>,
  overlayTracks: Map<Int, List<TimelineClipUiModel>>,
  textTrackClips: List<TimelineClipUiModel>,
  audioTrackClips: List<TimelineClipUiModel>,
  selectedClip: TimelineClipUiModel?,
  isInteractiveCropMode: Boolean,
  playheadMs: Long,
  totalDurationMs: Long,
  isPlaying: Boolean,
  seekCommand: SeekCommand,
  zoomScale: Float,
  textBitmapRenderer: TextBitmapRenderer,
  onTogglePlayPause: () -> Unit,
  onRestartToBeginning: () -> Unit,
  onPlaybackPositionUpdated: (Long) -> Unit,
  onPlaybackEnded: () -> Unit,
  onSelectClip: (Long) -> Unit,
  onDoubleTapTextClip: () -> Unit,
  onLiveCanvasTransform: (clipId: Long, offsetX: Float, offsetY: Float, scale: Float, rotationDeg: Float) -> Unit,
  onCommitCanvasTransform: () -> Unit,
  onLiveCropChange: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
  onCommitCrop: (closeOverlay: Boolean) -> Unit,
  onResetCrop: () -> Unit,
  onZoomIn: () -> Unit,
  onZoomOut: () -> Unit,
  onResetZoom: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val latestMainClips by rememberUpdatedState(mainTrackClips)
  val latestPlayheadMs by rememberUpdatedState(playheadMs)
  val latestOnPositionUpdated by rememberUpdatedState(onPlaybackPositionUpdated)
  val latestOnPlaybackEnded by rememberUpdatedState(onPlaybackEnded)

  val exoPlayer = remember(context) {
    ExoPlayer.Builder(context).build().apply {
      repeatMode = Player.REPEAT_MODE_OFF
      playWhenReady = false
    }
  }

  var mainLoudnessEnhancer by remember(exoPlayer) {
    mutableStateOf<LoudnessEnhancer?>(null)
  }

  DisposableEffect(exoPlayer, lifecycleOwner) {
    val listener = object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
          latestOnPlaybackEnded()
        }
      }

      override fun onAudioSessionIdChanged(audioSessionId: Int) {
        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId > 0) {
          runCatching {
            mainLoudnessEnhancer?.release()
            mainLoudnessEnhancer = LoudnessEnhancer(audioSessionId)
          }
        }
      }

      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val idx = exoPlayer.currentMediaItemIndex
        val clip = latestMainClips.getOrNull(idx)
        if (clip != null) {
          val desiredSpeed = clip.speed.coerceIn(0.25f, 4.0f)
          if (abs(exoPlayer.playbackParameters.speed - desiredSpeed) > 0.01f) {
            exoPlayer.playbackParameters = PlaybackParameters(desiredSpeed)
          }
        }
      }
    }
    exoPlayer.addListener(listener)

    val lifecycleObserver = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
        exoPlayer.playWhenReady = false
      }
    }
    lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

    onDispose {
      lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
      exoPlayer.removeListener(listener)
      runCatching { mainLoudnessEnhancer?.release() }
      exoPlayer.release()
    }
  }

  // Synchronized Audio Track playback
  AudioTrackPreviewController(
    audioClips = audioTrackClips,
    playheadMs = playheadMs,
    isPlaying = isPlaying,
    seekCommand = seekCommand
  )

  // Determine active Main Video Track clip at current playheadMs
  val activeMainIndexAndOffset = remember(mainTrackClips, playheadMs) {
    mapTimelineToExoPosition(mainTrackClips, playheadMs)
  }
  val activeMainClip = mainTrackClips.getOrNull(activeMainIndexAndOffset.first)

  // Apply Original Audio Mute + Per-Clip Volume (0-200%) on main ExoPlayer
  val targetMainVolume = if (isOriginalAudioMuted) 0f else (activeMainClip?.volume ?: 1f)
  LaunchedEffect(exoPlayer, mainLoudnessEnhancer, targetMainVolume) {
    applyVolumeWithBoost(exoPlayer, mainLoudnessEnhancer, targetMainVolume)
  }

  // Apply Media3 per-clip Video Effects (Crop, Rotate 90°, Flip H/V, Brightness/Contrast/Saturation)
  val isEditingActiveMainCrop = isInteractiveCropMode && selectedClip?.id == activeMainClip?.id
  val mainEffectClip = remember(activeMainClip, isEditingActiveMainCrop) {
    if (activeMainClip == null) {
      null
    } else if (isEditingActiveMainCrop) {
      // Show full uncropped frame underneath interactive crop handles while dragging crop bounds
      activeMainClip.copy(cropLeft = 0f, cropTop = 0f, cropRight = 1f, cropBottom = 1f)
    } else {
      activeMainClip
    }
  }
  val mainEffectsSignature = remember(mainEffectClip) {
    if (mainEffectClip == null) {
      "empty"
    } else {
      "${mainEffectClip.id}|${mainEffectClip.cropLeft},${mainEffectClip.cropTop},${mainEffectClip.cropRight},${mainEffectClip.cropBottom}|" +
        "${mainEffectClip.rotationDegrees}|${mainEffectClip.flipHorizontal}|${mainEffectClip.flipVertical}|" +
        "${mainEffectClip.brightness}|${mainEffectClip.contrast}|${mainEffectClip.saturation}"
    }
  }
  LaunchedEffect(exoPlayer, mainEffectsSignature) {
    runCatching {
      val effects = mainEffectClip?.let { Media3EffectsFactory.buildPerClipVideoEffects(it) }.orEmpty()
      exoPlayer.setVideoEffects(effects)
    }
  }

  // Evaluate Basic Transition at current playheadMs (Fade, Slide Left, Slide Right, Zoom In)
  val activeTransitionFrame = remember(mainTrackClips, playheadMs) {
    Media3EffectsFactory.evaluateTransitionAtMs(mainTrackClips, playheadMs)
  }

  // Rebuild MediaItems playlist when main track structure/trims change
  val playlistSignature = remember(mainTrackClips) {
    mainTrackClips.joinToString("|") { clip ->
      "${clip.id}:${clip.sourceUri}:${clip.trimStartMs}:${clip.trimEndMs}:${clip.mediaType}:${
        if (clip.mediaType == MediaType.IMAGE) clip.timelineDurationMs else 0L
      }"
    }
  }

  LaunchedEffect(playlistSignature) {
    if (mainTrackClips.isEmpty()) {
      exoPlayer.clearMediaItems()
      return@LaunchedEffect
    }
    val mediaItems = mainTrackClips.map { clip -> buildPreviewMediaItem(clip) }
    val (targetIdx, offsetInClipMs) = mapTimelineToExoPosition(mainTrackClips, latestPlayheadMs)
    val targetSpeed = mainTrackClips.getOrNull(targetIdx)?.speed ?: 1.0f

    exoPlayer.setMediaItems(mediaItems, targetIdx, offsetInClipMs)
    exoPlayer.playbackParameters = PlaybackParameters(targetSpeed)
    exoPlayer.prepare()
  }

  // Respond immediately to user scrubbing / seek commands
  LaunchedEffect(seekCommand.version) {
    if (mainTrackClips.isEmpty() || seekCommand.version == 0L) return@LaunchedEffect
    val (targetIdx, offsetInClipMs) = mapTimelineToExoPosition(mainTrackClips, seekCommand.targetMs)
    val targetSpeed = mainTrackClips.getOrNull(targetIdx)?.speed ?: 1.0f
    if (abs(exoPlayer.playbackParameters.speed - targetSpeed) > 0.01f) {
      exoPlayer.playbackParameters = PlaybackParameters(targetSpeed)
    }
    exoPlayer.seekTo(targetIdx, offsetInClipMs)
  }

  // Play / Pause control and 60fps playhead auto-advance loop
  LaunchedEffect(isPlaying, mainTrackClips) {
    exoPlayer.playWhenReady = isPlaying && mainTrackClips.isNotEmpty()
    if (isPlaying && mainTrackClips.isNotEmpty()) {
      val (startIdx, startOffset) = mapTimelineToExoPosition(mainTrackClips, latestPlayheadMs)
      val activeClip = mainTrackClips.getOrNull(startIdx)
      if (activeClip != null) {
        exoPlayer.playbackParameters = PlaybackParameters(activeClip.speed)
      }
      if (exoPlayer.currentMediaItemIndex != startIdx ||
        abs(exoPlayer.currentPosition - startOffset) > 120L
      ) {
        exoPlayer.seekTo(startIdx, startOffset)
      }

      while (true) {
        val clipsSnapshot = latestMainClips
        if (clipsSnapshot.isEmpty()) break
        val currentIdx = exoPlayer.currentMediaItemIndex.coerceIn(0, clipsSnapshot.lastIndex)
        val currentClip = clipsSnapshot[currentIdx]
        if (abs(exoPlayer.playbackParameters.speed - currentClip.speed) > 0.01f) {
          exoPlayer.playbackParameters = PlaybackParameters(currentClip.speed)
        }
        val posInClip = exoPlayer.currentPosition.coerceIn(0L, currentClip.trimmedSourceDurationMs)
        val timelinePos = currentClip.startMs + (posInClip / currentClip.speed).toLong()
        latestOnPositionUpdated(timelinePos)
        delay(16L)
      }
    }
  }

  // Active Overlay clips at current playheadMs (sorted bottom-to-top by trackIndex)
  val activeOverlays = remember(overlayTracks, playheadMs) {
    overlayTracks.entries
      .sortedBy { it.key }
      .flatMap { (_, trackClips) ->
        trackClips.filter { playheadMs >= it.startMs && playheadMs < it.endMs }
      }
  }

  // Active Text Overlay clips at current playheadMs
  val activeTextClips = remember(textTrackClips, playheadMs) {
    textTrackClips.filter { playheadMs >= it.startMs && playheadMs < it.endMs && it.textOverlay != null }
  }

  Column(modifier = modifier.fillMaxWidth()) {
    // Aspect-Ratio-Aware Stage Box
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(horizontal = 12.dp, vertical = 6.dp),
      contentAlignment = Alignment.Center
    ) {
      val containerRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else 1f
      val targetRatio = aspectRatio.ratioFloat

      val stageWidthDp = if (containerRatio > targetRatio) maxHeight * targetRatio else maxWidth
      val stageHeightDp = if (containerRatio > targetRatio) maxHeight else maxWidth / targetRatio
      val stageWidthPx = with(density) { stageWidthDp.toPx().coerceAtLeast(1f) }
      val stageHeightPx = with(density) { stageHeightDp.toPx().coerceAtLeast(1f) }

      Box(
        modifier = Modifier
          .width(stageWidthDp)
          .height(stageHeightDp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF050507))
          .border(1.dp, NexaBorderSubtle, RoundedCornerShape(12.dp))
          .testTag("video_preview_stage"),
        contentAlignment = Alignment.Center
      ) {
        if (mainTrackClips.isEmpty()) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.VideoLibrary,
              contentDescription = null,
              tint = NexaTextDisabled,
              modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Tap Media below to add clips",
              style = MaterialTheme.typography.bodySmall,
              color = NexaTextSecondary
            )
          }
        } else {
          // 1. Base Main Video Layer (with live Transition animation)
          Box(
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                translationX = activeTransitionFrame.translationFractionX * stageWidthPx
                scaleX = activeTransitionFrame.scale
                scaleY = activeTransitionFrame.scale
                alpha = activeTransitionFrame.alpha
              }
          ) {
            AndroidView(
              factory = { ctx ->
                PlayerView(ctx).apply {
                  useController = false
                  resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                  setShutterBackgroundColor(AndroidColor.parseColor("#050507"))
                  setBackgroundColor(AndroidColor.parseColor("#050507"))
                  layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                  )
                  player = exoPlayer
                }
              },
              update = { view ->
                if (view.player !== exoPlayer) {
                  view.player = exoPlayer
                }
              },
              modifier = Modifier.fillMaxSize()
            )
          }
        }

        // 2. Interactive Keyframe-Interpolated Overlay (PiP) Layers
        activeOverlays.forEach { overlayClip ->
          val localTimeMs = (playheadMs - overlayClip.startMs).coerceIn(0L, overlayClip.timelineDurationMs)
          val eval = KeyframeInterpolator.evaluateOverlay(
            transform = overlayClip.overlayTransform,
            keyframes = overlayClip.keyframes,
            localTimeMs = localTimeMs
          )

          InteractiveCanvasLayerBox(
            clipId = overlayClip.id,
            evaluatedTransform = eval,
            baseWidthDp = stageWidthDp,
            baseHeightDp = stageHeightDp,
            stageWidthPx = stageWidthPx,
            stageHeightPx = stageHeightPx,
            isSelected = selectedClip?.id == overlayClip.id && !isInteractiveCropMode,
            hasKeyframes = overlayClip.hasKeyframesEnabled || overlayClip.keyframes.isNotEmpty(),
            testTag = "overlay_pip_${overlayClip.id}",
            onSelect = { onSelectClip(overlayClip.id) },
            onLiveTransform = { ox, oy, sc, rot ->
              onLiveCanvasTransform(overlayClip.id, ox, oy, sc, rot)
            },
            onCommitTransform = onCommitCanvasTransform
          ) {
            OverlayPipVisualContent(
              overlayClip = overlayClip,
              playheadMs = playheadMs,
              isPlaying = isPlaying
            )
          }
        }

        // 3. Interactive Keyframe-Interpolated Text Overlay Layers
        activeTextClips.forEach { textClip ->
          val tx = textClip.textOverlay ?: return@forEach
          val localTimeMs = (playheadMs - textClip.startMs).coerceIn(0L, textClip.timelineDurationMs)
          val eval = KeyframeInterpolator.evaluateText(
            textOverlay = tx,
            keyframes = textClip.keyframes,
            localTimeMs = localTimeMs
          )
          val renderedBmp = remember(
            tx.content,
            tx.fontFamily,
            tx.fontSize,
            tx.color,
            tx.backgroundColor,
            tx.alignment,
            tx.isBold,
            tx.isItalic,
            stageHeightPx.roundToInt()
          ) {
            textBitmapRenderer.renderTextBitmap(tx, stageHeightPx.roundToInt().coerceAtLeast(480))
          }
          // Size natural text box proportionally to stage height so preview & BitmapOverlay export match
          val naturalWidthDp = stageHeightDp * (renderedBmp.width.toFloat() / stageHeightPx.coerceAtLeast(1f))
          val naturalHeightDp = stageHeightDp * (renderedBmp.height.toFloat() / stageHeightPx.coerceAtLeast(1f))

          InteractiveCanvasLayerBox(
            clipId = textClip.id,
            evaluatedTransform = eval,
            baseWidthDp = naturalWidthDp.coerceAtLeast(48.dp),
            baseHeightDp = naturalHeightDp.coerceAtLeast(28.dp),
            stageWidthPx = stageWidthPx,
            stageHeightPx = stageHeightPx,
            isSelected = selectedClip?.id == textClip.id,
            hasKeyframes = textClip.hasKeyframesEnabled || textClip.keyframes.isNotEmpty(),
            testTag = "text_canvas_layer_${textClip.id}",
            onSelect = { onSelectClip(textClip.id) },
            onDoubleTap = onDoubleTapTextClip,
            onLiveTransform = { ox, oy, sc, rot ->
              onLiveCanvasTransform(textClip.id, ox, oy, sc, rot)
            },
            onCommitTransform = onCommitCanvasTransform
          ) {
            TextLayerVisualContent(
              textClip = textClip,
              textBitmapRenderer = textBitmapRenderer,
              stageHeightPx = stageHeightPx.roundToInt()
            )
          }
        }

        // 4. Interactive Draggable Crop Frame Overlay when active
        if (isInteractiveCropMode && selectedClip != null &&
          (selectedClip.trackType == TrackType.VIDEO || selectedClip.trackType == TrackType.OVERLAY)
        ) {
          InteractiveCropStageOverlay(
            cropLeft = selectedClip.cropLeft,
            cropTop = selectedClip.cropTop,
            cropRight = selectedClip.cropRight,
            cropBottom = selectedClip.cropBottom,
            stageWidthPx = stageWidthPx,
            stageHeightPx = stageHeightPx,
            onLiveCropChange = onLiveCropChange,
            onCommitCrop = onCommitCrop,
            onResetCrop = onResetCrop
          )
        }
      }
    }

    // Playback Transport & Zoom Bar
    Surface(
      color = NexaSurface,
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(46.dp)
          .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        // Left: Time Readout (current / total)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(start = 6.dp)
        ) {
          Text(
            text = TimeFormatUtils.formatTimelinePrecision(playheadMs),
            style = MaterialTheme.typography.labelLarge,
            color = NexaCyan,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("current_time_text")
          )
          Text(
            text = " / ${TimeFormatUtils.formatTimelinePrecision(totalDurationMs)}",
            style = MaterialTheme.typography.labelMedium,
            color = NexaTextSecondary,
            modifier = Modifier.testTag("total_time_text")
          )
        }

        // Center: Restart + Play/Pause
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onRestartToBeginning,
            enabled = mainTrackClips.isNotEmpty(),
            modifier = Modifier
              .size(48.dp)
              .testTag("restart_playback_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.Replay,
              contentDescription = "Jump to start",
              tint = if (mainTrackClips.isNotEmpty()) NexaTextSecondary else NexaTextDisabled,
              modifier = Modifier.size(18.dp)
            )
          }

          IconButton(
            onClick = onTogglePlayPause,
            enabled = mainTrackClips.isNotEmpty(),
            modifier = Modifier
              .size(48.dp)
              .testTag("play_pause_button")
          ) {
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (mainTrackClips.isNotEmpty()) NexaSurfaceElevated else NexaBlack)
                .border(
                  1.dp,
                  if (isPlaying) NexaCyan else NexaBorderSubtle,
                  CircleShape
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(
                  if (isPlaying) R.string.editor_pause else R.string.editor_play
                ),
                tint = if (mainTrackClips.isNotEmpty()) NexaTextPrimary else NexaTextDisabled,
                modifier = Modifier.size(22.dp)
              )
            }
          }
        }

        // Right: Quick Timeline Zoom Controls
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onZoomOut,
            modifier = Modifier
              .size(36.dp)
              .testTag("zoom_out_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.Remove,
              contentDescription = "Zoom out timeline",
              tint = NexaTextSecondary,
              modifier = Modifier.size(16.dp)
            )
          }

          Surface(
            color = NexaSurfaceElevated,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, NexaBorderSubtle),
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .clickable(onClick = onResetZoom)
          ) {
            Text(
              text = String.format(java.util.Locale.US, "%.1fx", zoomScale),
              style = MaterialTheme.typography.labelSmall,
              color = NexaTextSecondary,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
          }

          IconButton(
            onClick = onZoomIn,
            modifier = Modifier
              .size(36.dp)
              .testTag("zoom_in_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = "Zoom in timeline",
              tint = NexaTextSecondary,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }
    }
  }
}

private fun buildPreviewMediaItem(clip: TimelineClipUiModel): MediaItem {
  val uri = Uri.parse(clip.sourceUri)
  return if (clip.mediaType == MediaType.IMAGE) {
    MediaItem.Builder()
      .setMediaId(clip.id.toString())
      .setUri(uri)
      .setImageDurationMs(clip.timelineDurationMs)
      .build()
  } else {
    MediaItem.Builder()
      .setMediaId(clip.id.toString())
      .setUri(uri)
      .setClippingConfiguration(
        MediaItem.ClippingConfiguration.Builder()
          .setStartPositionMs(clip.trimStartMs)
          .setEndPositionMs(clip.trimEndMs)
          .build()
      )
      .build()
  }
}

private fun mapTimelineToExoPosition(
  mainClips: List<TimelineClipUiModel>,
  playheadMs: Long
): Pair<Int, Long> {
  if (mainClips.isEmpty()) return 0 to 0L
  val clamped = playheadMs.coerceAtLeast(0L)
  for (i in mainClips.indices) {
    val clip = mainClips[i]
    val isLast = i == mainClips.lastIndex
    if (clamped < clip.endMs || isLast) {
      val timelineDelta = (clamped - clip.startMs).coerceIn(0L, clip.timelineDurationMs)
      val sourceOffset = (timelineDelta * clip.speed)
        .toLong()
        .coerceIn(0L, clip.trimmedSourceDurationMs)
      return i to sourceOffset
    }
  }
  return 0 to 0L
}
