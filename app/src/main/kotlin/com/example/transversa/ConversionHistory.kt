// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import android.content.Context
import android.net.Uri
import com.transversa.core.model.MediaKind
import com.transversa.core.model.OutputAction
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ConversionHistoryEntry(
    val id: String,
    val sourceName: String,
    val outputName: String,
    val kind: MediaKind,
    val action: OutputAction,
    val createdAtMillis: Long,
    val thumbnailPath: String? = null,
    val outputUri: Uri? = null,
    val mimeType: String? = null,
)

/** Small, private on-device history of completed conversions. */
class ConversionHistory(context: Context) {
    private val preferences = context.getSharedPreferences("transversa_conversion_history", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun read(): List<ConversionHistoryEntry> = runCatching {
        val array = JSONArray(preferences.getString(KEY_ENTRIES, "[]"))
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                ConversionHistoryEntry(
                    id = item.getString("id"),
                    sourceName = item.getString("sourceName"),
                    outputName = item.getString("outputName"),
                    kind = MediaKind.valueOf(item.getString("kind")),
                    action = OutputAction.valueOf(item.getString("action")),
                    createdAtMillis = item.getLong("createdAtMillis"),
                    thumbnailPath = item.optString("thumbnailPath").takeIf(String::isNotBlank),
                    outputUri = item.optString("outputUri").takeIf(String::isNotBlank)?.let(Uri::parse),
                    mimeType = item.optString("mimeType").takeIf(String::isNotBlank),
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun add(
        sourceName: String,
        outputName: String,
        kind: MediaKind,
        action: OutputAction,
        thumbnail: File? = null,
        outputUri: Uri? = null,
        mimeType: String? = null,
    ) {
        if (!isEnabled()) {
            thumbnail?.delete()
            return
        }
        val item = ConversionHistoryEntry(
            id = UUID.randomUUID().toString(),
            sourceName = sourceName,
            outputName = outputName,
            kind = kind,
            action = action,
            createdAtMillis = System.currentTimeMillis(),
            thumbnailPath = thumbnail?.absolutePath,
            outputUri = outputUri,
            mimeType = mimeType,
        )
        write(listOf(item) + read())
    }

    fun delete(id: String) {
        val entries = read()
        entries.firstOrNull { it.id == id }?.thumbnailPath?.let(::File)?.delete()
        write(entries.filterNot { it.id == id })
    }

    fun clear() {
        read().mapNotNull { it.thumbnailPath }.forEach { File(it).delete() }
        preferences.edit().remove(KEY_ENTRIES).apply()
    }

    private fun write(entries: List<ConversionHistoryEntry>) {
        val array = JSONArray()
        val retained = entries.take(MAX_ENTRIES)
        entries.drop(MAX_ENTRIES).mapNotNull { it.thumbnailPath }.forEach { File(it).delete() }
        retained.forEach { entry ->
            array.put(JSONObject()
                .put("id", entry.id)
                .put("sourceName", entry.sourceName)
                .put("outputName", entry.outputName)
                .put("kind", entry.kind.name)
                .put("action", entry.action.name)
                .put("createdAtMillis", entry.createdAtMillis)
                .put("thumbnailPath", entry.thumbnailPath)
                .put("outputUri", entry.outputUri?.toString())
                .put("mimeType", entry.mimeType))
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private companion object {
        const val KEY_ENTRIES = "entries"
        const val KEY_ENABLED = "enabled"
        const val MAX_ENTRIES = 200
    }
}

