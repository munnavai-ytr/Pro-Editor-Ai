package com.example.ui.editor.components

import android.graphics.Bitmap
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.local.entity.MediaType
import com.example.data.media.Media3EffectsFactory
import com.example.data.media.TextBitmapRenderer
import com.example.domain.model.EvaluatedLayerTransform
import com.example.domain.model.TimelineClipUiModel
import com.example.ui.editor.SeekCommand
import com.example.ui.theme.NexaBlack
import com.example.ui.theme.NexaBorderSubtle
import com.example.ui.theme.NexaCyan
import com.example.ui.theme.NexaPurple
import com.example.ui.theme.NexaSurfaceElevated
import com.example.ui.theme.NexaTextPrimary
import com.example.ui.theme.NexaTextSecondary
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Interactive bounding-box layer used by both Overlay (PiP) clips and Text Overlay clips
 * on the live video preview canvas. Supports 1-finger drag, 2-finger pinch-to-scale & rotate,
 * AND a dedicated corner handle for 1-finger/mouse scale & rotate.
 */
@Composable
fun InteractiveCanvasLayerBox(
  clipId: Long,
  evaluatedTransform: EvaluatedLayerTransform,
  baseWidthDp: Dp,
  baseHeightDp: Dp,
  stageWidthPx: Float,
  stageHeightPx: Float,
  isSelected: Boolean,
  hasKeyframes: Boolean,
  testTag: String,
  onSelect: () -> Unit,
  onDoubleTap: (() -> Unit)? = null,
  onLiveTransform: (offsetX: Float, offsetY: Float, scale: Float, rotationDeg: Float) -> Unit,
  onCommitTransform: () -> Unit,
  content: @Composable () -> Unit
) {
  val density = LocalDensity.current
  val latestEval by rememberUpdatedState(evaluatedTransform)
  val latestStageW by rememberUpdatedState(stageWidthPx.coerceAtLeast(1f))
  val latestStageH by rememberUpdatedState(stageHeightPx.coerceAtLeast(1f))
  val latestOnLive by rememberUpdatedState(onLiveTransform)
  val latestOnCommit by rememberUpdatedState(onCommitTransform)
  val latestOnSelect by rememberUpdatedState(onSelect)
  val latestOnDoubleTap by rememberUpdatedState(onDoubleTap)

  val centerShiftXPx = evaluatedTransform.offsetX * (stageWidthPx * 0.5f)
  val centerShiftYPx = evaluatedTransform.offsetY * (stageHeightPx * 0.5f)

  val scaledWidthDp = (baseWidthDp * evaluatedTransform.scale.coerceIn(0.12f, 3.0f)).coerceAtLeast(36.dp)
  val scaledHeightDp = (baseHeightDp * evaluatedTransform.scale.coerceIn(0.12f, 3.0f)).coerceAtLeast(28.dp)

  Box(
    modifier = Modifier
      .offset { IntOffset(centerShiftXPx.roundToInt(), centerShiftYPx.roundToInt()) }
      .width(scaledWidthDp + 24.dp)
      .height(scaledHeightDp + 24.dp)
      .graphicsLayer {
        rotationZ = evaluatedTransform.rotationDeg
      }
      .pointerInput(clipId) {
        detectTapGestures(
          onTap = { latestOnSelect() },
          onDoubleTap = {
            latestOnSelect()
            latestOnDoubleTap?.invoke()
          }
        )
      }
      .pointerInput(clipId) {
        awaitEachGesture {
          var didTransform = false
          var workingOffsetX = 0f
          var workingOffsetY = 0f
          var workingScale = 1f
          var workingRotation = 0f
          var initialized = false

          do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isNotEmpty()) {
              val pan = event.calculatePan()
              val zoom = if (pressed.size >= 2) event.calculateZoom() else 1f
              val rot = if (pressed.size >= 2) event.calculateRotation() else 0f

              val movedEnough = abs(pan.x) > 0.5f ||
                abs(pan.y) > 0.5f ||
                abs(zoom - 1f) > 0.005f ||
                abs(rot) > 0.15f

              if (movedEnough) {
                if (!initialized) {
                  latestOnSelect()
                  workingOffsetX = latestEval.offsetX
                  workingOffsetY = latestEval.offsetY
                  workingScale = latestEval.scale
                  workingRotation = latestEval.rotationDeg
                  initialized = true
                }
                didTransform = true

                // Convert local rotated pan back into stage space so dragging feels natural even when rotated
                val rad = Math.toRadians(workingRotation.toDouble())
                val cosA = kotlin.math.cos(rad).toFloat()
                val sinA = kotlin.math.sin(rad).toFloat()
                val stageDx = pan.x * cosA - pan.y * sinA
                val stageDy = pan.x * sinA + pan.y * cosA

                workingOffsetX = (workingOffsetX + stageDx / (latestStageW * 0.5f)).coerceIn(-0.95f, 0.95f)
                workingOffsetY = (workingOffsetY + stageDy / (latestStageH * 0.5f)).coerceIn(-0.95f, 0.95f)
                workingScale = (workingScale * zoom).coerceIn(0.15f, 2.8f)
                workingRotation += rot

                latestOnLive(workingOffsetX, workingOffsetY, workingScale, workingRotation)
                event.changes.forEach { it.consume() }
              }
            }
          } while (event.changes.any { it.pressed })

          if (didTransform) {
            latestOnCommit()
          }
        }
      }
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    // Visual Layer Content
    Box(
      modifier = Modifier
        .width(scaledWidthDp)
        .height(scaledHeightDp)
        .graphicsLayer {
          alpha = evaluatedTransform.opacity.coerceIn(0f, 1f)
        }
        .clip(RoundedCornerShape(6.dp))
        .then(
          if (isSelected) {
            Modifier.border(
              width = 1.8.dp,
              color = if (hasKeyframes) Color(0xFFFFD166) else NexaCyan,
              shape = RoundedCornerShape(6.dp)
            )
          } else {
            Modifier
          }
        ),
      contentAlignment = Alignment.Center
    ) {
      content()
    }

    // Selected Bounding Box Handles (4 corner dots + Bottom-Right 1-finger Scale/Rotate Handle)
    if (isSelected) {
      val cornerColor = if (hasKeyframes) Color(0xFFFFD166) else NexaCyan

      // Top-Left & Bottom-Left & Top-Right visual anchors
      listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart).forEach { align ->
        Box(
          modifier = Modifier
            .align(align)
            .padding(8.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(cornerColor)
            .border(1.dp, NexaBlack, CircleShape)
        )
      }

      // Bottom-Right Interactive Scale & Rotate Handle (accessible 32dp visual / 48dp hit box)
      var startScale by remember(clipId) { mutableFloatStateOf(1f) }
      var startRot by remember(clipId) { mutableFloatStateOf(0f) }
      var startRadiusPx by remember(clipId) { mutableFloatStateOf(100f) }
      var startAngleRad by remember(clipId) { mutableFloatStateOf(0f) }
      var accumDx by remember(clipId) { mutableFloatStateOf(0f) }
      var accumDy by remember(clipId) { mutableFloatStateOf(0f) }

      val halfWpx = with(density) { (scaledWidthDp.toPx() * 0.5f).coerceAtLeast(24f) }
      val halfHpx = with(density) { (scaledHeightDp.toPx() * 0.5f).coerceAtLeast(20f) }
      val latestHalfW by rememberUpdatedState(halfWpx)
      val latestHalfH by rememberUpdatedState(halfHpx)

      Box(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .size(36.dp)
          .pointerInput(clipId) {
            detectDragGestures(
              onDragStart = {
                startScale = latestEval.scale
                startRot = latestEval.rotationDeg
                startRadiusPx = hypot(latestHalfW, latestHalfH).coerceAtLeast(24f)
                startAngleRad = atan2(latestHalfH, latestHalfW)
                accumDx = 0f
                accumDy = 0f
              },
              onDragEnd = { latestOnCommit() },
              onDragCancel = { latestOnCommit() },
              onDrag = { change, dragAmount ->
                change.consume()
                accumDx += dragAmount.x
                accumDy += dragAmount.y
                val vecX = latestHalfW + accumDx
                val vecY = latestHalfH + accumDy
                val newRadius = hypot(vecX, vecY).coerceAtLeast(12f)
                val newAngle = atan2(vecY, vecX)
                val nextScale = (startScale * (newRadius / startRadiusPx)).coerceIn(0.15f, 2.8f)
                val deltaDeg = Math.toDegrees((newAngle - startAngleRad).toDouble()).toFloat()
                latestOnLive(
                  latestEval.offsetX,
                  latestEval.offsetY,
                  nextScale,
                  startRot + deltaDeg
                )
              }
            )
          }
          .testTag("layer_scale_rotate_handle_$clipId"),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(cornerColor)
            .border(1.5.dp, NexaBlack, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Rounded.OpenInFull,
            contentDescription = "Scale and rotate layer",
            tint = NexaBlack,
            modifier = Modifier.size(13.dp)
          )
        }
      }
    }
  }
}

@OptIn(UnstableApi::class)
@Composable
fun OverlayPipVisualContent(
  overlayClip: TimelineClipUiModel,
  playheadMs: Long,
  isPlaying: Boolean
) {
  val context = LocalContext.current

  if (overlayClip.mediaType == MediaType.IMAGE) {
    val colorFilter = remember(overlayClip.brightness, overlayClip.contrast, overlayClip.saturation) {
      buildComposeColorFilter(overlayClip.brightness, overlayClip.contrast, overlayClip.saturation)
    }
    AsyncImage(
      model = Uri.parse(overlayClip.sourceUri),
      contentDescription = "Overlay image",
      contentScale = ContentScale.Crop,
      colorFilter = colorFilter,
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          rotationZ = overlayClip.rotationDegrees.toFloat()
          scaleX = if (overlayClip.flipHorizontal) -1f else 1f
          scaleY = if (overlayClip.flipVertical) -1f else 1f
        }
    )
  } else {
    val pipPlayer = remember(
      overlayClip.id,
      overlayClip.sourceUri,
      overlayClip.trimStartMs,
      overlayClip.trimEndMs
    ) {
      ExoPlayer.Builder(context).build().apply {
        volume = 0f
        repeatMode = Player.REPEAT_MODE_OFF
        val mediaItem = MediaItem.Builder()
          .setUri(Uri.parse(overlayClip.sourceUri))
          .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
              .setStartPositionMs(overlayClip.trimStartMs)
              .setEndPositionMs(overlayClip.trimEndMs)
              .build()
          )
          .build()
        setMediaItem(mediaItem)
        prepare()
      }
    }

    val effectSignature = remember(
      overlayClip.cropLeft,
      overlayClip.cropTop,
      overlayClip.cropRight,
      overlayClip.cropBottom,
      overlayClip.rotationDegrees,
      overlayClip.flipHorizontal,
      overlayClip.flipVertical,
      overlayClip.brightness,
      overlayClip.contrast,
      overlayClip.saturation
    ) {
      "${overlayClip.cropLeft},${overlayClip.cropTop},${overlayClip.cropRight},${overlayClip.cropBottom}|" +
        "${overlayClip.rotationDegrees}|${overlayClip.flipHorizontal}|${overlayClip.flipVertical}|" +
        "${overlayClip.brightness}|${overlayClip.contrast}|${overlayClip.saturation}"
    }

    LaunchedEffect(pipPlayer, effectSignature) {
      runCatching {
        pipPlayer.setVideoEffects(Media3EffectsFactory.buildPerClipVideoEffects(overlayClip))
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
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
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

@Composable
fun TextLayerVisualContent(
  textClip: TimelineClipUiModel,
  textBitmapRenderer: TextBitmapRenderer,
  stageHeightPx: Int
) {
  val tx = textClip.textOverlay ?: return
  val bitmap: Bitmap = remember(
    tx.content,
    tx.fontFamily,
    tx.fontSize,
    tx.color,
    tx.backgroundColor,
    tx.alignment,
    tx.isBold,
    tx.isItalic,
    stageHeightPx
  ) {
    textBitmapRenderer.renderTextBitmap(tx, stageHeightPx.coerceAtLeast(480))
  }

  Image(
    bitmap = bitmap.asImageBitmap(),
    contentDescription = tx.content,
    contentScale = ContentScale.Fit,
    modifier = Modifier.fillMaxSize()
  )
}

/**
 * Interactive Crop Rectangle Overlay rendered directly over the preview stage
 * when Crop mode is active on the selected clip.
 */
@Composable
fun InteractiveCropStageOverlay(
  cropLeft: Float,
  cropTop: Float,
  cropRight: Float,
  cropBottom: Float,
  stageWidthPx: Float,
  stageHeightPx: Float,
  onLiveCropChange: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
  onCommitCrop: (closeOverlay: Boolean) -> Unit,
  onResetCrop: () -> Unit
) {
  val density = LocalDensity.current
  val latestBounds by rememberUpdatedState(floatArrayOf(cropLeft, cropTop, cropRight, cropBottom))
  val latestW by rememberUpdatedState(stageWidthPx.coerceAtLeast(1f))
  val latestH by rememberUpdatedState(stageHeightPx.coerceAtLeast(1f))
  val latestOnChange by rememberUpdatedState(onLiveCropChange)
  val latestOnCommit by rememberUpdatedState(onCommitCrop)

  Box(
    modifier = Modifier
      .fillMaxSize()
      .testTag("interactive_crop_overlay")
  ) {
    // Scrim + Rule-of-thirds Crop Grid
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
          // Dragging inside the crop rectangle pans the crop window
          var startL = 0f
          var startT = 0f
          var startR = 1f
          var startB = 1f
          var activePan = false
          var dxSum = 0f
          var dySum = 0f
          detectDragGestures(
            onDragStart = { offset ->
              val b = latestBounds
              val lx = b[0] * latestW
              val ty = b[1] * latestH
              val rx = b[2] * latestW
              val by = b[3] * latestH
              activePan = offset.x in lx..rx && offset.y in ty..by
              startL = b[0]
              startT = b[1]
              startR = b[2]
              startB = b[3]
              dxSum = 0f
              dySum = 0f
            },
            onDragEnd = {
              if (activePan) latestOnCommit(false)
              activePan = false
            },
            onDragCancel = {
              if (activePan) latestOnCommit(false)
              activePan = false
            },
            onDrag = { change, drag ->
              if (!activePan) return@detectDragGestures
              change.consume()
              dxSum += drag.x / latestW
              dySum += drag.y / latestH
              val w = startR - startL
              val h = startB - startT
              val nl = (startL + dxSum).coerceIn(0f, (1f - w).coerceAtLeast(0f))
              val nt = (startT + dySum).coerceIn(0f, (1f - h).coerceAtLeast(0f))
              latestOnChange(nl, nt, (nl + w).coerceAtMost(1f), (nt + h).coerceAtMost(1f))
            }
          )
        }
    ) {
      val lx = cropLeft * size.width
      val ty = cropTop * size.height
      val rx = cropRight * size.width
      val by = cropBottom * size.height
      val cw = (rx - lx).coerceAtLeast(1f)
      val ch = (by - ty).coerceAtLeast(1f)

      val scrim = Color.Black.copy(alpha = 0.62f)
      // Top, Bottom, Left, Right exterior scrims
      drawRect(scrim, Offset(0f, 0f), Size(size.width, ty))
      drawRect(scrim, Offset(0f, by), Size(size.width, (size.height - by).coerceAtLeast(0f)))
      drawRect(scrim, Offset(0f, ty), Size(lx, ch))
      drawRect(scrim, Offset(rx, ty), Size((size.width - rx).coerceAtLeast(0f), ch))

      // Crop border
      drawRect(
        color = NexaCyan,
        topLeft = Offset(lx, ty),
        size = Size(cw, ch),
        style = Stroke(width = 2.dp.toPx())
      )

      // Rule-of-thirds guidelines
      val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
      val gridColor = Color.White.copy(alpha = 0.4f)
      for (step in 1..2) {
        val gx = lx + (cw * step / 3f)
        val gy = ty + (ch * step / 3f)
        drawLine(gridColor, Offset(gx, ty), Offset(gx, by), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        drawLine(gridColor, Offset(lx, gy), Offset(rx, gy), strokeWidth = 1.dp.toPx(), pathEffect = dash)
      }
    }

    // 4 Draggable Corner Handles (48dp accessible touch targets)
    val handleSizeDp = 48.dp
    val halfHandlePx = with(density) { (handleSizeDp / 2).toPx() }

    CropCornerHandle(
      xPx = cropLeft * stageWidthPx - halfHandlePx,
      yPx = cropTop * stageHeightPx - halfHandlePx,
      testTag = "crop_handle_tl",
      onDragDeltaFraction = { dx, dy ->
        val b = latestBounds
        latestOnChange(
          (b[0] + dx).coerceIn(0f, b[2] - 0.15f),
          (b[1] + dy).coerceIn(0f, b[3] - 0.15f),
          b[2],
          b[3]
        )
      },
      onDragCommit = { latestOnCommit(false) },
      stageWidthPx = stageWidthPx,
      stageHeightPx = stageHeightPx
    )

    CropCornerHandle(
      xPx = cropRight * stageWidthPx - halfHandlePx,
      yPx = cropTop * stageHeightPx - halfHandlePx,
      testTag = "crop_handle_tr",
      onDragDeltaFraction = { dx, dy ->
        val b = latestBounds
        latestOnChange(
          b[0],
          (b[1] + dy).coerceIn(0f, b[3] - 0.15f),
          (b[2] + dx).coerceIn(b[0] + 0.15f, 1f),
          b[3]
        )
      },
      onDragCommit = { latestOnCommit(false) },
      stageWidthPx = stageWidthPx,
      stageHeightPx = stageHeightPx
    )

    CropCornerHandle(
      xPx = cropLeft * stageWidthPx - halfHandlePx,
      yPx = cropBottom * stageHeightPx - halfHandlePx,
      testTag = "crop_handle_bl",
      onDragDeltaFraction = { dx, dy ->
        val b = latestBounds
        latestOnChange(
          (b[0] + dx).coerceIn(0f, b[2] - 0.15f),
          b[1],
          b[2],
          (b[3] + dy).coerceIn(b[1] + 0.15f, 1f)
        )
      },
      onDragCommit = { latestOnCommit(false) },
      stageWidthPx = stageWidthPx,
      stageHeightPx = stageHeightPx
    )

    CropCornerHandle(
      xPx = cropRight * stageWidthPx - halfHandlePx,
      yPx = cropBottom * stageHeightPx - halfHandlePx,
      testTag = "crop_handle_br",
      onDragDeltaFraction = { dx, dy ->
        val b = latestBounds
        latestOnChange(
          b[0],
          b[1],
          (b[2] + dx).coerceIn(b[0] + 0.15f, 1f),
          (b[3] + dy).coerceIn(b[1] + 0.15f, 1f)
        )
      },
      onDragCommit = { latestOnCommit(false) },
      stageWidthPx = stageWidthPx,
      stageHeightPx = stageHeightPx
    )

    // Floating Crop Action Pill (Reset / Done)
    Surface(
      color = NexaSurfaceElevated.copy(alpha = 0.94f),
      shape = CircleShape,
      border = androidx.compose.foundation.BorderStroke(1.dp, NexaBorderSubtle),
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 8.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onResetCrop)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("crop_reset_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = "Reset crop",
            tint = NexaTextSecondary,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Reset",
            style = MaterialTheme.typography.labelSmall,
            color = NexaTextSecondary
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clip(CircleShape)
            .background(NexaCyan)
            .clickable { latestOnCommit(true) }
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("crop_apply_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = "Apply crop",
            tint = NexaBlack,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Done",
            style = MaterialTheme.typography.labelSmall,
            color = NexaBlack,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

@Composable
private fun CropCornerHandle(
  xPx: Float,
  yPx: Float,
  stageWidthPx: Float,
  stageHeightPx: Float,
  testTag: String,
  onDragDeltaFraction: (dxFrac: Float, dyFrac: Float) -> Unit,
  onDragCommit: () -> Unit
) {
  val latestW by rememberUpdatedState(stageWidthPx.coerceAtLeast(1f))
  val latestH by rememberUpdatedState(stageHeightPx.coerceAtLeast(1f))
  val latestOnDelta by rememberUpdatedState(onDragDeltaFraction)
  val latestOnCommit by rememberUpdatedState(onDragCommit)

  Box(
    modifier = Modifier
      .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
      .size(48.dp)
      .pointerInput(testTag) {
        detectDragGestures(
          onDragEnd = { latestOnCommit() },
          onDragCancel = { latestOnCommit() },
          onDrag = { change, dragAmount ->
            change.consume()
            latestOnDelta(dragAmount.x / latestW, dragAmount.y / latestH)
          }
        )
      }
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .size(16.dp)
        .clip(CircleShape)
        .background(NexaCyan)
        .border(2.dp, NexaBlack, CircleShape)
    )
  }
}

/**
 * Synchronized Audio Track Preview Controller playing active TrackType.AUDIO clips
 * with trim, speed, and 0%–200% volume (using LoudnessEnhancer when > 100%).
 */
@OptIn(UnstableApi::class)
@Composable
fun AudioTrackPreviewController(
  audioClips: List<TimelineClipUiModel>,
  playheadMs: Long,
  isPlaying: Boolean,
  seekCommand: SeekCommand
) {
  val activeAudioClips = remember(audioClips, playheadMs) {
    audioClips.filter { playheadMs >= it.startMs && playheadMs < it.endMs && it.volume > 0.002f }
  }

  activeAudioClips.forEach { audioClip ->
    SingleAudioClipPreviewPlayer(
      audioClip = audioClip,
      playheadMs = playheadMs,
      isPlaying = isPlaying,
      seekVersion = seekCommand.version
    )
  }
}

@OptIn(UnstableApi::class)
@Composable
private fun SingleAudioClipPreviewPlayer(
  audioClip: TimelineClipUiModel,
  playheadMs: Long,
  isPlaying: Boolean,
  seekVersion: Long
) {
  val context = LocalContext.current

  val audioPlayer = remember(
    audioClip.id,
    audioClip.sourceUri,
    audioClip.trimStartMs,
    audioClip.trimEndMs
  ) {
    ExoPlayer.Builder(context).build().apply {
      repeatMode = Player.REPEAT_MODE_OFF
      val item = MediaItem.Builder()
        .setUri(Uri.parse(audioClip.sourceUri))
        .setClippingConfiguration(
          MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(audioClip.trimStartMs)
            .setEndPositionMs(audioClip.trimEndMs)
            .build()
        )
        .build()
      setMediaItem(item)
      prepare()
    }
  }

  var loudnessEnhancer by remember(audioPlayer) {
    androidx.compose.runtime.mutableStateOf<LoudnessEnhancer?>(null)
  }

  DisposableEffect(audioPlayer) {
    val listener = object : Player.Listener {
      override fun onAudioSessionIdChanged(audioSessionId: Int) {
        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId > 0) {
          runCatching {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId)
          }
        }
      }
    }
    audioPlayer.addListener(listener)
    onDispose {
      audioPlayer.removeListener(listener)
      runCatching { loudnessEnhancer?.release() }
      audioPlayer.release()
    }
  }

  // Apply 0–200% volume
  LaunchedEffect(audioPlayer, loudnessEnhancer, audioClip.volume) {
    applyVolumeWithBoost(audioPlayer, loudnessEnhancer, audioClip.volume)
  }

  val targetLocalMs = ((playheadMs - audioClip.startMs) * audioClip.speed)
    .toLong()
    .coerceIn(0L, audioClip.trimmedSourceDurationMs)

  LaunchedEffect(audioPlayer, isPlaying, audioClip.speed) {
    audioPlayer.playbackParameters = PlaybackParameters(audioClip.speed)
    audioPlayer.playWhenReady = isPlaying
  }

  LaunchedEffect(audioPlayer, seekVersion, isPlaying) {
    if (!isPlaying || abs(audioPlayer.currentPosition - targetLocalMs) > 220L) {
      audioPlayer.seekTo(targetLocalMs)
    }
  }
}

@OptIn(UnstableApi::class)
fun applyVolumeWithBoost(
  player: ExoPlayer,
  enhancer: LoudnessEnhancer?,
  volume: Float
) {
  val safeVol = volume.coerceIn(0f, 2.0f)
  if (safeVol <= 1.0f) {
    player.volume = safeVol
    runCatching {
      enhancer?.setTargetGain(0)
      enhancer?.enabled = false
    }
  } else {
    player.volume = 1.0f
    // Convert linear gain (1.0..2.0) to millibels: mB = 2000 * log10(gain)
    val gainMb = (2000.0 * log10(safeVol.toDouble())).roundToInt().coerceIn(0, 620)
    runCatching {
      if (enhancer != null) {
        enhancer.setTargetGain(gainMb)
        enhancer.enabled = true
      }
    }
  }
}

fun buildComposeColorFilter(
  brightness: Float,
  contrast: Float,
  saturation: Float
): ColorFilter? {
  if (abs(brightness) < 0.01f && abs(contrast) < 0.01f && abs(saturation) < 0.01f) {
    return null
  }
  val satMatrix = ColorMatrix().apply {
    setToSaturation((1f + saturation).coerceIn(0f, 2f))
  }
  val c = (1f + contrast).coerceIn(0.1f, 2.2f)
  val bOffset = brightness * 90f + (1f - c) * 128f
  val bcMatrix = ColorMatrix(
    floatArrayOf(
      c, 0f, 0f, 0f, bOffset,
      0f, c, 0f, 0f, bOffset,
      0f, 0f, c, 0f, bOffset,
      0f, 0f, 0f, 1f, 0f
    )
  )
  satMatrix.timesAssign(bcMatrix)
  return ColorFilter.colorMatrix(satMatrix)
}
