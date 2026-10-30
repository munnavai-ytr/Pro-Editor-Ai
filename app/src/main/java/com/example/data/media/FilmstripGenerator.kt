package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import com.example.data.local.entity.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@Singleton
class FilmstripGenerator @Inject constructor(
  @ApplicationContext private val context: Context,
  private val metadataHelper: MediaMetadataHelper
) {
  private val extractionSemaphore = Semaphore(3)

  // Cache up to ~16MB of small filmstrip frame bitmaps in RAM
  private val memoryCache = object : LruCache<String, Bitmap>(16 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int {
      return (value.byteCount / 1024).coerceAtLeast(1)
    }
  }

  private val diskCacheDir: File by lazy {
    File(context.cacheDir, "filmstrip_frames").apply { mkdirs() }
  }

  fun getCachedFrameImmediate(assetId: Long, mediaType: MediaType, timeMs: Long): Bitmap? {
    val key = cacheKey(assetId, mediaType, timeMs)
    return memoryCache.get(key)
  }

  suspend fun loadFrame(
    assetId: Long,
    sourceUri: String,
    mediaType: MediaType,
    timeMs: Long
  ): Bitmap? {
    val key = cacheKey(assetId, mediaType, timeMs)
    memoryCache.get(key)?.let { if (!it.isRecycled) return it }

    return withContext(Dispatchers.IO) {
      memoryCache.get(key)?.let { if (!it.isRecycled) return@withContext it }

      val diskFile = File(diskCacheDir, "$key.jpg")
      if (diskFile.exists() && diskFile.length() > 0L) {
        BitmapFactory.decodeFile(diskFile.absolutePath)?.also { decoded ->
          memoryCache.put(key, decoded)
          return@withContext decoded
        }
      }

      extractionSemaphore.withPermit {
        memoryCache.get(key)?.let { if (!it.isRecycled) return@withPermit it }

        val extracted = when (mediaType) {
          MediaType.VIDEO -> extractVideoTile(Uri.parse(sourceUri), timeMs)
          MediaType.IMAGE -> extractImageTile(Uri.parse(sourceUri))
          MediaType.AUDIO -> null
        }

        extracted?.let { bmp ->
          memoryCache.put(key, bmp)
          runCatching {
            FileOutputStream(diskFile).use { fos ->
              bmp.compress(Bitmap.CompressFormat.JPEG, 76, fos)
            }
          }
        }
        extracted
      }
    }
  }

  private fun cacheKey(assetId: Long, mediaType: MediaType, timeMs: Long): String {
    val bucketMs = if (mediaType == MediaType.IMAGE) 0L else (timeMs.coerceAtLeast(0L) / 250L) * 250L
    return "asset_${assetId}_t_${bucketMs}"
  }

  private fun extractVideoTile(uri: Uri, timeMs: Long): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
      metadataHelper.setRetrieverSource(retriever, uri)
      metadataHelper.extractScaledFrame(
        retriever = retriever,
        timeUs = timeMs.coerceAtLeast(0L) * 1_000L,
        targetMaxPx = 144
      )
    } catch (_: Exception) {
      null
    } finally {
      runCatching { retriever.release() }
    }
  }

  private fun extractImageTile(uri: Uri): Bitmap? {
    return runCatching {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      metadataHelper.openUriStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
      val maxDim = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
      var sample = 1
      while (maxDim / sample > 160) {
        sample *= 2
      }
      val opts = BitmapFactory.Options().apply { inSampleSize = sample }
      metadataHelper.openUriStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
  }
}
