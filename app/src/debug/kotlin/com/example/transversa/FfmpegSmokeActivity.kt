// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import android.app.Activity
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.transversa.ffmpeg.FfmpegBridge
import java.io.File

/** Debug-only device smoke check for the native image, audio, and video conversion APIs. */
class FfmpegSmokeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = TextView(this).apply { textSize = 18f; setPadding(32, 32, 32, 32); text = "Running FFmpeg checks…" }
        setContentView(report)

        Thread {
            val lines = mutableListOf("FFmpeg ${FfmpegBridge.runtimeInfo().version}")
            fun runCheck(name: String, check: () -> String) {
                try {
                    lines += "PASS  $name (${check()})"
                } catch (failure: Throwable) {
                    lines += "FAIL  $name: ${failure.javaClass.simpleName}: ${failure.message}"
                    Log.e(TAG, lines.last(), failure)
                }
            }
            runCheck("JPEG → PNG") {
                val image = convert("source.jpg", "image.png") { input, output ->
                    FfmpegBridge.convertImageToPng(input, output)
                }
                val bitmap = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(image.path, bitmap)
                check(bitmap.outMimeType == "image/png" && bitmap.outWidth == 64 && bitmap.outHeight == 48) {
                    "Image output was ${bitmap.outMimeType} ${bitmap.outWidth}x${bitmap.outHeight}"
                }
                "${bitmap.outWidth} × ${bitmap.outHeight}"
            }

            runCheck("WAV → MP3") {
                val audio = convert("source.wav", "audio.mp3") { input, output ->
                    FfmpegBridge.convertAudioToMp3(input, output)
                }
                check(hasTrack(audio, "audio/mpeg")) { "MP3 output has no audio/mpeg track" }
                "audio/mpeg track"
            }

            runCheck("MP4 → H.264/AAC MP4") {
                val video = convert("source.mp4", "video.mp4") { input, output ->
                    FfmpegBridge.convertVideoToMp4(input, output)
                }
                check(hasTrack(video, "video/avc")) { "MP4 output has no H.264 video track" }
                check(hasTrack(video, "audio/mp4a-latm")) { "MP4 output has no AAC audio track" }
                "H.264 video + AAC audio tracks"
            }
            Log.i(TAG, lines.joinToString("; "))
            runOnUiThread { report.text = lines.joinToString("\n\n") }
        }.start()
    }

    private fun convert(inputName: String, outputName: String, conversion: (String, String) -> Unit): File {
        val input = File(cacheDir, "smoke-$inputName")
        assets.open(inputName).use { source -> input.outputStream().use(source::copyTo) }
        val output = File(cacheDir, "smoke-$outputName").apply { delete() }
        conversion(input.path, output.path)
        check(output.isFile && output.length() > 0) { "No output created for $outputName" }
        return output
    }

    private fun hasTrack(file: File, expectedMime: String): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.path)
            (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) == expectedMime
            }
        } finally {
            extractor.release()
        }
    }

    private companion object {
        const val TAG = "FfmpegSmoke"
    }
}
