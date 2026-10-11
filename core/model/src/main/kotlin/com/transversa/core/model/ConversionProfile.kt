// SPDX-License-Identifier: GPL-3.0-or-later
package com.transversa.core.model

data class ConversionProfile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val mediaKind: MediaKind,
    val container: String,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val encoderPreference: EncoderPreference = EncoderPreference.AUTO,
    val options: Map<String, String> = emptyMap(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        val DEFAULT_VIDEO = ConversionProfile(
            mediaKind = MediaKind.VIDEO,
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "aac",
        )
        val DEFAULT_IMAGE = ConversionProfile(
            mediaKind = MediaKind.IMAGE,
            container = "png",
        )
        val DEFAULT_AUDIO = ConversionProfile(
            mediaKind = MediaKind.AUDIO,
            container = "mp3",
            audioCodec = "mp3",
        )
    }
}

