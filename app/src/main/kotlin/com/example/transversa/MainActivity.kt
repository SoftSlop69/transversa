// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
            val mediaPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
                onResult = { uri ->
                    selectedName = uri?.let { selectedUri ->
                        context.contentResolver.query(
                            selectedUri,
                            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null,
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                cursor.getString(0)
                            } else {
                                null
                            }
                        } ?: selectedUri.lastPathSegment
                    }
                },
            )

            TransversaApp(
                selectedName = selectedName,
                onSelectMedia = {
                    mediaPicker.launch(arrayOf("video/*", "image/*", "audio/*"))
                },
            )
        }
    }
}

