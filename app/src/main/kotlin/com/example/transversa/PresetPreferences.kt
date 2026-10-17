// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import android.content.Context
import android.net.Uri
import com.transversa.core.model.ConversionProfile
import com.transversa.core.model.EncoderPreference
import com.transversa.core.model.MediaKind
import com.transversa.core.presets.BuiltInPresets
import com.transversa.core.presets.ConversionPreset
import org.json.JSONObject
import java.util.UUID

/** Android persistence adapter for preset catalogs, recent selections, and the SAF destination. */
class PresetPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun current(mediaKind: MediaKind): ConversionPreset? {
        val selectedId = preferences.getString(selectedKey(mediaKind), null)
        return all(mediaKind).firstOrNull { it.id == selectedId }
            ?: BuiltInPresets.defaultFor(mediaKind)
    }

    fun recent(mediaKind: MediaKind): List<ConversionPreset> {
        val catalog = all(mediaKind)
        val currentId = current(mediaKind)?.id
        return preferences.getString(recentKey(mediaKind), null)
            .orEmpty()
            .split(RECENT_DELIMITER)
            .asSequence()
            .filter { it.isNotBlank() && it != currentId }
            .distinct()
            .mapNotNull { id -> catalog.firstOrNull { it.id == id } }
            .take(MAX_RECENT_PRESETS)
            .toList()
    }

    fun all(mediaKind: MediaKind): List<ConversionPreset> {
        val deletedBuiltIns = preferences.getString(deletedKey(mediaKind), null)
            .orEmpty()
            .split(RECENT_DELIMITER)
            .filter(String::isNotBlank)
            .toSet()
        return BuiltInPresets.forMediaKind(mediaKind)
            .filterNot { it.id in deletedBuiltIns }
            .map { builtin -> readPresetOverrides().firstOrNull { it.id == builtin.id } ?: builtin }
            .plus(readCustomPresets(mediaKind))
    }

    fun save(preset: ConversionPreset) {
        val editor = preferences.edit()
        if (preset.id.startsWith(CUSTOM_ID_PREFIX)) {
            val updated = readAllCustomPresets().map { if (it.id == preset.id) preset else it }
            writeCustomPresets(updated, editor)
        } else {
            val updated = readPresetOverrides().filterNot { it.id == preset.id } + preset
            writePresetOverrides(updated, editor)
        }
        editor.apply()
    }

    fun createCustom(name: String, basedOn: ConversionPreset): ConversionPreset {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Preset name cannot be empty." }
        require(all(basedOn.mediaKind).none { it.name.equals(normalizedName, ignoreCase = true) }) {
            "A preset with that name already exists."
        }
        val preset = ConversionPreset(
            id = "custom-${UUID.randomUUID()}",
            name = normalizedName,
            mediaKind = basedOn.mediaKind,
            profile = basedOn.profile,
            description = "Custom preset based on ${basedOn.name}.",
        )
        val presets = readAllCustomPresets().toMutableList().apply { add(preset) }
        preferences.edit().also { editor ->
            writeCustomPresets(presets, editor)
            editor.apply()
        }
        return preset
    }

    fun delete(preset: ConversionPreset) {
        if (preset.isDefault) return

        val editor = preferences.edit()
        if (preset.id.startsWith(CUSTOM_ID_PREFIX)) {
            writeCustomPresets(readAllCustomPresets().filterNot { it.id == preset.id }, editor)
        } else {
            val deleted = preferences.getString(deletedKey(preset.mediaKind), null)
                .orEmpty()
                .split(RECENT_DELIMITER)
                .filter(String::isNotBlank)
                .plus(preset.id)
                .distinct()
            editor.putString(deletedKey(preset.mediaKind), deleted.joinToString(RECENT_DELIMITER))
        }

        if (preferences.getString(selectedKey(preset.mediaKind), null) == preset.id) {
            BuiltInPresets.defaultFor(preset.mediaKind)?.let { editor.putString(selectedKey(preset.mediaKind), it.id) }
        }
        val recent = preferences.getString(recentKey(preset.mediaKind), null)
            .orEmpty()
            .split(RECENT_DELIMITER)
            .filter { it.isNotBlank() && it != preset.id }
        editor.putString(recentKey(preset.mediaKind), recent.joinToString(RECENT_DELIMITER))
        editor.apply()
    }

    /** Persist selection and update recency only after an output conversion succeeds. */
    fun recordSuccessfulConversion(preset: ConversionPreset) {
        val previousSelection = current(preset.mediaKind)?.id
        val previousRecents = preferences.getString(recentKey(preset.mediaKind), null)
            .orEmpty()
            .split(RECENT_DELIMITER)
            .filter { it.isNotBlank() && it != preset.id }
        val recentIds = (listOfNotNull(previousSelection?.takeIf { it != preset.id }) + previousRecents)
            .distinct()
            .take(MAX_RECENT_PRESETS)
        preferences.edit()
            .putString(selectedKey(preset.mediaKind), preset.id)
            .putString(recentKey(preset.mediaKind), recentIds.joinToString(RECENT_DELIMITER))
            .apply()
    }

    fun lastSaveLocation(): Uri? = preferences.getString(KEY_LAST_SAVE_LOCATION, null)
        ?.let(Uri::parse)

    fun rememberSaveLocation(uri: Uri?) {
        preferences.edit().putString(KEY_LAST_SAVE_LOCATION, uri?.toString()).apply()
    }

    private fun selectedKey(mediaKind: MediaKind) = "${KEY_SELECTED_PRESET_PREFIX}${mediaKind.name}"
    private fun recentKey(mediaKind: MediaKind) = "${KEY_RECENT_PRESETS_PREFIX}${mediaKind.name}"
    private fun deletedKey(mediaKind: MediaKind) = "${KEY_DELETED_PRESETS_PREFIX}${mediaKind.name}"

    private fun readCustomPresets(mediaKind: MediaKind): List<ConversionPreset> =
        readAllCustomPresets().filter { it.mediaKind == mediaKind }

    private fun readAllCustomPresets(): List<ConversionPreset> {
        return readPresetList(KEY_CUSTOM_PRESETS)
    }

    private fun readPresetOverrides(): List<ConversionPreset> = readPresetList(KEY_PRESET_OVERRIDES)

    private fun readPresetList(key: String): List<ConversionPreset> {
        val serialized = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = org.json.JSONArray(serialized)
            (0 until array.length()).mapNotNull { index ->
                runCatching { array.getJSONObject(index).toPreset() }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun writeCustomPresets(presets: List<ConversionPreset>, editor: android.content.SharedPreferences.Editor) {
        writePresetList(KEY_CUSTOM_PRESETS, presets, editor)
    }

    private fun writePresetOverrides(presets: List<ConversionPreset>, editor: android.content.SharedPreferences.Editor) {
        writePresetList(KEY_PRESET_OVERRIDES, presets, editor)
    }

    private fun writePresetList(
        key: String,
        presets: List<ConversionPreset>,
        editor: android.content.SharedPreferences.Editor,
    ) {
        val array = org.json.JSONArray()
        presets.forEach { preset -> array.put(preset.toJson()) }
        editor.putString(key, array.toString())
    }

    private fun ConversionPreset.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("mediaKind", mediaKind.name)
        .put("description", description)
        .put("isDefault", isDefault)
        .put("profile", JSONObject()
            .put("schemaVersion", profile.schemaVersion)
            .put("mediaKind", profile.mediaKind.name)
            .put("container", profile.container)
            .put("videoCodec", profile.videoCodec)
            .put("audioCodec", profile.audioCodec)
            .put("encoderPreference", profile.encoderPreference.name)
            .put("options", JSONObject(profile.options)))

    private fun JSONObject.toPreset(): ConversionPreset {
        val profileObject = getJSONObject("profile")
        val profileKind = MediaKind.valueOf(profileObject.getString("mediaKind"))
        val optionsObject = profileObject.optJSONObject("options") ?: JSONObject()
        val options = buildMap {
            val keys = optionsObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, optionsObject.getString(key))
            }
        }
        return ConversionPreset(
            id = getString("id"),
            name = getString("name"),
            mediaKind = MediaKind.valueOf(getString("mediaKind")),
            profile = ConversionProfile(
                schemaVersion = profileObject.optInt("schemaVersion", ConversionProfile.CURRENT_SCHEMA_VERSION),
                mediaKind = profileKind,
                container = profileObject.getString("container"),
                videoCodec = profileObject.optString("videoCodec").takeIf(String::isNotBlank),
                audioCodec = profileObject.optString("audioCodec").takeIf(String::isNotBlank),
                encoderPreference = runCatching {
                    EncoderPreference.valueOf(profileObject.optString("encoderPreference", EncoderPreference.AUTO.name))
                }.getOrDefault(EncoderPreference.AUTO),
                options = options,
            ),
            isDefault = optBoolean("isDefault", false),
            description = getString("description"),
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "transversa_user_preferences"
        const val KEY_SELECTED_PRESET_PREFIX = "selected_preset_"
        const val KEY_RECENT_PRESETS_PREFIX = "recent_presets_"
        const val KEY_DELETED_PRESETS_PREFIX = "deleted_presets_"
        const val KEY_CUSTOM_PRESETS = "custom_presets"
        const val KEY_PRESET_OVERRIDES = "preset_overrides"
        const val KEY_LAST_SAVE_LOCATION = "last_save_location"
        const val CUSTOM_ID_PREFIX = "custom-"
        const val MAX_RECENT_PRESETS = 4
        const val RECENT_DELIMITER = "|"
    }
}

