package com.example.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.MediaType
import com.example.domain.model.ExportResolution
import com.example.domain.model.TimelineClipUiModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
@Singleton
class VideoExportManager @Inject constructor(
  @ApplicationContext private val context: Context
) {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val exportScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var activeTransformer: Transformer? = null
  private var activeTempFile: File? = null
  private var progressRunnable: Runnable? = null

  fun startExport(
    projectName: String,
    aspectRatio: AspectRatio,
    resolution: ExportResolution,
    clips: List<TimelineClipUiModel>,
    onProgress: (Int) -> Unit,
    onCompleted: (outputUri: String, displayFileName: String) -> Unit,
    onError: (String) -> Unit
  ) {
    exportScope.launch {
      cancelActiveExportInternal()

      val mainClips = clips
        .filter { it.trackIndex == 0 }
        .sortedWith(compareBy({ it.orderIndex }, { it.id }))

      if (mainClips.isEmpty()) {
        onError("Add at least one clip to the main video track before exporting.")
        return@launch
      }

      val (targetWidth, targetHeight) = resolution.dimensionsFor(aspectRatio)
      val gapImageUri = withContext(Dispatchers.IO) { ensureTransparentGapPng() }

      val exportCacheDir = withContext(Dispatchers.IO) {
        File(context.cacheDir, "exports").apply { mkdirs() }
      }
      val tempOutputFile = File(exportCacheDir, "export_tmp_${System.currentTimeMillis()}.mp4")
      activeTempFile = tempOutputFile

      try {
        val composition = buildTimelineComposition(
          mainClips = mainClips,
          overlayClips = clips.filter { it.trackIndex > 0 },
          targetWidth = targetWidth,
          targetHeight = targetHeight,
          gapImageUri = gapImageUri
        )

        val progressHolder = ProgressHolder()

        val listener = object : Transformer.Listener {
          override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            stopProgressPolling()
            onProgress(100)
            exportScope.launch {
              val savedResult = withContext(Dispatchers.IO) {
                saveMp4ToMoviesMediaStore(tempOutputFile, projectName, resolution)
              }
              activeTransformer = null
              if (savedResult != null) {
                onCompleted(savedResult.first, savedResult.second)
              } else {
                onError("Export finished, but saving to Movies folder failed.")
              }
            }
          }

          override fun onError(
            composition: Composition,
            exportResult: ExportResult,
            exportException: ExportException
          ) {
            stopProgressPolling()
            activeTransformer = null
            runCatching { tempOutputFile.delete() }
            onError(
              exportException.localizedMessage
                ?: "Video encoding failed (${exportException.errorCodeName})."
            )
          }
        }

        val transformer = Transformer.Builder(context)
          .setVideoMimeType(MimeTypes.VIDEO_H264)
          .setAudioMimeType(MimeTypes.AUDIO_AAC)
          .addListener(listener)
          .build()

        activeTransformer = transformer
        onProgress(1)
        transformer.start(composition, tempOutputFile.absolutePath)

        val pollTask = object : Runnable {
          override fun run() {
            val currentTransformer = activeTransformer ?: return
            val state = currentTransformer.getProgress(progressHolder)
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
              onProgress(progressHolder.progress.coerceIn(1, 99))
            }
            if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
              mainHandler.postDelayed(this, 100L)
            }
          }
        }
        progressRunnable = pollTask
        mainHandler.postDelayed(pollTask, 100L)
      } catch (e: Exception) {
        stopProgressPolling()
        activeTransformer = null
        runCatching { tempOutputFile.delete() }
        onError(e.localizedMessage ?: "Failed to initialize video export.")
      }
    }
  }

  fun cancelExport() {
    exportScope.launch {
      cancelActiveExportInternal()
    }
  }

  private fun cancelActiveExportInternal() {
    stopProgressPolling()
    runCatching { activeTransformer?.cancel() }
    activeTransformer = null
    activeTempFile?.let { file ->
      exportScope.launch(Dispatchers.IO) {
        runCatching { if (file.exists()) file.delete() }
      }
    }
    activeTempFile = null
  }

  private fun stopProgressPolling() {
    progressRunnable?.let { mainHandler.removeCallbacks(it) }
    progressRunnable = null
  }

  private fun buildTimelineComposition(
    mainClips: List<TimelineClipUiModel>,
    overlayClips: List<TimelineClipUiModel>,
    targetWidth: Int,
    targetHeight: Int,
    gapImageUri: Uri
  ): Composition {
    val mainEditedItems = mainClips.map { clip -> buildEditedMediaItem(clip, removeAudio = false) }
    val hasAnyVideoWithPotentialAudio = mainClips.any { it.mediaType == MediaType.VIDEO }

    val mainSequence = if (hasAnyVideoWithPotentialAudio) {
      EditedMediaItemSequence.withAudioAndVideoFrom(mainEditedItems)
    } else {
      EditedMediaItemSequence.withVideoFrom(mainEditedItems)
    }

    val mainDurationMs = mainClips.sumOf { it.timelineDurationMs }.coerceAtLeast(200L)

    val overlayByTrack = overlayClips
      .groupBy { it.trackIndex }
      .toSortedMap(compareByDescending { it }) // Higher trackIndex rendered topmost

    val overlaySequences = mutableListOf<EditedMediaItemSequence>()
    val overlayActiveRangesUs = mutableListOf<List<LongRange>>()

    for ((_, rawTrackClips) in overlayByTrack) {
      val sorted = rawTrackClips.sortedBy { it.startMs }
      val seqItems = mutableListOf<EditedMediaItem>()
      val activeRanges = mutableListOf<LongRange>()
      var cursorMs = 0L

      for (ovClip in sorted) {
        if (ovClip.startMs >= mainDurationMs) continue
        val effectiveStartMs = ovClip.startMs.coerceAtLeast(cursorMs)
        if (effectiveStartMs > cursorMs) {
          val gapMs = effectiveStartMs - cursorMs
          seqItems.add(buildGapEditedItem(gapImageUri, gapMs))
          cursorMs = effectiveStartMs
        }

        val maxAllowedTimelineDur = (mainDurationMs - cursorMs).coerceAtLeast(0L)
        if (maxAllowedTimelineDur < 100L) break

        val actualTimelineDur = ovClip.timelineDurationMs.coerceAtMost(maxAllowedTimelineDur)
        val adjustedTrimEnd = (ovClip.trimStartMs + (actualTimelineDur * ovClip.speed).toLong())
          .coerceAtMost(ovClip.trimEndMs)

        val clampedClip = ovClip.copy(trimEndMs = adjustedTrimEnd)
        seqItems.add(buildEditedMediaItem(clampedClip, removeAudio = true))

        val startUs = cursorMs * 1_000L
        val endUs = (cursorMs + clampedClip.timelineDurationMs) * 1_000L
        activeRanges.add(startUs..endUs)
        cursorMs += clampedClip.timelineDurationMs
      }

      if (cursorMs < mainDurationMs) {
        seqItems.add(buildGapEditedItem(gapImageUri, mainDurationMs - cursorMs))
      }

      if (seqItems.isNotEmpty() && activeRanges.isNotEmpty()) {
        overlaySequences.add(EditedMediaItemSequence.withVideoFrom(seqItems))
        overlayActiveRangesUs.add(activeRanges)
      }
    }

    val presentation = Presentation.createForWidthAndHeight(
      targetWidth,
      targetHeight,
      Presentation.LAYOUT_SCALE_TO_FIT
    )

    return if (overlaySequences.isEmpty()) {
      Composition.Builder(listOf(mainSequence))
        .setEffects(Effects(emptyList(), listOf(presentation)))
        .build()
    } else {
      // Sequence 0..k-1 are PiP overlays (rendered in front), last sequence is full-screen main track
      val allSequences = overlaySequences + mainSequence
      val mainSeqId = overlaySequences.size

      val compositorSettings = object : VideoCompositorSettings {
        override fun getOutputSize(inputSizes: List<Size>): Size {
          return Size(targetWidth, targetHeight)
        }

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
          if (inputId == mainSeqId || inputId !in overlayActiveRangesUs.indices) {
            return StaticOverlaySettings.Builder().build()
          }
          val isVisible = overlayActiveRangesUs[inputId].any { range ->
            presentationTimeUs in range
          }
          return if (isVisible) {
            // Position each overlay layer neatly in the bottom-right PiP region
            val verticalOffset = -0.46f + (inputId * 0.14f).coerceAtMost(0.42f)
            StaticOverlaySettings.Builder()
              .setScale(0.38f, 0.38f)
              .setOverlayFrameAnchor(0f, 0f)
              .setBackgroundFrameAnchor(0.48f, verticalOffset)
              .setAlphaScale(1f)
              .build()
          } else {
            StaticOverlaySettings.Builder()
              .setAlphaScale(0f)
              .build()
          }
        }
      }

      Composition.Builder(allSequences)
        .setVideoCompositorSettings(compositorSettings)
        .setEffects(Effects(emptyList(), listOf(presentation)))
        .build()
    }
  }

  private fun buildEditedMediaItem(
    clip: TimelineClipUiModel,
    removeAudio: Boolean
  ): EditedMediaItem {
    val uri = Uri.parse(clip.sourceUri)
    return when (clip.mediaType) {
      MediaType.IMAGE -> {
        val imageItem = MediaItem.Builder()
          .setUri(uri)
          .setImageDurationMs(clip.timelineDurationMs)
          .build()
        EditedMediaItem.Builder(imageItem)
          .setFrameRate(30)
          .build()
      }
      else -> {
        val clippedItem = MediaItem.Builder()
          .setUri(uri)
          .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
              .setStartPositionMs(clip.trimStartMs)
              .setEndPositionMs(clip.trimEndMs)
              .build()
          )
          .build()

        val audioProcessors = mutableListOf<AudioProcessor>()
        val videoEffects = mutableListOf<Effect>()

        if (abs(clip.speed - 1.0f) > 0.01f) {
          val speedPair = Effects.createExperimentalSpeedChangingEffect(
            ConstantSpeedProvider(clip.speed)
          )
          if (!removeAudio) {
            audioProcessors.add(speedPair.first)
          }
          videoEffects.add(speedPair.second)
        }

        EditedMediaItem.Builder(clippedItem)
          .setRemoveAudio(removeAudio)
          .setEffects(Effects(audioProcessors, videoEffects))
          .build()
      }
    }
  }

  private fun buildGapEditedItem(gapUri: Uri, durationMs: Long): EditedMediaItem {
    val mediaItem = MediaItem.Builder()
      .setUri(gapUri)
      .setImageDurationMs(durationMs.coerceAtLeast(50L))
      .build()
    return EditedMediaItem.Builder(mediaItem)
      .setFrameRate(30)
      .build()
  }

  private fun ensureTransparentGapPng(): Uri {
    val gapFile = File(context.cacheDir, "nexa_gap_transparent.png")
    if (!gapFile.exists() || gapFile.length() == 0L) {
      val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
      bmp.eraseColor(Color.TRANSPARENT)
      FileOutputStream(gapFile).use { fos ->
        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
      }
      bmp.recycle()
    }
    return Uri.fromFile(gapFile)
  }

  private fun saveMp4ToMoviesMediaStore(
    tempFile: File,
    projectName: String,
    resolution: ExportResolution
  ): Pair<String, String>? {
    if (!tempFile.exists() || tempFile.length() == 0L) return null

    val sanitizedName = projectName
      .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
      .trim()
      .replace(Regex("\\s+"), "_")
      .ifBlank { "Project" }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "PlayNexa_${sanitizedName}_${resolution.shortSidePx}p_$stamp.mp4"

    val resolver = context.contentResolver
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
          put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
          put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
          put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/PlayNexa")
          put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, values) ?: return fallbackFileProviderUri(tempFile, fileName)

        resolver.openOutputStream(itemUri)?.use { out ->
          FileInputStream(tempFile).use { input ->
            input.copyTo(out)
          }
        }

        val readyValues = ContentValues().apply {
          put(MediaStore.Video.Media.IS_PENDING, 0)
        }
        resolver.update(itemUri, readyValues, null, null)
        runCatching { tempFile.delete() }
        itemUri.toString() to "Movies/PlayNexa/$fileName"
      } else {
        @Suppress("DEPRECATION")
        val moviesDir = File(
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
          "PlayNexa"
        ).apply { mkdirs() }
        val targetFile = File(moviesDir, fileName)
        FileInputStream(tempFile).use { input ->
          FileOutputStream(targetFile).use { out ->
            input.copyTo(out)
          }
        }
        @Suppress("DEPRECATION")
        val values = ContentValues().apply {
          put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
          put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
          put(MediaStore.Video.Media.DATA, targetFile.absolutePath)
        }
        val insertedUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        runCatching { tempFile.delete() }
        val shareableUri = insertedUri
          ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
        shareableUri.toString() to "Movies/PlayNexa/$fileName"
      }
    } catch (_: Exception) {
      fallbackFileProviderUri(tempFile, fileName)
    }
  }

  private fun fallbackFileProviderUri(tempFile: File, fileName: String): Pair<String, String>? {
    return runCatching {
      val finalCacheFile = File(tempFile.parentFile, fileName)
      if (tempFile.renameTo(finalCacheFile)) {
        val uri = FileProvider.getUriForFile(
          context,
          "${context.packageName}.fileprovider",
          finalCacheFile
        )
        uri.toString() to fileName
      } else {
        val uri = FileProvider.getUriForFile(
          context,
          "${context.packageName}.fileprovider",
          tempFile
        )
        uri.toString() to tempFile.name
      }
    }.getOrNull()
  }

  private class ConstantSpeedProvider(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed
    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
  }
}
