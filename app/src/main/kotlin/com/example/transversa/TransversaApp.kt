// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.example.transversa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.carousel.CarouselParallaxScrollEffectState
import androidx.compose.material3.carousel.carouselParallaxScrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import kotlinx.coroutines.delay
import coil3.compose.AsyncImage
import java.io.File
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import kotlin.math.roundToInt
import com.transversa.core.media.MediaDescriptor
import com.transversa.core.model.MediaKind
import com.transversa.core.model.OutputAction
import com.transversa.core.presets.ConversionPreset

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun TransversaApp(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    mediaState: MediaScreenState,
    outputState: MediaOutputState,
    activePreset: ConversionPreset?,
    isSharedInput: Boolean,
    recentPresets: List<ConversionPreset>,
    allPresets: List<ConversionPreset>,
    history: List<ConversionHistoryEntry>,
    historyEnabled: Boolean,
    onHistoryEnabledChange: (Boolean) -> Unit,
    donationState: DonationBillingState,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onSelectMedia: () -> Unit,
    onClearMedia: () -> Unit,
    onPresetSelected: (ConversionPreset) -> Unit,
    onCreatePreset: (String, MediaKind) -> ConversionPreset?,
    onDeletePreset: (ConversionPreset) -> Unit,
    onSavePreset: (ConversionPreset) -> Unit,
    onSaveMedia: (MediaDescriptor, ConversionPreset) -> Unit,
    onShareMedia: (MediaDescriptor, ConversionPreset) -> Unit,
    onOpenSavedFile: (android.net.Uri) -> Unit,
    onDeleteHistoryEntry: (String) -> Unit,
    onClearHistory: () -> Unit,
    onShareApp: () -> Unit,
    onDonate: (String) -> Unit,
    onExit: () -> Unit,
) {
    TransversaTheme(themeMode) {
        var showSplash by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            delay(700)
            showSplash = false
        }
        if (showSplash) {
            SplashScreen()
            return@TransversaTheme
        }
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val route = backStackEntry?.destination?.route ?: ROUTE_CONVERT
        val editorScreen = route.startsWith("$ROUTE_EDITOR/")
        val title = when {
            route == ROUTE_HISTORY -> stringResource(R.string.history_title)
            route == ROUTE_PRESETS || route.startsWith("$ROUTE_PRESETS/") || route == "$ROUTE_PRESETS/{mediaKind}" -> stringResource(R.string.presets_title)
            route == ROUTE_INFO -> stringResource(R.string.info_title)
            editorScreen -> stringResource(R.string.preset_editor_title)
            else -> stringResource(R.string.convert_title)
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val horizontalMode = maxWidth >= 600.dp && maxWidth > maxHeight
            val carouselHeaderOwnsToolbar = horizontalMode && (
                route == ROUTE_INFO || (route == ROUTE_CONVERT && mediaState is MediaScreenState.Ready)
            )
            var navigationExpanded by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.weight(1f).fillMaxSize(),
            topBar = {
                if (!carouselHeaderOwnsToolbar) TopAppBar(
                    title = {
                        Text(
                            title,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (!navController.popBackStack()) onExit()
                        }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                        }
                    },
                    actions = {
                        if (route == ROUTE_CONVERT && mediaState !is MediaScreenState.Empty && !outputState.isBusy) {
                            IconButton(onClick = onClearMedia, shapes = IconButtonDefaults.shapes()) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.clear_media),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else if (route == ROUTE_HISTORY && history.isNotEmpty()) {
                            TextButton(onClick = onClearHistory, shapes = androidx.compose.material3.ButtonDefaults.shapes()) { Text(stringResource(R.string.clear_history)) }
                        }
                    },
                )
            },
            bottomBar = {
                if (!editorScreen && !horizontalMode) {
                    BoxWithConstraints {
                        val isCompactWidth = maxWidth < 600.dp
                        ShortNavigationBar(
                            arrangement = if (isCompactWidth) {
                                ShortNavigationBarArrangement.EqualWeight
                            } else {
                                ShortNavigationBarArrangement.Centered
                            },
                        ) {
                            mainNavigationItems().forEach { item ->
                                ShortNavigationBarItem(
                                    selected = route == item.route ||
                                        (item.route == ROUTE_PRESETS && (route.startsWith("$ROUTE_PRESETS/") || route == "$ROUTE_PRESETS/{mediaKind}")),
                                    onClick = {
                                        if (
                                            route != item.route &&
                                            !(item.route == ROUTE_PRESETS && route.startsWith("$ROUTE_PRESETS/"))
                                        ) {
                                            navController.navigate(item.route) {
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = null) },
                                    label = { Text(stringResource(item.label)) },
                                    iconPosition = if (isCompactWidth) {
                                        NavigationItemIconPosition.Top
                                    } else {
                                        NavigationItemIconPosition.Start
                                    },
                                )
                            }
                        }
                    }
                }
            },
        ) { contentPadding ->
            NavHost(navController, startDestination = ROUTE_CONVERT, modifier = Modifier.padding(contentPadding)) {
                composable(ROUTE_CONVERT) {
                    ConvertScreen(
                        mediaState, outputState, activePreset, isSharedInput, recentPresets,
                        onSelectMedia, onPresetSelected,
                        onNavigateBack = { if (!navController.popBackStack()) onExit() },
                        onClearMedia = onClearMedia,
                        onGoToPresets = {
                            val kind = (mediaState as? MediaScreenState.Ready)?.media?.kind
                                ?: activePreset?.mediaKind
                                ?: MediaKind.VIDEO
                            navController.navigate("$ROUTE_PRESETS/${kind.name}") { launchSingleTop = true }
                        },
                        onSaveMedia, onShareMedia,
                        onOpenSavedFile,
                        allPresets = allPresets,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable(ROUTE_HISTORY) {
                    HistoryScreen(history, historyEnabled, onHistoryEnabledChange, onDeleteHistoryEntry, modifier = Modifier.fillMaxSize())
                }
                composable(ROUTE_INFO) {
                    InfoScreen(
                        donationState = donationState,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        notificationsEnabled = notificationsEnabled,
                        onNotificationsEnabledChange = onNotificationsEnabledChange,
                        onShareApp = onShareApp,
                        onDonate = onDonate,
                        horizontalMode = horizontalMode,
                        onNavigateBack = { if (!navController.popBackStack()) onExit() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable(ROUTE_PRESETS) {
                    PresetsScreen(
                        allPresets = allPresets,
                        createKind = activePreset?.mediaKind ?: MediaKind.VIDEO,
                        onEdit = { navController.navigate("$ROUTE_EDITOR/${it.id}") },
                        onCreatePreset = onCreatePreset,
                        onDeletePreset = onDeletePreset,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable(
                    route = "$ROUTE_PRESETS/{mediaKind}",
                    arguments = listOf(navArgument("mediaKind") { type = NavType.StringType }),
                ) { entry ->
                    val expandedKind = entry.arguments?.getString("mediaKind")
                        ?.let { value -> MediaKind.entries.firstOrNull { it.name == value } }
                    PresetsScreen(
                        allPresets = allPresets,
                        createKind = expandedKind ?: activePreset?.mediaKind ?: MediaKind.VIDEO,
                        initialExpandedKind = expandedKind,
                        onEdit = { navController.navigate("$ROUTE_EDITOR/${it.id}") },
                        onCreatePreset = onCreatePreset,
                        onDeletePreset = onDeletePreset,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable(
                    route = "$ROUTE_EDITOR/{presetId}",
                    arguments = listOf(navArgument("presetId") { type = NavType.StringType }),
                ) { entry ->
                    val presetId = entry.arguments?.getString("presetId")
                    val preset = allPresets.firstOrNull { it.id == presetId }
                    if (preset == null) {
                        TextButton(onClick = { navController.popBackStack() }, shapes = androidx.compose.material3.ButtonDefaults.shapes()) { Text(stringResource(R.string.back_to_presets)) }
                    } else {
                        PresetEditorScreen(
                            preset = preset,
                            allPresets = allPresets,
                            onDelete = { onDeletePreset(preset); navController.popBackStack() },
                            onSave = { onSavePreset(it); navController.popBackStack() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
                if (horizontalMode && !editorScreen) {
                    LandscapeNavigationSidebar(
                        route = route,
                        expanded = navigationExpanded,
                        onExpandedChange = { navigationExpanded = it },
                        onNavigate = { destination ->
                            if (
                                route != destination &&
                                !(destination == ROUTE_PRESETS && route.startsWith("$ROUTE_PRESETS/"))
                            ) {
                                navController.navigate(destination) { launchSingleTop = true }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    val robotoFlex = remember {
        FontFamily(
            Font(
                R.font.roboto_flex,
                weight = FontWeight.Black,
                variationSettings = FontVariation.Settings(FontVariation.weight(900)),
            ),
        )
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = robotoFlex, fontWeight = FontWeight.Black),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ConvertScreen(
    mediaState: MediaScreenState,
    outputState: MediaOutputState,
    activePreset: ConversionPreset?,
    isSharedInput: Boolean,
    recentPresets: List<ConversionPreset>,
    onSelectMedia: () -> Unit,
    onPresetSelected: (ConversionPreset) -> Unit,
    onNavigateBack: () -> Unit,
    onClearMedia: () -> Unit,
    onGoToPresets: () -> Unit,
    onSaveMedia: (MediaDescriptor, ConversionPreset) -> Unit,
    onShareMedia: (MediaDescriptor, ConversionPreset) -> Unit,
    onOpenSavedFile: (android.net.Uri) -> Unit,
    allPresets: List<ConversionPreset>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val useHorizontalLayout = maxWidth >= 600.dp && maxWidth > maxHeight
        if (useHorizontalLayout && mediaState is MediaScreenState.Ready) {
            HorizontalReadyConvertScreen(
                media = mediaState.media,
                outputState = outputState,
                activePreset = activePreset,
                recentPresets = recentPresets,
                allPresets = allPresets,
                isSharedInput = isSharedInput,
                onNavigateBack = onNavigateBack,
                onClearMedia = onClearMedia,
                onSelectMedia = onSelectMedia,
                onPresetSelected = onPresetSelected,
                onGoToPresets = onGoToPresets,
                onSaveMedia = onSaveMedia,
                onShareMedia = onShareMedia,
                onOpenSavedFile = onOpenSavedFile,
                availableHeight = maxHeight,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
        when (mediaState) {
            MediaScreenState.Empty -> {
                Text(
                    text = stringResource(R.string.home_prompt),
                    style = MaterialTheme.typography.bodyLarge,
                )
                FilledTonalButton(
                    onClick = onSelectMedia,
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Outlined.VideoLibrary, contentDescription = null, modifier = Modifier.size(32.dp))
                        Text(stringResource(R.string.get_started), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            is MediaScreenState.Inspecting -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoadingIndicator(modifier = Modifier.size(32.dp).padding(end = 14.dp))
                    Text(stringResource(R.string.inspecting_media))
                }
                Button(onClick = onSelectMedia, shapes = androidx.compose.material3.ButtonDefaults.shapes()) { Text(stringResource(R.string.choose_another_media)) }
            }

            is MediaScreenState.Ready -> {
                MediaDetailsPanel(
                    media = mediaState.media,
                    activePreset = activePreset,
                    hideFilename = isSharedInput,
                    onSelectMedia = onSelectMedia,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = { activePreset?.let { onSaveMedia(mediaState.media, it) } },
                        enabled = !outputState.isBusy && mediaState.media.kind != MediaKind.UNKNOWN && activePreset != null,
                        modifier = Modifier.weight(1f).height(96.dp),
                        shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(32.dp))
                            Text(stringResource(R.string.save_media), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    FilledTonalButton(
                        onClick = { activePreset?.let { onShareMedia(mediaState.media, it) } },
                        enabled = !outputState.isBusy && mediaState.media.kind != MediaKind.UNKNOWN && activePreset != null,
                        modifier = Modifier.weight(1f).height(96.dp),
                        shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(32.dp))
                            Text(stringResource(R.string.share_media), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (outputState.isBusy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LoadingIndicator(modifier = Modifier.size(32.dp).padding(end = 12.dp))
                        Text(stringResource(R.string.converting_media_progress, outputState.progressPercent))
                    }
                }
                if (outputState.savedFileName != null && outputState.savedFileUri != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.saved_file_prefix),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = outputState.savedFileName,
                            modifier = Modifier.clickable(role = Role.Button) {
                                onOpenSavedFile(outputState.savedFileUri)
                            },
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                        )
                    }
                }
                outputState.message?.let { message ->
                    Text(
                        text = message,
                        color = if (outputState.hasError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                activePreset?.let { preset ->
                    PresetSelector(
                        activePreset = preset,
                        recentPresets = recentPresets,
                        allPresets = allPresets,
                        onPresetSelected = onPresetSelected,
                        onGoToPresets = onGoToPresets,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                Button(onClick = onSelectMedia, shapes = androidx.compose.material3.ButtonDefaults.shapes()) { Text(stringResource(R.string.select_media)) }
            }
        }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LandscapeNavigationSidebar(
    route: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val sidebarWidth by animateDpAsState(
        targetValue = if (expanded) 184.dp else 72.dp,
        label = "landscapeNavigationWidth",
    )
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(sidebarWidth)
            .padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            IconButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = if (expanded) Modifier.fillMaxWidth() else Modifier,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.collapse_navigation else R.string.expand_navigation,
                    ),
                )
            }
            mainNavigationItems().forEach { item ->
                val selected = route == item.route ||
                    (item.route == ROUTE_PRESETS && route.startsWith("$ROUTE_PRESETS/"))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                        )
                        .clickable { onNavigate(item.route) }
                        .padding(horizontal = if (expanded) 14.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = if (expanded) null else stringResource(item.label),
                        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (expanded) {
                        Text(
                            text = stringResource(item.label),
                            modifier = Modifier.padding(start = 14.dp),
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LandscapeHeaderCard(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onClearMedia: (() -> Unit)? = null,
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White, contentColor = Color.Black),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f).padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            onClearMedia?.let { onClear ->
                Button(
                    onClick = onClear,
                    modifier = Modifier,
                ) {
                    Text(stringResource(R.string.clear_history))
                }
            }
        }
    }
}
@Composable
internal fun landscapeTitleCardWidth(title: String): androidx.compose.ui.unit.Dp {
    val titleWidthPx = rememberTextMeasurer().measure(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        softWrap = false,
    ).size.width
    return with(LocalDensity.current) { titleWidthPx.toDp() } + 48.dp + 32.dp + 12.dp
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HorizontalReadyConvertScreen(
    media: MediaDescriptor,
    outputState: MediaOutputState,
    activePreset: ConversionPreset?,
    recentPresets: List<ConversionPreset>,
    allPresets: List<ConversionPreset>,
    isSharedInput: Boolean,
    onNavigateBack: () -> Unit,
    onClearMedia: () -> Unit,
    onSelectMedia: () -> Unit,
    onPresetSelected: (ConversionPreset) -> Unit,
    onGoToPresets: () -> Unit,
    onSaveMedia: (MediaDescriptor, ConversionPreset) -> Unit,
    onShareMedia: (MediaDescriptor, ConversionPreset) -> Unit,
    onOpenSavedFile: (android.net.Uri) -> Unit,
    availableHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val itemHeight = (maxHeight - 8.dp).coerceAtLeast(220.dp)
        val previewHeight = (itemHeight - 64.dp).coerceAtLeast(140.dp)
        val titleCardWidth = landscapeTitleCardWidth(stringResource(R.string.convert_title))
        val itemWidths = listOf(titleCardWidth, itemHeight * 1.08f, itemHeight * 0.82f, itemHeight * 0.96f)
        val listState = rememberLazyListState()
        val parallaxState = remember(listState) { CarouselParallaxScrollEffectState(listState) }
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(itemWidths) { index, itemWidth ->
                Box(
                    Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .carouselParallaxScrollEffect(index, parallaxState, RoundedCornerShape(20.dp)),
                ) {
            when (index) {
                0 -> LandscapeHeaderCard(
                    title = stringResource(R.string.convert_title),
                    onNavigateBack = onNavigateBack,
                    onClearMedia = if (!outputState.isBusy) onClearMedia else null,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )

                1 -> MediaDetailsPanel(
                    media = media,
                    activePreset = activePreset,
                    hideFilename = isSharedInput,
                    onSelectMedia = onSelectMedia,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    previewHeight = previewHeight,
                    elevatedWhiteCard = true,
                )

                2 -> ElevatedCard(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White, contentColor = Color.Black),
                    elevation = CardDefaults.elevatedCardElevation(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FilledTonalButton(
                            onClick = { activePreset?.let { onSaveMedia(media, it) } },
                            enabled = !outputState.isBusy && media.kind != MediaKind.UNKNOWN && activePreset != null,
                            modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 88.dp),
                            shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(32.dp))
                                Text(stringResource(R.string.save_media), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        FilledTonalButton(
                            onClick = { activePreset?.let { onShareMedia(media, it) } },
                            enabled = !outputState.isBusy && media.kind != MediaKind.UNKNOWN && activePreset != null,
                            modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 88.dp),
                            shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(32.dp))
                                Text(stringResource(R.string.share_media), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        if (outputState.isBusy) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LoadingIndicator(modifier = Modifier.size(24.dp))
                                Text(stringResource(R.string.converting_media_progress, outputState.progressPercent))
                            }
                        }
                        if (outputState.savedFileName != null && outputState.savedFileUri != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.saved_file_prefix), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = outputState.savedFileName,
                                    modifier = Modifier.clickable(role = Role.Button) { onOpenSavedFile(outputState.savedFileUri) },
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                                )
                            }
                        }
                        outputState.message?.let { message ->
                            Text(
                                text = message,
                                color = if (outputState.hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                3 -> ElevatedCard(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White, contentColor = Color.Black),
                    elevation = CardDefaults.elevatedCardElevation(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.presets_title), style = MaterialTheme.typography.titleMedium)
                        activePreset?.let { preset ->
                            PresetSelector(
                                activePreset = preset,
                                recentPresets = recentPresets,
                                allPresets = allPresets,
                                onPresetSelected = onPresetSelected,
                                onGoToPresets = onGoToPresets,
                                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }
                }
            }
        }
    }
}

@Composable
private fun PresetSelector(
    activePreset: ConversionPreset,
    recentPresets: List<ConversionPreset>,
    allPresets: List<ConversionPreset>,
    onPresetSelected: (ConversionPreset) -> Unit,
    onGoToPresets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val availablePresets = allPresets.filter { it.mediaKind == activePreset.mediaKind }
    val defaultPreset = availablePresets.firstOrNull {
        it.mediaKind == activePreset.mediaKind && it.isDefault
    }
    val choices = (listOfNotNull(defaultPreset) + recentPresets + availablePresets + activePreset)
        .distinctBy { it.id }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        choices.forEachIndexed { index, preset ->
            SegmentedListItem(
                selected = preset.id == activePreset.id,
                onClick = { onPresetSelected(preset) },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = choices.size + 1),
                leadingContent = {
                    Icon(
                        if (preset.isDefault) Icons.Outlined.Star else Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                content = {
                    Text(
                        preset.name,
                        fontWeight = if (preset.isDefault) FontWeight.Bold else FontWeight.Normal,
                    )
                },
            )
        }
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = choices.size, count = choices.size + 1),
            modifier = Modifier.clickable(role = Role.Button, onClick = onGoToPresets),
            leadingContent = {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            content = { Text(stringResource(R.string.edit_presets)) },
        )
    }
}

@Composable
private fun HistoryScreen(
    history: List<ConversionHistoryEntry>,
    historyEnabled: Boolean,
    onHistoryEnabledChange: (Boolean) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                leadingContent = {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                supportingContent = { Text(stringResource(R.string.history_toggle_description)) },
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = historyEnabled,
                        onCheckedChange = onHistoryEnabledChange,
                    )
                },
                content = { Text(stringResource(R.string.history_toggle_title)) },
            )
        }
        if (history.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(if (historyEnabled) R.string.empty_history else R.string.history_disabled_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                history.forEachIndexed { index, item ->
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = history.size),
                        leadingContent = {
                            val thumbnail = item.thumbnailPath?.let(::File)
                            if (thumbnail?.isFile == true) {
                                AsyncImage(
                                    model = thumbnail,
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                                )
                            } else {
                                val mediaIcon = when (item.kind) {
                                    MediaKind.VIDEO -> Icons.Outlined.VideoLibrary
                                    MediaKind.IMAGE -> Icons.Outlined.Image
                                    MediaKind.AUDIO -> Icons.Outlined.MusicNote
                                    MediaKind.UNKNOWN -> Icons.Outlined.VideoLibrary
                                }
                                Icon(mediaIcon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        supportingContent = {
                            Column {
                                val actionLabel = stringResource(if (item.action == OutputAction.SAVE) R.string.save_media else R.string.share_media)
                                Text("${item.sourceName}  ·  $actionLabel  ·  ${mediaKindLabel(item.kind)}")
                                Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(item.createdAtMillis)))
                            }
                        },
                        trailingContent = {
                            FilledIconButton(
                                onClick = { onDelete(item.id) },
                                modifier = Modifier.size(36.dp),
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete_history_item), modifier = Modifier.size(18.dp))
                            }
                        },
                        content = { Text(item.outputName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetsScreen(
    allPresets: List<ConversionPreset>,
    createKind: MediaKind,
    initialExpandedKind: MediaKind? = null,
    onEdit: (ConversionPreset) -> Unit,
    onCreatePreset: (String, MediaKind) -> ConversionPreset?,
    onDeletePreset: (ConversionPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var dialogCreateKind by remember(createKind) { mutableStateOf(createKind) }
    var expandedKinds by remember(initialExpandedKind) {
        mutableStateOf(initialExpandedKind?.let(::setOf) ?: emptySet())
    }
    val duplicate = allPresets.any {
        it.mediaKind == dialogCreateKind && it.name.equals(presetName.trim(), ignoreCase = true)
    }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            allPresets.groupBy { it.mediaKind }.forEach { (kind, presets) ->
                val orderedPresets = presets.sortedByDescending { it.isDefault }
                val expanded = kind in expandedKinds
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) {
                                    expandedKinds = if (expanded) expandedKinds - kind else expandedKinds + kind
                                }.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(mediaKindLabel(kind), style = MaterialTheme.typography.titleSmall)
                                IconButton(
                                    onClick = { expandedKinds = if (expanded) expandedKinds - kind else expandedKinds + kind },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        contentDescription = stringResource(
                                            if (expanded) R.string.collapse_preset_group else R.string.expand_preset_group,
                                            mediaKindLabel(kind),
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
                                Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                                    ) {
                                    orderedPresets.forEachIndexed { index, preset ->
                        SegmentedListItem(
                            onClick = { onEdit(preset) },
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = orderedPresets.size + 1),
                            leadingContent = {
                                Icon(
                                    if (preset.isDefault) Icons.Outlined.Star else Icons.Outlined.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            supportingContent = { Text(preset.description) },
                            trailingContent = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (!preset.isDefault) {
                                        FilledIconButton(
                                            onClick = { onDeletePreset(preset) },
                                            modifier = Modifier.size(IconButtonDefaults.extraSmallContainerSize()),
                                            shapes = IconButtonDefaults.shapes(),
                                        ) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = stringResource(R.string.delete_preset_accessibility),
                                                modifier = Modifier.size(IconButtonDefaults.extraSmallIconSize),
                                            )
                                        }
                                    }
                                    FilledIconButton(
                                        onClick = { onEdit(preset) },
                                        modifier = Modifier.size(IconButtonDefaults.extraSmallContainerSize()),
                                        shapes = IconButtonDefaults.shapes(),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = stringResource(R.string.edit_preset),
                                            modifier = Modifier.size(IconButtonDefaults.extraSmallIconSize),
                                        )
                                    }
                                }
                            },
                            content = { Text(preset.name) },
                        )
                                    }
                                    SegmentedListItem(
                        onClick = {
                            dialogCreateKind = kind
                            presetName = ""
                            showCreateDialog = true
                        },
                        shapes = ListItemDefaults.segmentedShapes(
                            index = orderedPresets.size,
                            count = orderedPresets.size + 1,
                        ),
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        content = { Text(stringResource(R.string.add_preset)) },
                                    )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.create_preset)) },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text(stringResource(R.string.preset_name)) },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) ({ Text(stringResource(R.string.preset_name_exists)) }) else null,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newPreset = onCreatePreset(presetName.trim(), dialogCreateKind)
                        showCreateDialog = false
                        newPreset?.let(onEdit)
                    },
                    enabled = presetName.isNotBlank() && !duplicate,
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.create_preset))
                }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }, shapes = androidx.compose.material3.ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun mediaKindLabel(kind: MediaKind): String = when (kind) {
    MediaKind.VIDEO -> stringResource(R.string.video)
    MediaKind.IMAGE -> stringResource(R.string.images)
    MediaKind.AUDIO -> stringResource(R.string.audio)
    MediaKind.UNKNOWN -> stringResource(R.string.unknown_media_type)
}

private data class MainNavigationItem(val route: String, val label: Int, val icon: ImageVector)

private fun mainNavigationItems() = listOf(
    MainNavigationItem(ROUTE_CONVERT, R.string.convert_title, Icons.Outlined.Home),
    MainNavigationItem(ROUTE_HISTORY, R.string.history_title, Icons.Outlined.History),
    MainNavigationItem(ROUTE_PRESETS, R.string.presets_title, Icons.Outlined.Tune),
    MainNavigationItem(ROUTE_INFO, R.string.info_title, Icons.Outlined.Info),
)

private const val ROUTE_CONVERT = "convert"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_PRESETS = "presets"
private const val ROUTE_INFO = "info"
private const val ROUTE_EDITOR = "preset-editor"

@Composable
private fun PresetEditorScreen(
    preset: ConversionPreset,
    allPresets: List<ConversionPreset>,
    onDelete: () -> Unit,
    onSave: (ConversionPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var presetName by remember(preset.id) { mutableStateOf(preset.name) }
    var editingName by remember(preset.id) { mutableStateOf(false) }
    var showDeleteConfirmation by remember(preset.id) { mutableStateOf(false) }
    val duplicateName = allPresets.any {
        it.id != preset.id && it.mediaKind == preset.mediaKind && it.name.equals(presetName.trim(), ignoreCase = true)
    }
    var container by remember(preset.id) { mutableStateOf(preset.profile.container.lowercase()) }
    var videoCodec by remember(preset.id) {
        mutableStateOf(
            preset.profile.videoCodec?.takeIf { codec ->
                when (preset.profile.container.lowercase()) {
                    "webm" -> codec == "vp9"
                    "mkv" -> codec in listOf("h264", "hevc", "vp9")
                    else -> codec in listOf("h264", "hevc")
                }
            } ?: if (preset.profile.container.equals("webm", ignoreCase = true)) "vp9" else "h264",
        )
    }
    var audioCodec by remember(preset.id) {
        val format = preset.profile.container.lowercase()
        val validCodecs = when (preset.mediaKind) {
            MediaKind.VIDEO -> when (format) {
                "webm" -> listOf("vorbis")
                "mkv" -> listOf("aac", "mp3", "flac", "vorbis")
                else -> listOf("aac", "mp3")
            }
            MediaKind.AUDIO -> when (format) {
                "mp3" -> listOf("mp3")
                "flac" -> listOf("flac")
                else -> listOf("vorbis")
            }
            else -> listOf("aac")
        }
        mutableStateOf(preset.profile.audioCodec?.takeIf { it in validCodecs } ?: validCodecs.first())
    }
    var videoQuality by remember(preset.id) {
        mutableIntStateOf(preset.profile.options["video.crf"]?.toIntOrNull() ?: 23)
    }
    var audioBitrate by remember(preset.id) {
        mutableIntStateOf((preset.profile.options["audio.bitrate"]?.toIntOrNull() ?: 192_000) / 1_000)
    }
    var imageQuality by remember(preset.id) {
        mutableIntStateOf(
            if (preset.profile.container.equals("png", ignoreCase = true)) {
                preset.profile.options["image.compression"]?.toIntOrNull() ?: 3
            } else {
                preset.profile.options["image.quality"]?.toIntOrNull() ?: 90
            },
        )
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (editingName) {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text(stringResource(R.string.preset_name)) },
                    singleLine = true,
                    isError = duplicateName || presetName.isBlank(),
                    supportingText = when {
                        duplicateName -> ({ Text(stringResource(R.string.preset_name_exists)) })
                        presetName.isBlank() -> ({ Text(stringResource(R.string.preset_name_empty)) })
                        else -> null
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { editingName = false },
                    enabled = presetName.isNotBlank() && !duplicateName,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.finish_name_edit))
                }
            } else {
                Text(
                    text = presetName,
                    modifier = Modifier.weight(1f).clickable(role = Role.Button) { editingName = true },
                    style = MaterialTheme.typography.headlineSmall,
                )
                IconButton(onClick = { editingName = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit_preset_name))
                }
            }
            if (!preset.isDefault) {
                IconButton(onClick = { showDeleteConfirmation = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete_preset_accessibility),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Text(stringResource(R.string.preset_format_codec_heading), style = MaterialTheme.typography.titleMedium)
        when (preset.mediaKind) {
            MediaKind.VIDEO -> {
                PresetChoiceGroup(
                    label = stringResource(R.string.container_format),
                    selected = container,
                    options = listOf("mp4", "mkv", "webm", "mov"),
                    optionLabel = { it.uppercase() },
                    onSelected = {
                        container = it
                        videoCodec = when (it) {
                            "webm" -> "vp9"
                            else -> "h264"
                        }
                        audioCodec = if (it == "webm") "vorbis" else "aac"
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PresetChoiceGroup(
                        label = stringResource(R.string.video_codec),
                        selected = videoCodec,
                        options = when (container) {
                            "webm" -> listOf("vp9")
                            "mkv" -> listOf("h264", "hevc", "vp9")
                            else -> listOf("h264", "hevc")
                        },
                        optionLabel = { videoCodecLabel(it) },
                        onSelected = { videoCodec = it },
                        modifier = Modifier.weight(1f),
                    )
                    PresetChoiceGroup(
                        label = stringResource(R.string.audio_codec),
                        selected = audioCodec,
                        options = when (container) {
                            "webm" -> listOf("vorbis")
                            "mkv" -> listOf("aac", "mp3", "flac", "vorbis")
                            else -> listOf("aac", "mp3")
                        },
                        optionLabel = { it.uppercase() },
                        onSelected = { audioCodec = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PresetQualitySlider(
                        label = stringResource(R.string.video_quality_crf),
                        valueText = videoQuality.toString(),
                        value = videoQuality.toFloat(),
                        valueRange = 0f..51f,
                        steps = 50,
                        onValueChange = { videoQuality = it.roundToInt() },
                        modifier = Modifier.weight(1f),
                    )
                    PresetQualitySlider(
                        label = stringResource(R.string.audio_bitrate),
                        valueText = "$audioBitrate kb/s",
                        value = audioBitrate.toFloat(),
                        valueRange = 32f..320f,
                        onValueChange = { audioBitrate = it.roundToInt() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            MediaKind.AUDIO -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PresetChoiceGroup(
                        label = stringResource(R.string.container_format),
                        selected = container,
                        options = listOf("mp3", "flac", "ogg"),
                        optionLabel = { it.uppercase() },
                        onSelected = {
                            container = it
                            audioCodec = when (it) {
                                "mp3" -> "mp3"
                                "flac" -> "flac"
                                else -> "vorbis"
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    PresetChoiceGroup(
                        label = stringResource(R.string.audio_codec),
                        selected = audioCodec,
                        options = when (container) {
                            "mp3" -> listOf("mp3")
                            "flac" -> listOf("flac")
                            else -> listOf("vorbis")
                        },
                        optionLabel = { it.uppercase() },
                        onSelected = { audioCodec = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                PresetQualitySlider(
                    label = stringResource(R.string.audio_bitrate),
                    valueText = "$audioBitrate kb/s",
                    value = audioBitrate.toFloat(),
                    valueRange = 32f..320f,
                    onValueChange = { audioBitrate = it.roundToInt() },
                )
            }

            MediaKind.IMAGE -> {
                PresetChoiceGroup(
                    label = stringResource(R.string.image_format),
                    selected = container,
                    options = listOf("png", "jpg", "webp"),
                    optionLabel = { it.uppercase() },
                    onSelected = {
                        container = it
                        imageQuality = if (it == "png") 3 else 90
                    },
                )
                PresetQualitySlider(
                    label = stringResource(
                        if (container == "png") R.string.png_compression else R.string.image_quality,
                    ),
                    valueText = imageQuality.toString(),
                    value = imageQuality.toFloat(),
                    valueRange = if (container == "png") 0f..9f else 1f..100f,
                    steps = if (container == "png") 8 else 0,
                    onValueChange = { imageQuality = it.roundToInt() },
                )
            }

            MediaKind.UNKNOWN -> Unit
        }

        Button(
            onClick = {
                val options = when (preset.mediaKind) {
                    MediaKind.VIDEO -> preset.profile.options + mapOf(
                        "video.crf" to videoQuality.toString(),
                        "audio.bitrate" to (audioBitrate * 1_000).toString(),
                    )
                    MediaKind.AUDIO -> preset.profile.options + ("audio.bitrate" to (audioBitrate * 1_000).toString())
                    MediaKind.IMAGE -> {
                        val options = preset.profile.options - "image.compression" - "image.quality"
                        options + if (container == "png") {
                            "image.compression" to imageQuality.toString()
                        } else {
                            "image.quality" to imageQuality.toString()
                        }
                    }
                    MediaKind.UNKNOWN -> preset.profile.options
                }
                onSave(
                    preset.copy(
                        name = presetName.trim(),
                        profile = preset.profile.copy(
                            container = container,
                            videoCodec = videoCodec,
                            audioCodec = audioCodec,
                            options = options,
                        ),
                    ),
                )
            },
            enabled = presetName.isNotBlank() && !duplicateName,
            modifier = Modifier.fillMaxWidth(),
            shapes = androidx.compose.material3.ButtonDefaults.shapes(),
        ) { Text(stringResource(R.string.save_changes)) }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.confirm_delete_preset_title)) },
            text = { Text(stringResource(R.string.confirm_delete_preset_message, preset.name)) },
            confirmButton = {
                TextButton(onClick = onDelete, shapes = androidx.compose.material3.ButtonDefaults.shapes()) { Text(stringResource(R.string.delete_preset)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }, shapes = androidx.compose.material3.ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun <T> PresetChoiceGroup(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(optionLabel(option), style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

private fun videoCodecLabel(codec: String): String = when (codec) {
    "h264" -> "H.264"
    "hevc" -> "H.265"
    else -> codec.uppercase()
}

@Composable
private fun PresetQualitySlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val sliderState = androidx.compose.material3.rememberSliderState(
        value = value,
        steps = steps,
        trackRange = valueRange,
    )
    LaunchedEffect(value) { sliderState.value = value }
    Column(modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(valueText, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            state = sliderState,
            onValueChange = {
                sliderState.value = it
                onValueChange(it)
            },
        )
    }
}

