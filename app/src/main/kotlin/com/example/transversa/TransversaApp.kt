// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun TransversaApp(
    mediaState: MediaScreenState,
    onSelectMedia: () -> Unit,
) {
    TransversaTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { contentPadding ->
            HomeScreen(
                mediaState = mediaState,
                onSelectMedia = onSelectMedia,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}

@Composable
private fun HomeScreen(
    mediaState: MediaScreenState,
    onSelectMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (mediaState) {
            MediaScreenState.Empty -> {
                Text(
                    text = stringResource(R.string.home_prompt),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onSelectMedia) { Text(stringResource(R.string.select_media)) }
            }

            is MediaScreenState.Inspecting -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 14.dp))
                    Text(stringResource(R.string.inspecting_media))
                }
                Text(mediaState.uri.lastPathSegment.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onSelectMedia) { Text(stringResource(R.string.choose_another_media)) }
            }

            is MediaScreenState.Ready -> {
                MediaPreview(mediaState.media)
                MediaMetadata(mediaState.media)
                Button(onClick = onSelectMedia) { Text(stringResource(R.string.choose_another_media)) }
            }

            is MediaScreenState.Failed -> {
                Text(
                    text = stringResource(
                        if (mediaState.permissionRevoked) R.string.media_permission_unavailable
                        else R.string.media_inspection_failed,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onSelectMedia) { Text(stringResource(R.string.select_media)) }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
