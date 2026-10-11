// SPDX-License-Identifier: GPL-3.0-or-later
package com.transversa.ffmpeg

/** Runtime diagnostics and in-process conversion for the embedded FFmpeg build. */
object FfmpegBridge {
    init {
        System.loadLibrary("transversa_ffmpeg")
    }

    fun runtimeInfo(): RuntimeInfo = RuntimeInfo(
        version = nativeVersion(),
        configuration = nativeConfiguration(),
        encoders = nativeEncoders().toSet(),
    )

    /** Convert the first image frame from [inputPath] to PNG at [outputPath]. */
    fun convertImageToPng(
        inputPath: String,
        outputPath: String,
        compressionLevel: Int = 3,
        progressListener: ConversionProgressListener = ConversionProgressListener {},
    ) {
        require(inputPath.isNotBlank()) { "Input path must not be blank." }
        require(outputPath.isNotBlank()) { "Output path must not be blank." }
        require(inputPath != outputPath) { "Input and output paths must differ." }
        require(compressionLevel in 0..9) { "PNG compression level must be between 0 and 9." }
        nativeConvertImageToPng(inputPath, outputPath, compressionLevel, progressListener)
    }

    /** Convert a staged local audio file to a 192 kb/s MP3 at [outputPath]. */
    fun convertAudioToMp3(
        inputPath: String,
        outputPath: String,
        bitrate: Int = 192_000,
        progressListener: ConversionProgressListener = ConversionProgressListener {},
    ) {
        require(inputPath.isNotBlank()) { "Input path must not be blank." }
        require(outputPath.isNotBlank()) { "Output path must not be blank." }
        require(inputPath != outputPath) { "Input and output paths must differ." }
        require(bitrate in 32_000..320_000) { "MP3 bitrate must be between 32 and 320 kb/s." }
        nativeConvertAudioToMp3(inputPath, outputPath, bitrate, progressListener)
    }

    /** Convert a staged local video file to MP4/H.264/AAC at [outputPath]. */
    fun convertVideoToMp4(
        inputPath: String,
        outputPath: String,
        videoBitrate: Int = 5_000_000,
        crf: Int = 23,
        audioBitrate: Int = 192_000,
        mediumPreset: Boolean = false,
        progressListener: ConversionProgressListener = ConversionProgressListener {},
    ) {
        require(inputPath.isNotBlank()) { "Input path must not be blank." }
        require(outputPath.isNotBlank()) { "Output path must not be blank." }
        require(inputPath != outputPath) { "Input and output paths must differ." }
        require(videoBitrate in 250_000..50_000_000) { "Video bitrate must be between 250 kb/s and 50 Mb/s." }
        require(crf in 0..51) { "H.264 CRF must be between 0 and 51." }
        require(audioBitrate in 32_000..320_000) { "AAC bitrate must be between 32 and 320 kb/s." }
        nativeConvertVideoToMp4(inputPath, outputPath, videoBitrate, crf, audioBitrate, mediumPreset, progressListener)
    }

    private external fun nativeVersion(): String
    private external fun nativeConfiguration(): String
    private external fun nativeEncoders(): Array<String>
    private external fun nativeConvertImageToPng(
        inputPath: String,
        outputPath: String,
        compressionLevel: Int,
        progressListener: ConversionProgressListener,
    )
    private external fun nativeConvertAudioToMp3(
        inputPath: String,
        outputPath: String,
        bitrate: Int,
        progressListener: ConversionProgressListener,
    )
    private external fun nativeConvertVideoToMp4(
        inputPath: String,
        outputPath: String,
        videoBitrate: Int,
        crf: Int,
        audioBitrate: Int,
        mediumPreset: Boolean,
        progressListener: ConversionProgressListener,
    )
}

fun interface ConversionProgressListener {
    fun onProgress(percent: Int)
}

class FfmpegConversionException(message: String) : RuntimeException(message)

data class RuntimeInfo(
    val version: String,
    val configuration: String,
    val encoders: Set<String>,
)
