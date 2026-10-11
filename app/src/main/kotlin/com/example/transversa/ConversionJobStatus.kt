// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import android.content.Context
import android.net.Uri

internal data class ConversionJobResult(
    val jobId: String,
    val action: String,
    val succeeded: Boolean,
    val fileName: String?,
    val mimeType: String?,
    val resultUri: Uri?,
    val error: String?,
)

/** Durable handoff from the foreground conversion service back to the UI. */
internal class ConversionJobStatus(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun begin(jobId: String, action: String) {
        preferences.edit()
            .putString(KEY_ACTIVE_ID, jobId)
            .putString(KEY_ACTION, action)
            .putString(KEY_STATE, STATE_RUNNING)
            .remove(KEY_FILE_NAME)
            .remove(KEY_MIME_TYPE)
            .remove(KEY_RESULT_URI)
            .remove(KEY_ERROR)
            .putBoolean(KEY_HANDLED, false)
            .commit()
    }

    fun finish(result: ConversionJobResult) {
        preferences.edit()
            .putString(KEY_ACTIVE_ID, result.jobId)
            .putString(KEY_ACTION, result.action)
            .putString(KEY_STATE, if (result.succeeded) STATE_SUCCEEDED else STATE_FAILED)
            .putString(KEY_FILE_NAME, result.fileName)
            .putString(KEY_MIME_TYPE, result.mimeType)
            .putString(KEY_RESULT_URI, result.resultUri?.toString())
            .putString(KEY_ERROR, result.error)
            .putBoolean(KEY_HANDLED, false)
            .commit()
    }

    fun read(jobId: String): ConversionJobResult? {
        if (preferences.getString(KEY_ACTIVE_ID, null) != jobId) return null
        val state = preferences.getString(KEY_STATE, null)
        if (state != STATE_SUCCEEDED && state != STATE_FAILED) return null
        return ConversionJobResult(
            jobId = jobId,
            action = preferences.getString(KEY_ACTION, ConversionJob.ACTION_SAVE) ?: ConversionJob.ACTION_SAVE,
            succeeded = state == STATE_SUCCEEDED,
            fileName = preferences.getString(KEY_FILE_NAME, null),
            mimeType = preferences.getString(KEY_MIME_TYPE, null),
            resultUri = preferences.getString(KEY_RESULT_URI, null)?.let(Uri::parse),
            error = preferences.getString(KEY_ERROR, null),
        )
    }

    fun pendingJobId(): String? = preferences.getString(KEY_ACTIVE_ID, null)
        ?.takeUnless { preferences.getBoolean(KEY_HANDLED, false) }

    fun markHandled(jobId: String) {
        if (preferences.getString(KEY_ACTIVE_ID, null) == jobId) {
            preferences.edit().putBoolean(KEY_HANDLED, true).apply()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "transversa_conversion_job"
        const val KEY_ACTIVE_ID = "active_job_id"
        const val KEY_ACTION = "action"
        const val KEY_STATE = "state"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_MIME_TYPE = "mime_type"
        const val KEY_RESULT_URI = "result_uri"
        const val KEY_ERROR = "error"
        const val KEY_HANDLED = "handled"
        const val STATE_RUNNING = "running"
        const val STATE_SUCCEEDED = "succeeded"
        const val STATE_FAILED = "failed"
    }
}
