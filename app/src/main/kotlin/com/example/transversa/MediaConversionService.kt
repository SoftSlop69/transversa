// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.FileProvider
import com.transversa.core.media.AndroidMediaInspector
import com.transversa.core.media.MediaDescriptor
import com.transversa.core.model.MediaKind
import com.transversa.core.model.OutputAction
import com.transversa.core.presets.ConversionPreset
import com.transversa.ffmpeg.ConversionProgressListener
import com.transversa.ffmpeg.FfmpegBridge
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class MediaConversionService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mediaUri = intent?.data
        val presetJson = intent?.getStringExtra(EXTRA_PRESET)
        val jobId = intent?.getStringExtra(EXTRA_JOB_ID)
        val action = intent?.action
        if (mediaUri == null || presetJson == null || jobId == null || action !in setOf(ConversionJob.ACTION_SAVE, ConversionJob.ACTION_SHARE)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val conversionAction = action ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        createNotificationChannels()
        val notification = progressNotification(0, getString(R.string.conversion_starting))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val requiredServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            val declaredServiceType = packageManager.getServiceInfo(
                ComponentName(this, MediaConversionService::class.java),
                0,
            ).foregroundServiceType
            check(declaredServiceType and requiredServiceType != 0) {
                "The required foreground service type is missing from the installed manifest."
            }
            // Pass the API 35 mediaProcessing type through the platform API;
            // older AndroidX ServiceCompat versions can drop this newer flag.
            startForeground(
                PROGRESS_NOTIFICATION_ID,
                notification,
                requiredServiceType,
            )
        } else {
            startForeground(PROGRESS_NOTIFICATION_ID, notification)
        }
        val destinationUri = intent.getStringExtra(EXTRA_DESTINATION_URI)?.let(Uri::parse)
        executor.execute {
            runConversion(startId, mediaUri, presetJson, conversionAction, destinationUri, jobId)
        }
        return START_REDELIVER_INTENT
    }

    override fun onTimeout(startId: Int, foregroundServiceType: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun runConversion(
        startId: Int,
        mediaUri: Uri,
        presetJson: String,
        action: String,
        destinationUri: Uri?,
        jobId: String,
    ) {
        var output: File? = null
        try {
            val preset = ConversionJob.decodePreset(presetJson)
            val media = AndroidMediaInspector(this).inspect(mediaUri)
            require(preset.mediaKind == media.kind) { "The selected preset does not match this media type." }
            if (action == ConversionJob.ACTION_SAVE) requireNotNull(destinationUri) { "The save destination is missing." }

            val spec = outputSpec(preset)
            val input = File.createTempFile("transversa-input-", ".media", cacheDir)
            val outputDirectory = File(cacheDir, "converted").apply {
                if (!exists() && !mkdirs()) error("Could not prepare private output storage.")
                check(isDirectory) { "Private output storage is not a directory." }
            }
            output = File(outputDirectory, "converted-${UUID.randomUUID().toString().takeLast(6)}.${spec.extension}")
            try {
                val source = contentResolver.openInputStream(media.uri)
                    ?: error("The selected media is no longer available.")
                var copiedBytes = 0L
                source.use { sourceStream ->
                    input.outputStream().use { destinationStream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = sourceStream.read(buffer)
                            if (count < 0) break
                            destinationStream.write(buffer, 0, count)
                            copiedBytes += count
                            media.sizeBytes?.takeIf { it > 0L }?.let { size ->
                                reportProgress((copiedBytes * 10L / size).toInt().coerceIn(0, 10), media.displayName)
                            }
                        }
                        destinationStream.flush()
                    }
                }
                val nativeProgress = ConversionProgressListener { percent ->
                    reportProgress(10 + percent.coerceIn(0, 100) * 89 / 100, media.displayName)
                }
                when (media.kind) {
                    MediaKind.IMAGE -> FfmpegBridge.convertImageToPng(
                        input.path,
                        output.path,
                        preset.profile.options["image.compression"]?.toIntOrNull() ?: 3,
                        nativeProgress,
                    )
                    MediaKind.AUDIO -> FfmpegBridge.convertAudioToMp3(
                        input.path,
                        output.path,
                        preset.profile.options["audio.bitrate"]?.toIntOrNull() ?: 192_000,
                        nativeProgress,
                    )
                    MediaKind.VIDEO -> FfmpegBridge.convertVideoToMp4(
                        input.path,
                        output.path,
                        videoBitrate = preset.profile.options["video.bitrate"]?.toIntOrNull() ?: 5_000_000,
                        crf = preset.profile.options["video.crf"]?.toIntOrNull() ?: 23,
                        audioBitrate = preset.profile.options["audio.bitrate"]?.toIntOrNull() ?: 192_000,
                        mediumPreset = preset.profile.options["video.preset"] == "medium",
                        progressListener = nativeProgress,
                    )
                    MediaKind.UNKNOWN -> error("This media type cannot be converted.")
                }
                check(output.isFile && output.length() > 0L) { "FFmpeg returned an empty output." }
                val outputName = outputFileName(media.displayName, spec.extension)
                val resultUri = if (action == ConversionJob.ACTION_SAVE) {
                    val target = contentResolver.openOutputStream(requireNotNull(destinationUri), "wt")
                        ?: error("The selected destination could not be opened.")
                    target.use { stream -> output.inputStream().use { it.copyTo(stream) } }
                    requireNotNull(destinationUri)
                } else {
                    FileProvider.getUriForFile(this, "$packageName.fileprovider", output)
                }

                PresetPreferences(this).recordSuccessfulConversion(preset)
                val conversionHistory = ConversionHistory(this)
                val thumbnail = if (conversionHistory.isEnabled()) {
                    HistoryThumbnailGenerator.create(this, media.uri, media.kind, preset.profile.container)
                } else {
                    null
                }
                conversionHistory.add(
                    media.displayName,
                    outputName,
                    media.kind,
                    if (action == ConversionJob.ACTION_SAVE) OutputAction.SAVE else OutputAction.SHARE,
                    thumbnail,
                )
                reportProgress(100, media.displayName)
                finishConversion(startId, jobId, action, outputName, spec.mimeType, resultUri, null)
                if (action == ConversionJob.ACTION_SAVE) output.delete()
            } finally {
                input.delete()
            }
        } catch (failure: Exception) {
            android.util.Log.e(TAG, "Background media conversion failed", failure)
            output?.delete()
            finishConversion(startId, jobId, action, null, null, null, failure.message)
        } catch (failure: LinkageError) {
            android.util.Log.e(TAG, "Background conversion native library could not be loaded", failure)
            output?.delete()
            finishConversion(startId, jobId, action, null, null, null, failure.message)
        }
    }

    private fun reportProgress(progress: Int, fileName: String) {
        notificationManager.notify(
            PROGRESS_NOTIFICATION_ID,
            progressNotification(progress, getString(R.string.conversion_progress_file, fileName)),
        )
        sendBroadcast(Intent(ACTION_PROGRESS).setPackage(packageName).putExtra(EXTRA_PROGRESS, progress))
    }

    private fun finishConversion(
        startId: Int,
        jobId: String,
        action: String,
        fileName: String?,
        mimeType: String?,
        resultUri: Uri?,
        error: String?,
    ) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        val success = error == null && resultUri != null && fileName != null && mimeType != null
        ConversionJobStatus(this).finish(
            ConversionJobResult(jobId, action, success, fileName, mimeType, resultUri, error),
        )
        val resultAction = if (success) ACTION_COMPLETED else ACTION_FAILED
        val resultIntent = Intent(resultAction)
            .setPackage(packageName)
            .putExtra(EXTRA_ACTION, action)
            .putExtra(EXTRA_FILE_NAME, fileName)
            .putExtra(EXTRA_MIME_TYPE, mimeType)
            .putExtra(EXTRA_RESULT_URI, resultUri?.toString())
            .putExtra(EXTRA_ERROR, error)
        if (isAppFocused()) {
            sendBroadcast(resultIntent)
        } else {
            postCompletionNotification(success, action, fileName, mimeType, resultUri)
        }
        stopSelf(startId)
    }

    private fun postCompletionNotification(success: Boolean, action: String, fileName: String?, mimeType: String?, uri: Uri?) {
        val optedIn = getSharedPreferences(NOTIFICATION_PREFERENCES, MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATIONS_ENABLED, false)
        if (!optedIn || !NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val title = when {
            !success -> getString(R.string.conversion_failed_title)
            action == ConversionJob.ACTION_SAVE -> getString(R.string.conversion_saved_title)
            else -> getString(R.string.conversion_ready_to_share_title)
        }
        val contentIntent = if (success && uri != null && fileName != null && mimeType != null) {
            val openIntent = Intent(this, ConversionResultActivity::class.java)
                .setAction(if (action == ConversionJob.ACTION_SAVE) ConversionResultActivity.ACTION_OPEN_SAVED else ConversionResultActivity.ACTION_SHARE_RESULT)
                .putExtra(EXTRA_FILE_NAME, fileName)
                .putExtra(EXTRA_MIME_TYPE, mimeType)
                .putExtra(EXTRA_RESULT_URI, uri.toString())
            PendingIntent.getActivity(
                this,
                RESULT_REQUEST_CODE,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else null
        val notification = NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_transversa_splash)
            .setContentTitle(title)
            .setContentText(fileName ?: getString(R.string.conversion_failed_message))
            .setStyle(NotificationCompat.BigTextStyle().bigText(fileName ?: errorTextForNotification()))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            notificationManager.notify(RESULT_NOTIFICATION_ID, notification)
        } catch (denied: SecurityException) {
            android.util.Log.w(TAG, "Completion notification permission is not granted", denied)
        }
    }

    private fun errorTextForNotification() = getString(R.string.conversion_failed_message)

    private fun progressNotification(progress: Int, text: String): Notification =
        NotificationCompat.Builder(this, PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_transversa_splash)
            .setContentTitle(getString(R.string.conversion_in_progress_title))
            .setContentText(text)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(PROGRESS_CHANNEL_ID, getString(R.string.conversion_progress_channel), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(RESULT_CHANNEL_ID, getString(R.string.conversion_result_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { setShowBadge(true) },
        )
    }

    private fun isAppFocused(): Boolean = getSharedPreferences(FOCUS_PREFERENCES, MODE_PRIVATE)
        .getBoolean(KEY_APP_FOCUSED, false)

    private fun outputSpec(preset: ConversionPreset): OutputSpec = when (preset.profile.container.lowercase()) {
        "png" -> OutputSpec("image/png", "png")
        "mp3" -> OutputSpec("audio/mpeg", "mp3")
        "mp4" -> OutputSpec("video/mp4", "mp4")
        else -> error("This preset's output format is not supported yet.")
    }

    private fun outputFileName(displayName: String, extension: String): String {
        val leafName = displayName.substringAfterLast('/').substringAfterLast('\\')
        val baseName = leafName.substringBeforeLast('.', leafName)
            .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
            .trim().trim('.').ifBlank { "Transversa" }
        return "$baseName.$extension"
    }

    private data class OutputSpec(val mimeType: String, val extension: String)

    companion object {
        private const val NOTIFICATION_PREFERENCES = "transversa_preferences"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val ACTION_PROGRESS = "com.example.transversa.action.CONVERSION_PROGRESS"
        const val ACTION_COMPLETED = "com.example.transversa.action.CONVERSION_COMPLETED"
        const val ACTION_FAILED = "com.example.transversa.action.CONVERSION_FAILED"
        const val EXTRA_PRESET = "preset_json"
        const val EXTRA_JOB_ID = "conversion_job_id"
        const val EXTRA_DESTINATION_URI = "destination_uri"
        const val EXTRA_ACTION = "conversion_action"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_MIME_TYPE = "mime_type"
        const val EXTRA_RESULT_URI = "result_uri"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_ERROR = "error"
        const val PROGRESS_CHANNEL_ID = "media_conversion_progress"
        const val RESULT_CHANNEL_ID = "media_conversion_results"
        const val PROGRESS_NOTIFICATION_ID = 7001
        const val RESULT_NOTIFICATION_ID = 7002
        private const val RESULT_REQUEST_CODE = 7003
        private const val FOCUS_PREFERENCES = "transversa_runtime_state"
        private const val KEY_APP_FOCUSED = "app_in_foreground"
        private const val TAG = "TransversaConversion"
    }
}

class ConversionResultActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(MediaConversionService.EXTRA_RESULT_URI)?.let(Uri::parse)
        if (uri != null) {
            when (intent.action) {
                ACTION_SHARE_RESULT -> {
                    val mimeType = intent.getStringExtra(MediaConversionService.EXTRA_MIME_TYPE) ?: "application/octet-stream"
                    val fileName = intent.getStringExtra(MediaConversionService.EXTRA_FILE_NAME) ?: getString(R.string.app_name)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = android.content.ClipData.newUri(contentResolver, fileName, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(sendIntent, getString(R.string.share_chooser_title)))
                }
                ACTION_OPEN_SAVED -> {
                    val filesIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { startActivity(filesIntent) }
                }
            }
        }
        finish()
    }

    companion object {
        const val ACTION_SHARE_RESULT = "com.example.transversa.action.SHARE_RESULT"
        const val ACTION_OPEN_SAVED = "com.example.transversa.action.OPEN_SAVED"
    }
}
