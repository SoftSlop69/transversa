// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.transversa.core.media.MediaDescriptor
import com.transversa.core.model.ConversionProfile
import com.transversa.core.model.EncoderPreference
import com.transversa.core.model.MediaKind
import com.transversa.core.presets.ConversionPreset
import org.json.JSONObject
import java.util.UUID

internal object ConversionJob {
    const val ACTION_SAVE = "com.example.transversa.action.SAVE_CONVERSION"
    const val ACTION_SHARE = "com.example.transversa.action.SHARE_CONVERSION"

    fun start(context: Context, media: MediaDescriptor, preset: ConversionPreset, action: String, destination: Uri? = null): String {
        val jobId = UUID.randomUUID().toString()
        val status = ConversionJobStatus(context)
        status.begin(jobId, action)
        val intent = Intent(context, MediaConversionService::class.java).apply {
            this.action = action
            data = media.uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(MediaConversionService.EXTRA_JOB_ID, jobId)
            putExtra(MediaConversionService.EXTRA_PRESET, preset.encode())
            destination?.let {
                putExtra(MediaConversionService.EXTRA_DESTINATION_URI, it.toString())
                // URI permission flags only cover URIs carried by data or ClipData.
                // The destination is an extra, so include it explicitly for the service.
                clipData = ClipData.newUri(context.contentResolver, "Transversa save destination", it)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (failure: RuntimeException) {
            status.finish(ConversionJobResult(jobId, action, false, null, null, null, failure.message))
            throw failure
        }
        return jobId
    }

    private fun ConversionPreset.encode(): String {
        val options = JSONObject()
        profile.options.forEach { (key, value) -> options.put(key, value) }
        val encodedProfile = JSONObject()
            .put("schemaVersion", profile.schemaVersion)
            .put("mediaKind", profile.mediaKind.name)
            .put("container", profile.container)
            .put("videoCodec", profile.videoCodec)
            .put("audioCodec", profile.audioCodec)
            .put("encoderPreference", profile.encoderPreference.name)
            .put("options", options)
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("mediaKind", mediaKind.name)
            .put("profile", encodedProfile)
            .put("isDefault", isDefault)
            .put("description", description)
            .toString()
    }

    fun decodePreset(json: String): ConversionPreset {
        val encoded = JSONObject(json)
        val profileJson = encoded.getJSONObject("profile")
        val optionsJson = profileJson.getJSONObject("options")
        val options = buildMap {
            val keys = optionsJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, optionsJson.getString(key))
            }
        }
        val mediaKind = MediaKind.valueOf(encoded.getString("mediaKind"))
        return ConversionPreset(
            id = encoded.getString("id"),
            name = encoded.getString("name"),
            mediaKind = mediaKind,
            profile = ConversionProfile(
                schemaVersion = profileJson.optInt("schemaVersion", ConversionProfile.CURRENT_SCHEMA_VERSION),
                mediaKind = MediaKind.valueOf(profileJson.getString("mediaKind")),
                container = profileJson.getString("container"),
                videoCodec = profileJson.optString("videoCodec").takeIf(String::isNotEmpty),
                audioCodec = profileJson.optString("audioCodec").takeIf(String::isNotEmpty),
                encoderPreference = EncoderPreference.valueOf(profileJson.optString("encoderPreference", EncoderPreference.AUTO.name)),
                options = options,
            ),
            isDefault = encoded.optBoolean("isDefault"),
            description = encoded.optString("description"),
        )
    }
}
