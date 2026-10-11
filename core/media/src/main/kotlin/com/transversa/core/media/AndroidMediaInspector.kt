// SPDX-License-Identifier: GPL-3.0-or-later
package com.transversa.core.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.provider.OpenableColumns
import android.net.Uri
import com.transversa.core.model.MediaKind

class AndroidMediaInspector(context: Context) {
    private val applicationContext = context.applicationContext
    private val contentResolver: ContentResolver = applicationContext.contentResolver

    fun inspect(uri: Uri): MediaDescriptor {
        val (displayName, sizeBytes) = readDocumentDetails(uri)
        val image = inspectImage(uri)
        if (image != null) {
            return MediaDescriptor(
                uri = uri,
                displayName = displayName,
                mimeType = image.mimeType,
                kind = MediaKind.IMAGE,
                sizeBytes = sizeBytes,
                width = image.width,
                height = image.height,
            )
        }

        val tracks = inspectTracks(uri)
        val video = tracks.video
        if (video != null) {
            return MediaDescriptor(
                uri = uri,
                displayName = displayName,
                mimeType = video.mimeType,
                kind = MediaKind.VIDEO,
                sizeBytes = sizeBytes,
                durationMs = video.durationMs ?: tracks.audio?.durationMs,
                width = video.width,
                height = video.height,
                hasAudio = tracks.audio != null,
                rotationDegrees = video.rotationDegrees,
            )
        }

        val audio = tracks.audio
        if (audio != null) {
            return MediaDescriptor(
                uri = uri,
                displayName = displayName,
                mimeType = audio.mimeType,
                kind = MediaKind.AUDIO,
                sizeBytes = sizeBytes,
                durationMs = audio.durationMs,
            )
        }

        return MediaDescriptor(
            uri = uri,
            displayName = displayName,
            mimeType = contentResolver.getType(uri),
            kind = MediaKind.UNKNOWN,
            sizeBytes = sizeBytes,
        )
    }

    private fun readDocumentDetails(uri: Uri): Pair<String, Long?> {
        var name: String? = null
        var size: Long? = null
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameColumn >= 0) name = cursor.getString(nameColumn)
                    if (sizeColumn >= 0) size = cursor.getLong(sizeColumn).takeIf { it >= 0L }
                }
            }
        return (name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "Selected media") to size
    }

    private fun inspectImage(uri: Uri): ImageInfo? = try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        val mimeType = options.outMimeType
        if (options.outWidth > 0 && options.outHeight > 0 && mimeType?.startsWith("image/") == true) {
            ImageInfo(mimeType, options.outWidth, options.outHeight)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    private fun inspectTracks(uri: Uri): TrackInfo {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(applicationContext, uri, null)
            var video: Track? = null
            var audio: Track? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mimeType = format.getString(MediaFormat.KEY_MIME) ?: continue
                when {
                    mimeType.startsWith("video/") && video == null -> video = format.toTrack(mimeType)
                    mimeType.startsWith("audio/") && audio == null -> audio = format.toTrack(mimeType)
                }
            }
            return TrackInfo(video, audio)
        } catch (_: Exception) {
            return TrackInfo()
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.toTrack(mimeType: String): Track = Track(
        mimeType = mimeType,
        durationMs = if (containsKey(MediaFormat.KEY_DURATION)) {
            getLong(MediaFormat.KEY_DURATION).takeIf { it > 0L }?.div(1_000L)
        } else {
            null
        },
        width = if (mimeType.startsWith("video/") && containsKey(MediaFormat.KEY_WIDTH)) {
            getInteger(MediaFormat.KEY_WIDTH).takeIf { it > 0 }
        } else {
            null
        },
        height = if (mimeType.startsWith("video/") && containsKey(MediaFormat.KEY_HEIGHT)) {
            getInteger(MediaFormat.KEY_HEIGHT).takeIf { it > 0 }
        } else {
            null
        },
        rotationDegrees = if (containsKey(MediaFormat.KEY_ROTATION)) getInteger(MediaFormat.KEY_ROTATION) else 0,
    )

    private data class ImageInfo(val mimeType: String, val width: Int, val height: Int)
    private data class TrackInfo(val video: Track? = null, val audio: Track? = null)
    private data class Track(
        val mimeType: String,
        val durationMs: Long?,
        val width: Int?,
        val height: Int?,
        val rotationDegrees: Int,
    )
}
