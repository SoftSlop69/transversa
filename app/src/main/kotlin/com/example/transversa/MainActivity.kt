// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.FileProvider
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.transversa.core.media.AndroidMediaInspector
import com.transversa.core.media.MediaDescriptor
import com.transversa.core.model.MediaKind
import com.transversa.core.model.OutputAction
import com.transversa.core.presets.BuiltInPresets
import com.transversa.core.presets.ConversionPreset
import com.transversa.ffmpeg.FfmpegBridge
import com.transversa.ffmpeg.ConversionProgressListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val selectedUriTextState = mutableStateOf<String?>(null)
    private val isSharedInputState = mutableStateOf(false)
    private val notificationsEnabledState = mutableStateOf(false)
    private var cachedOutput: CachedOutput? = null
    private var pendingSaveMedia: MediaDescriptor? = null
    private var pendingSavePreset: ConversionPreset? = null
    private val presetPreferences by lazy { PresetPreferences(this) }
    private val conversionHistory by lazy { ConversionHistory(this) }
    private lateinit var donationBilling: DonationBilling

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        donationBilling = DonationBilling(this)
        HistoryThumbnailGenerator.refreshExistingAudioArtwork(this, conversionHistory.read())
        selectedUriTextState.value = savedInstanceState?.getString(STATE_SELECTED_URI)
            ?: intent.toMediaUri()?.also(::tryPersistReadPermission)?.toString()
        isSharedInputState.value = savedInstanceState?.getBoolean(STATE_IS_SHARED_INPUT)
            ?: (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE)

        setContent {
            val context = LocalContext.current
            val conversionJobStatus = remember(context) { ConversionJobStatus(context) }
            var activeConversionJobId by remember(context) {
                mutableStateOf(conversionJobStatus.pendingJobId())
            }
            val appearancePreferences = remember(context) {
                context.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            }
            var themeMode by remember(appearancePreferences) {
                mutableStateOf(AppThemeMode.fromPreference(appearancePreferences.getString(KEY_THEME_MODE, null)))
            }
            val inspector = remember(context) { AndroidMediaInspector(context) }
            val selectedUriText = selectedUriTextState.value
            var screenState by remember { mutableStateOf<MediaScreenState>(MediaScreenState.Empty) }
            var outputState by remember(selectedUriText) { mutableStateOf(MediaOutputState()) }
            var presetRevision by remember { mutableIntStateOf(0) }
            var history by remember { mutableStateOf(conversionHistory.read()) }
            var historyEnabled by remember { mutableStateOf(conversionHistory.isEnabled()) }
            val donationState = donationBilling.state.value
            val mediaKind = (screenState as? MediaScreenState.Ready)?.media?.kind
            var activePreset by remember(mediaKind) {
                mutableStateOf(mediaKind?.let(presetPreferences::current))
            }
            val recentPresets = remember(mediaKind, presetRevision) {
                mediaKind?.let(presetPreferences::recent).orEmpty()
            }
            val allPresets = remember(presetRevision) {
                MediaKind.entries.filterNot { it == MediaKind.UNKNOWN }.flatMap(presetPreferences::all)
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                appearancePreferences.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, granted).apply()
                refreshNotificationsEnabled()
                if (!granted) Toast.makeText(this@MainActivity, R.string.notifications_disabled, Toast.LENGTH_LONG).show()
            }

            DisposableEffect(Unit) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context, intent: Intent) {
                        when (intent.action) {
                            MediaConversionService.ACTION_PROGRESS -> {
                                outputState = outputState.copy(
                                    isBusy = true,
                                    progressPercent = intent.getIntExtra(MediaConversionService.EXTRA_PROGRESS, 0),
                                )
                            }
                        }
                    }
                }
                val filter = IntentFilter().apply {
                    addAction(MediaConversionService.ACTION_PROGRESS)
                }
                ContextCompat.registerReceiver(
                    this@MainActivity,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                onDispose { unregisterReceiver(receiver) }
            }

            LaunchedEffect(activeConversionJobId) {
                val jobId = activeConversionJobId ?: return@LaunchedEffect
                while (true) {
                    val result = withContext(Dispatchers.IO) { conversionJobStatus.read(jobId) }
                    if (result != null) {
                        conversionJobStatus.markHandled(jobId)
                        activeConversionJobId = null
                        if (result.succeeded) {
                            history = conversionHistory.read()
                            presetRevision++
                            outputState = if (result.action == ConversionJob.ACTION_SAVE) {
                                MediaOutputState(
                                    savedFileName = result.fileName.orEmpty(),
                                    savedFileUri = result.resultUri,
                                )
                            } else {
                                MediaOutputState(message = getString(R.string.share_sheet_opened))
                            }
                            if (result.action == ConversionJob.ACTION_SHARE && result.resultUri != null) {
                                openShareChooser(
                                    result.resultUri,
                                    result.mimeType ?: "application/octet-stream",
                                    result.fileName.orEmpty(),
                                )
                            }
                        } else {
                            outputState = MediaOutputState(
                                message = getString(R.string.output_action_failed),
                                hasError = true,
                            )
                        }
                        break
                    }
                    delay(250)
                }
            }

            LaunchedEffect(selectedUriText) {
                val uri = selectedUriText?.let(Uri::parse)
                if (uri == null) {
                    screenState = MediaScreenState.Empty
                } else {
                    screenState = MediaScreenState.Inspecting(uri)
                    screenState = try {
                        val media = withContext(Dispatchers.IO) { inspector.inspect(uri) }
                        MediaScreenState.Ready(media)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (permission: SecurityException) {
                        MediaScreenState.Failed(uri, permissionRevoked = true)
                    } catch (_: Exception) {
                        MediaScreenState.Failed(uri, permissionRevoked = false)
                    }
                }
            }

            val mediaPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
                onResult = { uri ->
                    uri?.let {
                        tryPersistReadPermission(it)
                        isSharedInputState.value = false
                        selectedUriTextState.value = it.toString()
                    }
                },
            )

            val saveDocumentPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
                onResult = { result ->
                    val media = pendingSaveMedia
                    val preset = pendingSavePreset
                    pendingSaveMedia = null
                    pendingSavePreset = null
                    val destination = result.data?.data.takeIf { result.resultCode == RESULT_OK }
                    if (media != null && preset != null && destination != null) {
                        rememberLastSaveLocation(destination, result.data?.flags ?: 0)
                        outputState = MediaOutputState(isBusy = true)
                        runCatching {
                            activeConversionJobId = ConversionJob.start(this@MainActivity, media, preset, ConversionJob.ACTION_SAVE, destination)
                        }.onFailure { failure ->
                            android.util.Log.e(TAG, "Could not start background save", failure)
                            outputState = MediaOutputState(message = getString(R.string.output_action_failed), hasError = true)
                        }
                    } else {
                        outputState = MediaOutputState()
                    }
                },
            )

            TransversaApp(
                themeMode = themeMode,
                onThemeModeChange = { selectedMode ->
                    themeMode = selectedMode
                    appearancePreferences.edit().putString(KEY_THEME_MODE, selectedMode.name).apply()
                },
                mediaState = screenState,
                outputState = outputState,
                activePreset = activePreset,
                isSharedInput = isSharedInputState.value,
                recentPresets = recentPresets,
                allPresets = allPresets,
                history = history,
                historyEnabled = historyEnabled,
                onHistoryEnabledChange = { enabled ->
                    conversionHistory.setEnabled(enabled)
                    historyEnabled = enabled
                },
                donationState = donationState,
                notificationsEnabled = notificationsEnabledState.value,
                onNotificationsEnabledChange = { enabled ->
                    if (!enabled) {
                        appearancePreferences.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, false).apply()
                        refreshNotificationsEnabled()
                    } else if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else if (!NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()) {
                        appearancePreferences.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, true).apply()
                        openNotificationSettings()
                    } else {
                        appearancePreferences.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, true).apply()
                        refreshNotificationsEnabled()
                    }
                },
                onSelectMedia = { mediaPicker.launch(arrayOf("video/*", "image/*", "audio/*")) },
                onClearMedia = {
                    selectedUriTextState.value = null
                    isSharedInputState.value = false
                    outputState = MediaOutputState()
                    cachedOutput = null
                },
                onPresetSelected = { preset ->
                    activePreset = preset
                },
                onCreatePreset = { name, kind ->
                    val source = activePreset?.takeIf { it.mediaKind == kind }
                        ?: allPresets.firstOrNull { it.mediaKind == kind && it.isDefault }
                        ?: BuiltInPresets.defaultFor(kind)
                    source?.let {
                        val preset = presetPreferences.createCustom(name, it)
                        activePreset = preset
                        presetRevision++
                        preset
                    }
                },
                onDeletePreset = { preset ->
                    presetPreferences.delete(preset)
                    activePreset = mediaKind?.let(presetPreferences::current)
                    presetRevision++
                },
                onSavePreset = { preset ->
                    presetPreferences.save(preset)
                    activePreset = preset
                    presetRevision++
                },
                onSaveMedia = { media, preset ->
                    activePreset = preset
                    val spec = outputSpec(preset)
                    pendingSaveMedia = media
                    pendingSavePreset = preset
                    outputState = MediaOutputState(message = getString(R.string.choose_save_destination))
                    val createDocument = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = spec.mimeType
                        putExtra(Intent.EXTRA_TITLE, outputFileName(media.displayName, spec.extension))
                        presetPreferences.lastSaveLocation()?.let { initialUri ->
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                        }
                    }
                    saveDocumentPicker.launch(createDocument)
                },
                onOpenSavedFile = { savedUri ->
                    openSavedFileLocation(savedUri)
                },
                onShareMedia = { media, preset ->
                    activePreset = preset
                    presetRevision++
                    outputState = MediaOutputState(isBusy = true)
                    runCatching {
                        activeConversionJobId = ConversionJob.start(this@MainActivity, media, preset, ConversionJob.ACTION_SHARE)
                    }.onFailure { failure ->
                        android.util.Log.e(TAG, "Could not start background share", failure)
                        outputState = MediaOutputState(message = getString(R.string.output_action_failed), hasError = true)
                    }
                },
                onDeleteHistoryEntry = { id ->
                    conversionHistory.delete(id)
                    history = conversionHistory.read()
                },
                onClearHistory = {
                    conversionHistory.clear()
                    history = emptyList()
                },
                onShareApp = {
                    val storeUrl = "https://play.google.com/store/apps/details?id=$packageName"
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_text, storeUrl))
                    }
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app_chooser_title)))
                },
                onDonate = { productId -> donationBilling.launchPurchase(this@MainActivity, productId) },
                onExit = { finish() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.toMediaUri()?.also(::tryPersistReadPermission)?.let {
            isSharedInputState.value = intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE
            selectedUriTextState.value = it.toString()
        }
    }

    override fun onResume() {
        super.onResume()
        getSharedPreferences(FOCUS_PREFERENCES, MODE_PRIVATE).edit().putBoolean(KEY_APP_FOCUSED, true).apply()
        NotificationManagerCompat.from(this).cancel(MediaConversionService.RESULT_NOTIFICATION_ID)
        refreshNotificationsEnabled()
    }

    private fun refreshNotificationsEnabled() {
        val explicitlyEnabled = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATIONS_ENABLED, false)
        notificationsEnabledState.value = explicitlyEnabled && NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun openNotificationSettings() {
        val notificationSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        val fallbackSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        runCatching { startActivity(notificationSettings) }
            .onFailure { runCatching { startActivity(fallbackSettings) } }
    }

    override fun onPause() {
        getSharedPreferences(FOCUS_PREFERENCES, MODE_PRIVATE).edit().putBoolean(KEY_APP_FOCUSED, false).apply()
        super.onPause()
    }

    override fun onDestroy() {
        if (::donationBilling.isInitialized) donationBilling.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTED_URI, selectedUriTextState.value)
        outState.putBoolean(STATE_IS_SHARED_INPUT, isSharedInputState.value)
        super.onSaveInstanceState(outState)
    }

    private fun tryPersistReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Shares commonly grant temporary access only; preview them for this activity session.
        } catch (_: IllegalArgumentException) {
            // Some content providers do not offer persistable document grants.
        }
    }

    private fun rememberLastSaveLocation(destination: Uri, resultFlags: Int) {
        val readWriteFlags = resultFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (resultFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0 && readWriteFlags != 0) {
            try {
                contentResolver.takePersistableUriPermission(destination, readWriteFlags)
            } catch (_: SecurityException) {
                // Some document providers grant only temporary access to created files.
            }
        }

        // Documents providers use provider-specific parent IDs. The SAF initial-URI extra
        // accepts the prior document URI and reopens its containing directory where possible.
        presetPreferences.rememberSaveLocation(destination)
    }

    private fun openSavedFileLocation(savedUri: Uri) {
        val filesIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(savedUri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(filesIntent)
    }

    private fun openShareChooser(uri: Uri, mimeType: String, fileName: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(contentResolver, fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, getString(R.string.share_chooser_title)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(chooser)
    }

    private fun convertOrReuse(
        media: MediaDescriptor,
        preset: ConversionPreset,
        onProgress: (Int) -> Unit = {},
    ): CachedOutput {
        val sourceKey = media.uri.toString()
        cachedOutput?.takeIf {
            it.sourceKey == sourceKey && it.presetId == preset.id && it.file.isFile && it.file.length() > 0L
        }?.let {
            onProgress(100)
            return it
        }

        require(preset.mediaKind == media.kind) { "The selected preset does not match this media type." }
        val spec = outputSpec(preset)
        val input = File.createTempFile("transversa-input-", ".media", cacheDir)
        val outputDirectory = File(cacheDir, "converted").apply {
            if (!exists() && !mkdirs()) error("Could not prepare private output storage.")
            check(isDirectory) { "Private output storage is not a directory." }
        }
        val output = File(outputDirectory, "converted-${UUID.randomUUID().toString().takeLast(6)}.${spec.extension}")
        try {
            val source = contentResolver.openInputStream(media.uri)
                ?: error("The selected media is no longer available.")
            onProgress(0)
            source.use { sourceStream ->
                input.outputStream().use { destinationStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copiedBytes = 0L
                    while (true) {
                        val count = sourceStream.read(buffer)
                        if (count < 0) break
                        destinationStream.write(buffer, 0, count)
                        copiedBytes += count
                        val size = media.sizeBytes
                        if (size != null && size > 0L) {
                            onProgress(((copiedBytes * 10L / size).toInt()).coerceIn(0, 10))
                        }
                    }
                    destinationStream.flush()
                }
            }
            onProgress(10)
            val nativeProgress = ConversionProgressListener { percent ->
                onProgress(10 + (percent.coerceIn(0, 100) * 89 / 100))
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
            onProgress(100)
            val completed = CachedOutput(
                sourceKey = sourceKey,
                presetId = preset.id,
                file = output,
                mimeType = spec.mimeType,
                fileName = outputFileName(media.displayName, spec.extension),
            )
            cachedOutput?.file?.delete()
            cachedOutput = completed
            return completed
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        } finally {
            input.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent?.toMediaUri(): Uri? {
        if (this == null) return null
        val uri = when (action) {
            Intent.ACTION_VIEW -> data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        } ?: clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        return uri
    }

    private companion object {
        const val PREFERENCES_NAME = "transversa_preferences"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val STATE_SELECTED_URI = "selected_media_uri"
        const val STATE_IS_SHARED_INPUT = "is_shared_input"
        const val TAG = "TransversaOutput"
        const val FOCUS_PREFERENCES = "transversa_runtime_state"
        const val KEY_APP_FOCUSED = "app_in_foreground"
    }

    private data class CachedOutput(
        val sourceKey: String,
        val presetId: String,
        val file: File,
        val mimeType: String,
        val fileName: String,
    )
}

private data class OutputSpec(val mimeType: String, val extension: String)

private fun outputSpec(preset: ConversionPreset): OutputSpec = when (preset.profile.container.lowercase()) {
    "png" -> OutputSpec("image/png", "png")
    "mp3" -> OutputSpec("audio/mpeg", "mp3")
    "mp4" -> OutputSpec("video/mp4", "mp4")
    else -> error("This preset's output format is not supported yet.")
}

private fun outputFileName(displayName: String, extension: String): String {
    val leafName = displayName
        .substringAfterLast('/')
        .substringAfterLast('\\')
    val baseName = leafName
        .substringBeforeLast('.', leafName)
        .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
        .trim()
        .trim('.')
        .ifBlank { "Transversa" }
    return "$baseName.$extension"
}
