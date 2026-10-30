package com.example.ui.editor.components

import android.graphics.Color as AndroidColor
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.MediaType
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
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewStage(
  aspectRatio: AspectRatio,
  mainTrackClips: List<TimelineClipUiModel>,
  overlayTracks: Map<Int, List<TimelineClipUiModel>>,
  selectedClipId: Long?,
  playheadMs: Long,
  totalDurationMs: Long,
  isPlaying: Boolean,
  seekCommand: SeekCommand,
  zoomScale: Float,
  onTogglePlayPause: () -> Unit,
  onRestartToBeginning: () -> Unit,
  onPlaybackPositionUpdated: (Long) -> Unit,
  onPlaybackEnded: () -> Unit,
  onSelectOverlayClip: (Long) -> Unit,
  onZoomIn: () -> Unit,
  onZoomOut: () -> Unit,
  onResetZoom: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
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

  DisposableEffect(exoPlayer, lifecycleOwner) {
    val listener = object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
          latestOnPlaybackEnded()
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
      exoPlayer.release()
    }
  }

  // Structural signature of main track clips to rebuild/update MediaItems immediately on trims/splits/reorders
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

  // Active overlay clips at current playheadMs (sorted bottom-to-top by trackIndex)
  val activeOverlays = remember(overlayTracks, playheadMs) {
    overlayTracks.entries
      .sortedBy { it.key }
      .mapNotNull { (_, trackClips) ->
        trackClips.firstOrNull { playheadMs >= it.startMs && playheadMs < it.endMs }
      }
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

      val stageModifier = if (containerRatio > targetRatio) {
        Modifier
          .fillMaxHeight()
          .width(maxHeight * targetRatio)
      } else {
        Modifier
          .fillMaxWidth()
          .height(maxWidth / targetRatio)
      }

      Box(
        modifier = stageModifier
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

          // Render synchronized PiP Overlays matching Media3 Transformer VideoCompositorSettings
          activeOverlays.forEachIndexed { index, overlayClip ->
            OverlayPipPreviewBox(
              overlayClip = overlayClip,
              playheadMs = playheadMs,
              isPlaying = isPlaying,
              isSelected = overlayClip.id == selectedClipId,
              stackOffsetIndex = index,
              onClick = { onSelectOverlayClip(overlayClip.id) },
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                  end = 10.dp,
                  bottom = (10 + index * 18).dp
                )
                .fillMaxWidth(0.38f)
                .fillMaxHeight(0.38f)
            )
          }
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
          .height(48.dp)
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

        // Right: Quick Timeline Zoom Controls (complements two-finger pinch-to-zoom)
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

@OptIn(UnstableApi::class)
@Composable
private fun OverlayPipPreviewBox(
  overlayClip: TimelineClipUiModel,
  playheadMs: Long,
  isPlaying: Boolean,
  isSelected: Boolean,
  stackOffsetIndex: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val borderColor = if (isSelected) NexaCyan else Color.White.copy(alpha = 0.45f)

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(NexaBlack)
      .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .testTag("overlay_pip_${overlayClip.id}_$stackOffsetIndex")
  ) {
    if (overlayClip.mediaType == MediaType.IMAGE) {
      AsyncImage(
        model = Uri.parse(overlayClip.sourceUri),
        contentDescription = "Overlay image",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    } else {
      val pipPlayer = remember(overlayClip.id, overlayClip.sourceUri, overlayClip.trimStartMs, overlayClip.trimEndMs) {
        ExoPlayer.Builder(context).build().apply {
          volume = 0f // PiP overlay sequence is video-only in Transformer export
          repeatMode = Player.REPEAT_MODE_OFF
          setMediaItem(buildPreviewMediaItem(overlayClip))
          prepare()
        }
      }

      DisposableEffect(pipPlayer) {
        onDispose { pipPlayer.release() }
      }

      val targetLocalMs = ((playheadMs - overlayClip.startMs) * overlayClip.speed)
        .toLong()
        .coerceIn(0L, overlayClip.trimmedSourceDurationMs)

      LaunchedEffect(pipPlayer, isPlaying, overlayClip.speed) {
        pipPlayer.playbackParameters = PlaybackParameters(overlayClip.speed)
        pipPlayer.playWhenReady = isPlaying
      }

      LaunchedEffect(pipPlayer, playheadMs, isPlaying) {
        if (!isPlaying || abs(pipPlayer.currentPosition - targetLocalMs) > 180L) {
          pipPlayer.seekTo(targetLocalMs)
        }
      }

      AndroidView(
        factory = { ctx ->
          PlayerView(ctx).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            player = pipPlayer
          }
        },
        update = { view ->
          if (view.player !== pipPlayer) view.player = pipPlayer
        },
        modifier = Modifier.fillMaxSize()
      )
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
