// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.transversa.core.media.MediaDescriptor
import com.transversa.core.model.MediaKind
import kotlinx.coroutines.delay

@Composable
fun MediaPreview(media: MediaDescriptor) {
    when (media.kind) {
        MediaKind.VIDEO -> VideoPreview(media.uri)
        MediaKind.AUDIO -> AudioPreview(media.uri)
        MediaKind.IMAGE -> ImagePreview(media.uri)
        MediaKind.UNKNOWN -> PreviewUnavailable()
    }
}

@Composable
private fun VideoPreview(uri: Uri) {
    val context = LocalContext.current
    var previewFailed by remember(uri) { mutableStateOf(false) }
    val player = remember(uri) { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                previewFailed = true
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(uri))
        player.playWhenReady = false
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    if (previewFailed) {
        PreviewUnavailable()
    } else {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    setPlayer(player)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    useController = true
                }
            },
            update = { it.player = player },
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
    }
}

@Composable
private fun AudioPreview(uri: Uri) {
    val context = LocalContext.current
    var previewFailed by remember(uri) { mutableStateOf(false) }
    var isPlaying by remember(uri) { mutableStateOf(false) }
    var isSeeking by remember(uri) { mutableStateOf(false) }
    var durationMs by remember(uri) { mutableLongStateOf(0L) }
    var positionMs by remember(uri) { mutableLongStateOf(0L) }
    val player = remember(uri) { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) durationMs = player.duration.coerceAtLeast(0L)
            }

            override fun onPlayerError(error: PlaybackException) {
                previewFailed = true
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(uri))
        player.playWhenReady = false
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (!isSeeking) positionMs = player.currentPosition.coerceAtLeast(0L)
            if (player.duration > 0L) durationMs = player.duration
            delay(300)
        }
    }

    if (previewFailed) {
        PreviewUnavailable()
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.audio_preview), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Text(stringResource(if (isPlaying) R.string.pause_audio else R.string.play_audio))
                }
                Slider(
                    value = if (durationMs > 0L) positionMs.coerceIn(0L, durationMs).toFloat() else 0f,
                    valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    enabled = durationMs > 0L,
                    onValueChange = {
                        isSeeking = true
                        positionMs = it.toLong()
                    },
                    onValueChangeFinished = {
                        player.seekTo(positionMs)
                        isSeeking = false
                    },
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(positionMs), style = MaterialTheme.typography.bodySmall)
                    Text(formatTime(durationMs), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ImagePreview(uri: Uri) {
    var previewFailed by remember(uri) { mutableStateOf(false) }
    var imageLoaded by remember(uri) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 380.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (previewFailed) {
            PreviewUnavailable()
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(uri).build(),
                contentDescription = stringResource(R.string.image_preview),
                contentScale = ContentScale.Fit,
                onLoading = { imageLoaded = false },
                onSuccess = { imageLoaded = true },
                onError = { previewFailed = true },
                modifier = Modifier.fillMaxSize(),
            )
            if (!imageLoaded) CircularProgressIndicator()
        }
    }
}

@Composable
private fun PreviewUnavailable() {
    Text(
        text = stringResource(R.string.preview_unavailable),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
fun MediaMetadata(media: MediaDescriptor) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(media.displayName, style = MaterialTheme.typography.titleMedium)
            MetadataRow(stringResource(R.string.media_type), stringResource(media.kind.labelResource()))
            media.mimeType?.let { MetadataRow(stringResource(R.string.media_format), it) }
            media.width?.let { width ->
                val height = media.height
                if (height != null) MetadataRow(stringResource(R.string.dimensions), "$width × $height")
            }
            media.durationMs?.let { MetadataRow(stringResource(R.string.duration), formatTime(it)) }
            media.sizeBytes?.let {
                MetadataRow(stringResource(R.string.file_size), android.text.format.Formatter.formatShortFileSize(context, it))
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun MediaKind.labelResource(): Int = when (this) {
    MediaKind.UNKNOWN -> R.string.unknown_media_type
    MediaKind.VIDEO -> R.string.video
    MediaKind.IMAGE -> R.string.image
    MediaKind.AUDIO -> R.string.audio
}

private fun formatTime(durationMs: Long): String = DateUtils.formatElapsedTime(durationMs.coerceAtLeast(0L) / 1_000L)
