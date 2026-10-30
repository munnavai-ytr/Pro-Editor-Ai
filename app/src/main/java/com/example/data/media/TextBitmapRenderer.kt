package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import com.example.data.local.entity.TextAlignOption
import com.example.data.local.entity.TextOverlayEntity
import com.example.domain.model.NexaFontOption
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

@Singleton
class TextBitmapRenderer @Inject constructor(
  @ApplicationContext private val context: Context
) {
  private val bitmapCache = object : LruCache<String, Bitmap>(12 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int =
      (value.byteCount / 1024).coerceAtLeast(1)
  }

  /**
   * Renders [textOverlay] into an ARGB_8888 Bitmap scaled proportionally to [stageHeightPx],
   * so both interactive preview and 720p/1080p Media3 BitmapOverlay export match identically.
   */
  fun renderTextBitmap(
    textOverlay: TextOverlayEntity,
    stageHeightPx: Int = 1080
  ): Bitmap {
    val cleanContent = textOverlay.content.ifBlank { " " }
    val safeStageH = stageHeightPx.coerceIn(320, 2160)
    val cacheKey = buildString {
      append(cleanContent)
      append('|').append(textOverlay.fontFamily)
      append('|').append(textOverlay.fontSize)
      append('|').append(textOverlay.color)
      append('|').append(textOverlay.backgroundColor)
      append('|').append(textOverlay.alignment)
      append('|').append(textOverlay.isBold)
      append('|').append(textOverlay.isItalic)
      append('|').append(safeStageH)
    }

    bitmapCache.get(cacheKey)?.let { cached ->
      if (!cached.isRecycled) return cached
    }

    val fontOption = NexaFontOption.fromName(textOverlay.fontFamily)
    val baseTypeface = runCatching {
      ResourcesCompat.getFont(context, fontOption.fontResId)
    }.getOrNull() ?: Typeface.SANS_SERIF

    val styleFlags = when {
      textOverlay.isBold && textOverlay.isItalic -> Typeface.BOLD_ITALIC
      textOverlay.isBold -> Typeface.BOLD
      textOverlay.isItalic -> Typeface.ITALIC
      else -> Typeface.NORMAL
    }
    val resolvedTypeface = Typeface.create(baseTypeface, styleFlags)

    // Scale font size relative to a 720px reference stage height
    val pxScale = safeStageH / 720f
    val textSizePx = (textOverlay.fontSize.coerceIn(12f, 80f) * 1.45f * pxScale).coerceAtLeast(16f)

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      typeface = resolvedTypeface
      textSize = textSizePx
      color = textOverlay.color
      isFakeBoldText = textOverlay.isBold
      if (textOverlay.isItalic && !resolvedTypeface.isItalic) {
        textSkewX = -0.22f
      }
      if (textOverlay.backgroundColor == null) {
        setShadowLayer(textSizePx * 0.14f, 0f, textSizePx * 0.06f, Color.argb(190, 0, 0, 0))
      }
    }

    val lines = cleanContent.split("\n")
    val maxLineWidth = lines.maxOfOrNull { line -> ceil(textPaint.measureText(line)).toInt() }
      ?.coerceAtLeast(16) ?: 32
    val maxAllowedWidth = (safeStageH * 1.2f).toInt().coerceAtLeast(240)
    val layoutWidth = maxLineWidth.coerceIn(24, maxAllowedWidth)

    val layoutAlignment = when (TextAlignOption.fromString(textOverlay.alignment)) {
      TextAlignOption.LEFT -> Layout.Alignment.ALIGN_NORMAL
      TextAlignOption.CENTER -> Layout.Alignment.ALIGN_CENTER
      TextAlignOption.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
    }

    val staticLayout = StaticLayout.Builder
      .obtain(cleanContent, 0, cleanContent.length, textPaint, layoutWidth)
      .setAlignment(layoutAlignment)
      .setIncludePad(true)
      .setLineSpacing(0f, 1.08f)
      .build()

    val padX = (textSizePx * 0.55f).toInt().coerceAtLeast(12)
    val padY = (textSizePx * 0.35f).toInt().coerceAtLeast(8)

    val bmpWidth = (staticLayout.width + padX * 2).coerceAtLeast(32)
    val bmpHeight = (staticLayout.height + padY * 2).coerceAtLeast(32)

    val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    textOverlay.backgroundColor?.let { pillColor ->
      val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pillColor
        style = Paint.Style.FILL
      }
      val cornerRadius = textSizePx * 0.42f
      canvas.drawRoundRect(
        RectF(2f, 2f, bmpWidth - 2f, bmpHeight - 2f),
        cornerRadius,
        cornerRadius,
        bgPaint
      )
    }

    canvas.save()
    canvas.translate(padX.toFloat(), padY.toFloat())
    staticLayout.draw(canvas)
    canvas.restore()

    bitmapCache.put(cacheKey, bitmap)
    return bitmap
  }
}
