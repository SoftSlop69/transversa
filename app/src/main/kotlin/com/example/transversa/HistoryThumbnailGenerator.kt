// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.transversa.core.model.MediaKind
import java.io.File
import java.util.UUID
import kotlin.math.max

internal object HistoryThumbnailGenerator {
    private const val SIZE = 320
    private const val AUDIO_ARTWORK_VERSION = 1

    fun create(context: Context, uri: Uri, kind: MediaKind, container: String): File? = runCatching {
        val bitmap = when (kind) {
            MediaKind.VIDEO -> videoFrame(context, uri)
            MediaKind.IMAGE -> imageThumbnail(context, uri)
            MediaKind.AUDIO -> audioArtwork(container)
            MediaKind.UNKNOWN -> null
        } ?: return null
        val directory = File(context.filesDir, "history-thumbnails").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        file
    }.getOrNull()

    fun refreshExistingAudioArtwork(context: Context, entries: List<ConversionHistoryEntry>) {
        val preferences = context.getSharedPreferences("transversa_conversion_history", Context.MODE_PRIVATE)
        if (preferences.getInt("audio_artwork_version", 0) >= AUDIO_ARTWORK_VERSION) return

        entries.filter { it.kind == MediaKind.AUDIO }.forEach { entry ->
            val file = entry.thumbnailPath?.let(::File)?.takeIf(File::isFile) ?: return@forEach
            runCatching {
                val bitmap = audioArtwork(fileExtension(entry.outputName))
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        preferences.edit().putInt("audio_artwork_version", AUDIO_ARTWORK_VERSION).apply()
    }

    private fun videoFrame(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.scaled()
        } finally {
            retriever.release()
        }
    }

    private fun imageThumbnail(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > SIZE * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.scaled()
    }

    private fun audioArtwork(container: String): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(213, 233, 215) }
        canvas.drawRoundRect(RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), 40f, 40f, backgroundPaint)

        val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 107, 60)
            strokeWidth = 14f
            strokeCap = Paint.Cap.ROUND
        }
        val waveform = Path().apply {
            moveTo(48f, 144f)
            cubicTo(102f, 70f, 134f, 70f, 160f, 144f)
            cubicTo(186f, 218f, 218f, 218f, 272f, 144f)
        }
        canvas.drawPath(waveform, waveformPaint)

        val label = container.uppercase()
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 107, 60) }
        val pill = RectF(74f, 213f, 246f, 281f)
        canvas.drawRoundRect(pill, 30f, 30f, pillPaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = if (label.length > 5) 30f else 40f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        canvas.drawText(label, SIZE / 2f, 259f, textPaint)
        return bitmap
    }

    private fun Bitmap.scaled(): Bitmap {
        val scale = SIZE.toFloat() / max(width, height)
        if (scale >= 1f) return this
        val resized = Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
        if (resized !== this) recycle()
        return resized
    }

    private fun fileExtension(name: String): String = name.substringAfterLast('.', "audio").uppercase()
}
