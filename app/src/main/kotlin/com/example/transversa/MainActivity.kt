// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.transversa.core.media.AndroidMediaInspector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val selectedUriTextState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        selectedUriTextState.value = savedInstanceState?.getString(STATE_SELECTED_URI)
            ?: intent.toMediaUri()?.also(::tryPersistReadPermission)?.toString()

        setContent {
            val context = LocalContext.current
            val inspector = remember(context) { AndroidMediaInspector(context) }
            val selectedUriText = selectedUriTextState.value
            var screenState by remember { mutableStateOf<MediaScreenState>(MediaScreenState.Empty) }

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
                        selectedUriTextState.value = it.toString()
                    }
                },
            )

            TransversaApp(
                mediaState = screenState,
                onSelectMedia = { mediaPicker.launch(arrayOf("video/*", "image/*", "audio/*")) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.toMediaUri()?.also(::tryPersistReadPermission)?.let { selectedUriTextState.value = it.toString() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTED_URI, selectedUriTextState.value)
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
        const val STATE_SELECTED_URI = "selected_media_uri"
    }
}
