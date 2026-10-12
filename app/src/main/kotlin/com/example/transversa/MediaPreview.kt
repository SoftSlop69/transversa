// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.example.transversa

import android.net.Uri
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberSliderState
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.os.Build
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.transversa.core.media.MediaDescriptor
import com.transversa.core.model.MediaKind
import com.transversa.core.presets.ConversionPreset
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MediaPreview(media: MediaDescriptor, modifier: Modifier = Modifier, videoHeight: Dp = 260.dp) {
    when (media.kind) {
        MediaKind.VIDEO -> VideoPreview(media.uri, modifier, videoHeight)
        MediaKind.AUDIO -> AudioPreview(media.uri, modifier)
        MediaKind.IMAGE -> ImagePreview(media.uri, modifier)
        MediaKind.UNKNOWN -> PreviewUnavailable(modifier)
    }
}

@Composable
fun MediaDetailsPanel(
    media: MediaDescriptor,
    activePreset: ConversionPreset?,
    hideFilename: Boolean,
    onSelectMedia: () -> Unit,
    modifier: Modifier = Modifier,
    previewHeight: Dp = 260.dp,
    elevatedWhiteCard: Boolean = false,
) {
    var expanded by remember(media.uri) { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val title = "${sourceFormat(media)} › ${activePreset?.profile?.container?.uppercase() ?: "?"}"
    val content: @Composable () -> Unit = {
        Column {
            MediaPreview(media, Modifier.fillMaxWidth(), previewHeight)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { expanded = !expanded }, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = stringResource(
                                if (expanded) R.string.hide_media_information else R.string.show_media_information,
                            ),
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        MetadataDetails(
                            media = media,
                            hideFilename = hideFilename,
                            onSelectMedia = onSelectMedia,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
    if (elevatedWhiteCard) {
        ElevatedCard(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.elevatedCardColors(containerColor = Color.White, contentColor = Color.Black),
            elevation = CardDefaults.elevatedCardElevation(),
            content = { content() },
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            content = { content() },
        )
    }
}

@Composable
private fun VideoPreview(uri: Uri, modifier: Modifier, height: Dp) {
    val context = LocalContext.current
    var previewFailed by remember(uri) { mutableStateOf(false) }
    var backdropFrame by remember(uri) { mutableStateOf<Bitmap?>(null) }
    val player = remember(uri) { ExoPlayer.Builder(context).build() }

    LaunchedEffect(uri) {
        backdropFrame = withContext(Dispatchers.IO) {
            extractFirstVideoFrame(context, uri)
        }
    }

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
        PreviewUnavailable(modifier.height(height))
    } else {
        Box(
            modifier = Modifier
                .then(modifier)
                .fillMaxWidth()
                .height(height)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clipToBounds(),
        ) {
            backdropFrame?.let { frame ->
                Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.12f
                            scaleY = 1.12f
                        }
                        .blur(32.dp),
                )
            }
            AndroidView(
                factory = { viewContext ->
                    (android.view.LayoutInflater.from(viewContext)
                        .inflate(R.layout.transversa_video_player_view, null, false) as PlayerView).apply {
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        useController = true
                        controllerAutoShow = false
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setPlayer(player)
                        hideController()
                    }
                },
                update = { view ->
                    view.player = player
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun extractFirstVideoFrame(context: android.content.Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST,
                480,
                480,
            )
        } else {
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
        }?.let { frame ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                frame
            } else {
                val scale = minOf(1f, 480f / frame.width, 480f / frame.height)
                val scaled = Bitmap.createScaledBitmap(
                    frame,
                    (frame.width * scale).toInt().coerceAtLeast(1),
                    (frame.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
                if (scaled !== frame) frame.recycle()
                scaled
            }
        }
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}

@Composable
private fun AudioPreview(uri: Uri, modifier: Modifier) {
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

    val progress = if (durationMs > 0L) positionMs.coerceIn(0L, durationMs).toFloat() else 0f
    val sliderState = rememberSliderState(
        value = progress,
        trackRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
    )
    LaunchedEffect(progress, isSeeking) {
        if (!isSeeking) sliderState.value = progress
    }

    if (previewFailed) {
        PreviewUnavailable(modifier)
    } else {
        Column(modifier = modifier.fillMaxWidth().padding(20.dp)) {
            Text(stringResource(R.string.audio_preview), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { if (player.isPlaying) player.pause() else player.play() }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(if (isPlaying) R.string.pause_audio else R.string.play_audio))
            }
            Slider(
                state = sliderState,
                enabled = durationMs > 0L,
                onValueChange = {
                    sliderState.value = it
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

@Composable
private fun ImagePreview(uri: Uri, modifier: Modifier) {
    var previewFailed by remember(uri) { mutableStateOf(false) }
    var imageLoaded by remember(uri) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 380.dp)
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
            if (!imageLoaded) LoadingIndicator()
        }
    }
}

@Composable
private fun PreviewUnavailable(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.preview_unavailable),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(24.dp),
    )
}

@Composable
private fun MetadataDetails(
    media: MediaDescriptor,
    hideFilename: Boolean,
    onSelectMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!hideFilename) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSelectMedia),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.file_name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    text = media.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }
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

private fun sourceFormat(media: MediaDescriptor): String {
    val extension = media.displayName.substringAfterLast('.', "")
        .takeIf { it.isNotBlank() && it.length <= 10 }
    val mimeSubtype = media.mimeType?.substringAfter('/', "")?.substringBefore(';')
        ?.takeIf { it.isNotBlank() }
    return (extension ?: mimeSubtype ?: "?").uppercase()
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
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

