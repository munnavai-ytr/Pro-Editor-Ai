package com.example.data.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import com.example.data.local.entity.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InspectedMedia(
  val usableUri: String,
  val mediaType: MediaType,
  val durationMs: Long,
  val thumbnailPath: String?
)

@Singleton
class MediaMetadataHelper @Inject constructor(
  @ApplicationContext private val context: Context
) {
  companion object {
    const val DEFAULT_IMAGE_DURATION_MS = 4_000L
    const val MAX_IMAGE_SOURCE_DURATION_MS = 30_000L
  }

  suspend fun inspectAndPrepareMedia(rawUri: Uri): InspectedMedia = withContext(Dispatchers.IO) {
    val effectiveUri = ensureDurableReadableUri(rawUri)
    val mimeType = resolveMimeType(effectiveUri)
    val mediaType = when {
      mimeType?.startsWith("image/") == true -> MediaType.IMAGE
      mimeType?.startsWith("audio/") == true -> MediaType.AUDIO
      else -> MediaType.VIDEO
    }

    when (mediaType) {
      MediaType.VIDEO -> inspectVideo(effectiveUri)
      MediaType.IMAGE -> inspectImage(effectiveUri)
      MediaType.AUDIO -> inspectAudio(effectiveUri)
    }
  }

  private fun ensureDurableReadableUri(uri: Uri): Uri {
    if (uri.scheme == "file") return uri
    val persistTaken = runCatching {
      context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION
      )
      true
    }.getOrDefault(false)

    if (persistTaken) {
      return uri
    }

    // Fallback: copy into internal app storage if the URI provider does not grant persistable permissions
    return runCatching {
      val mediaDir = File(context.filesDir, "imported_media").apply { mkdirs() }
      val ext = resolveExtension(uri) ?: "mp4"
      val destFile = File(mediaDir, "media_${UUID.randomUUID()}.$ext")
      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(destFile).use { output ->
          input.copyTo(output)
        }
      }
      Uri.fromFile(destFile)
    }.getOrDefault(uri)
  }

  private fun resolveMimeType(uri: Uri): String? {
    if (uri.scheme == "content") {
      return context.contentResolver.getType(uri)
    }
    val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
  }

  private fun resolveExtension(uri: Uri): String? {
    val mime = context.contentResolver.getType(uri)
    if (mime != null) {
      MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { return it }
    }
    return MimeTypeMap.getFileExtensionFromUrl(uri.toString()).takeIf { it.isNotBlank() }
  }

  private fun inspectVideo(uri: Uri): InspectedMedia {
    val retriever = MediaMetadataRetriever()
    return try {
      setRetrieverSource(retriever, uri)
      val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
      val durationMs = durationStr?.toLongOrNull()?.coerceAtLeast(300L) ?: 3_000L

      val sampleTimeUs = (durationMs.coerceAtMost(2_000L) / 3L) * 1_000L
      val frameBitmap = extractScaledFrame(retriever, sampleTimeUs, 480)
      val savedThumb = frameBitmap?.let { saveThumbnailBitmap(it) }
      frameBitmap?.recycle()

      InspectedMedia(
        usableUri = uri.toString(),
        mediaType = MediaType.VIDEO,
        durationMs = durationMs,
        thumbnailPath = savedThumb
      )
    } catch (_: Exception) {
      InspectedMedia(
        usableUri = uri.toString(),
        mediaType = MediaType.VIDEO,
        durationMs = 3_000L,
        thumbnailPath = null
      )
    } finally {
      runCatching { retriever.release() }
    }
  }

  private fun inspectImage(uri: Uri): InspectedMedia {
    val thumbPath = runCatching {
      val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      openUriStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

      val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight).coerceAtLeast(1)
      var sampleSize = 1
      while (maxDim / sampleSize > 512) {
        sampleSize *= 2
      }
      val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
      val bmp = openUriStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
      val path = bmp?.let { saveThumbnailBitmap(it) }
      bmp?.recycle()
      path
    }.getOrNull()

    return InspectedMedia(
      usableUri = uri.toString(),
      mediaType = MediaType.IMAGE,
      durationMs = DEFAULT_IMAGE_DURATION_MS,
      thumbnailPath = thumbPath
    )
  }

  private fun inspectAudio(uri: Uri): InspectedMedia {
    val retriever = MediaMetadataRetriever()
    val durationMs = try {
      setRetrieverSource(retriever, uri)
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        ?.toLongOrNull()
        ?.coerceAtLeast(500L) ?: 5_000L
    } catch (_: Exception) {
      5_000L
    } finally {
      runCatching { retriever.release() }
    }

    return InspectedMedia(
      usableUri = uri.toString(),
      mediaType = MediaType.AUDIO,
      durationMs = durationMs,
      thumbnailPath = null
    )
  }

  fun setRetrieverSource(retriever: MediaMetadataRetriever, uri: Uri) {
    if (uri.scheme == "file" && uri.path != null) {
      retriever.setDataSource(uri.path)
    } else {
      retriever.setDataSource(context, uri)
    }
  }

  fun openUriStream(uri: Uri) =
    if (uri.scheme == "file" && uri.path != null) {
      File(uri.path!!).inputStream()
    } else {
      context.contentResolver.openInputStream(uri)
    }

  fun extractScaledFrame(
    retriever: MediaMetadataRetriever,
    timeUs: Long,
    targetMaxPx: Int
  ): Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      retriever.getScaledFrameAtTime(
        timeUs,
        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
        targetMaxPx,
        targetMaxPx
      ) ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } else {
      val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
      if (raw != null && maxOf(raw.width, raw.height) > targetMaxPx) {
        val ratio = targetMaxPx.toFloat() / maxOf(raw.width, raw.height).toFloat()
        val w = (raw.width * ratio).toInt().coerceAtLeast(1)
        val h = (raw.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
        if (scaled != raw) raw.recycle()
        scaled
      } else {
        raw
      }
    }
  }

  private fun saveThumbnailBitmap(bitmap: Bitmap): String? {
    return runCatching {
      val dir = File(context.filesDir, "thumbnails").apply { mkdirs() }
      val outFile = File(dir, "thumb_${UUID.randomUUID()}.jpg")
      FileOutputStream(outFile).use { fos ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, fos)
      }
      outFile.absolutePath
    }.getOrNull()
  }
}
