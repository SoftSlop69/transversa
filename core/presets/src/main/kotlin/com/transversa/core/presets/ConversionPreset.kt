// SPDX-License-Identifier: GPL-3.0-or-later
package com.transversa.core.presets

import com.transversa.core.model.ConversionProfile
import com.transversa.core.model.MediaKind

/** A built-in conversion choice with settings understood by the native bridge. */
data class ConversionPreset(
    val id: String,
    val name: String,
    val mediaKind: MediaKind,
    val profile: ConversionProfile,
    val isDefault: Boolean = false,
    val description: String,
)

object BuiltInPresets {
    fun forMediaKind(mediaKind: MediaKind): List<ConversionPreset> = when (mediaKind) {
        MediaKind.VIDEO -> listOf(
            preset("video", "Simple", mediaKind, ConversionProfile.DEFAULT_VIDEO, isDefault = true,
                description = "Balanced MP4 video with H.264 and AAC.",),
            preset("video", "Smaller file", mediaKind, ConversionProfile.DEFAULT_VIDEO.copy(
                options = mapOf("video.crf" to "29", "video.preset" to "veryfast", "video.bitrate" to "2000000", "audio.bitrate" to "128000"),
            ), description = "Uses a lower video and audio bitrate to reduce file size."),
            preset("video", "Higher quality", mediaKind, ConversionProfile.DEFAULT_VIDEO.copy(
                options = mapOf("video.crf" to "18", "video.preset" to "medium", "video.bitrate" to "8000000", "audio.bitrate" to "256000"),
            ), description = "Uses a slower H.264 preset and higher bitrates."),
        )
        MediaKind.AUDIO -> listOf(
            preset("audio", "Simple", mediaKind, ConversionProfile.DEFAULT_AUDIO, isDefault = true,
                description = "Balanced MP3 audio at 192 kb/s.",),
            preset("audio", "Smaller file", mediaKind, ConversionProfile.DEFAULT_AUDIO.copy(
                options = mapOf("audio.bitrate" to "128000"),
            ), description = "Creates a smaller MP3 at 128 kb/s."),
            preset("audio", "Higher quality", mediaKind, ConversionProfile.DEFAULT_AUDIO.copy(
                options = mapOf("audio.bitrate" to "320000"),
            ), description = "Creates a higher bitrate MP3 at 320 kb/s."),
        )
        MediaKind.IMAGE -> listOf(
            preset("image", "Simple", mediaKind, ConversionProfile.DEFAULT_IMAGE, isDefault = true,
                description = "Lossless PNG image.",),
            preset("image", "Smaller PNG", mediaKind, ConversionProfile.DEFAULT_IMAGE.copy(
                options = mapOf("image.compression" to "9"),
            ), description = "Uses stronger lossless PNG compression; conversion may take longer."),
        )
        MediaKind.UNKNOWN -> emptyList()
    }

    fun defaultFor(mediaKind: MediaKind): ConversionPreset? =
        forMediaKind(mediaKind).firstOrNull { it.isDefault }

    private fun preset(
        group: String,
        name: String,
        mediaKind: MediaKind,
        profile: ConversionProfile,
        isDefault: Boolean = false,
        description: String,
    ) = ConversionPreset(
        id = "$group.${name.lowercase().replace(' ', '-')}",
        name = name,
        mediaKind = mediaKind,
        profile = profile,
        isDefault = isDefault,
        description = description,
    )
}
