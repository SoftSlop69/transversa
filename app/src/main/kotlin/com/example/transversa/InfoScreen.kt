// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.carousel.CarouselParallaxScrollEffectState
import androidx.compose.material3.carousel.carouselParallaxScrollEffect
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LicenseNotice(val title: Int, val summary: Int, val asset: String)

private val licenseNotices = listOf(
    LicenseNotice(R.string.license_gpl3_title, R.string.license_project_summary, "GPL-3.0.txt"),
    LicenseNotice(R.string.license_gpl2_title, R.string.license_ffmpeg_summary, "GPL-2.0.txt"),
    LicenseNotice(R.string.license_lgpl2_title, R.string.license_lame_summary, "LGPL-2.0.txt"),
    LicenseNotice(R.string.license_lgpl_title, R.string.license_ffmpeg_lgpl_summary, "LGPL-2.1.txt"),
    LicenseNotice(R.string.license_bsd_title, R.string.license_kvazaar_summary, "BSD-3-Clause.txt"),
    LicenseNotice(R.string.license_apache_title, R.string.license_android_summary, "Apache-2.0.txt"),
    LicenseNotice(R.string.license_android_sdk_title, R.string.license_billing_summary, "ANDROID-SDK-LICENSE.txt"),
    LicenseNotice(R.string.license_ofl_title, R.string.license_font_summary, "OFL-1.1.txt"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InfoScreen(
    donationState: DonationBillingState,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onShareApp: () -> Unit,
    onDonate: (String) -> Unit,
    horizontalMode: Boolean = false,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showDonations by remember { mutableStateOf(false) }
    var selectedLicense by remember { mutableStateOf<LicenseNotice?>(null) }
    var applicationExpanded by remember { mutableStateOf(false) }
    var licensesExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val useHorizontalCarousel = horizontalMode && maxWidth >= 600.dp && maxWidth > maxHeight
        if (useHorizontalCarousel) {
            val itemHeight = (maxHeight - 8.dp).coerceAtLeast(220.dp)
            val infoTitleCardWidth = landscapeTitleCardWidth(stringResource(R.string.info_title))
            val itemWidths = listOf(infoTitleCardWidth, itemHeight * 1.25f, itemHeight * 0.86f, itemHeight * 1.05f)
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
                if (index == 0) {
                    LandscapeHeaderCard(
                        title = stringResource(R.string.info_title),
                        onNavigateBack = onNavigateBack,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                    )
                } else {
                    ElevatedCard(
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = Color.White, contentColor = Color.Black),
                        elevation = CardDefaults.elevatedCardElevation(),
                    ) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            when (index) {
                            1 -> InfoPrimaryColumn(
                                themeMode, onThemeModeChange, notificationsEnabled,
                                onNotificationsEnabledChange, onShowDonations = { showDonations = true },
                                compactLabels = true,
                            )
                            2 -> InfoBuiltWithColumn(compact = true)
                            3 -> InfoDetailsColumn(
                                applicationExpanded, { applicationExpanded = it },
                                licensesExpanded, { licensesExpanded = it },
                                onLicenseSelected = { selectedLicense = it },
                            )
                            }
                        }
                    }
                }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                InfoPrimaryColumn(
                    themeMode, onThemeModeChange, notificationsEnabled,
                    onNotificationsEnabledChange, onShowDonations = { showDonations = true },
                    compactLabels = false,
                )
                InfoBuiltWithColumn()
                InfoDetailsColumn(
                    applicationExpanded, { applicationExpanded = it },
                    licensesExpanded, { licensesExpanded = it },
                    onLicenseSelected = { selectedLicense = it },
                )
            }
        }
    }

    if (showDonations) {
        ModalBottomSheet(
            onDismissRequest = { showDonations = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.donate_to_transversa), style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = onShareApp, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.share_app))
                }
                Text(stringResource(R.string.support_options), style = MaterialTheme.typography.titleMedium)
                DonationTier(
                    title = R.string.donation_beer,
                    icon = R.drawable.donation_beer,
                    productId = "donation_small",
                    offer = donationState.offers["donation_small"],
                    state = donationState,
                    onDonate = onDonate,
                )
                DonationTier(
                    title = R.string.donation_donut,
                    icon = R.drawable.donation_donut,
                    productId = "donation_medium",
                    offer = donationState.offers["donation_medium"],
                    state = donationState,
                    onDonate = onDonate,
                )
                DonationTier(
                    title = R.string.donation_socks,
                    icon = R.drawable.donation_socks,
                    productId = "donation_socks",
                    offer = donationState.offers["donation_socks"],
                    state = donationState,
                    onDonate = onDonate,
                )
                val purchaseMessage = when (donationState.purchaseStatus) {
                    DonationPurchaseStatus.NONE -> null
                    DonationPurchaseStatus.PENDING -> R.string.donation_pending
                    DonationPurchaseStatus.SUCCESS -> R.string.donation_success
                    DonationPurchaseStatus.FAILED -> R.string.donation_failed
                }
                purchaseMessage?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                TextButton(onClick = { showDonations = false }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }

    selectedLicense?.let { license ->
        val text by produceState(initialValue = "", license.asset) {
            value = withContext(Dispatchers.IO) {
                runCatching { context.assets.open("licenses/${license.asset}").bufferedReader().use { it.readText() } }
                    .getOrElse { context.getString(R.string.license_load_failed) }
            }
        }
        ModalBottomSheet(onDismissRequest = { selectedLicense = null }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(license.title), style = MaterialTheme.typography.headlineSmall)
                Text(text, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { selectedLicense = null }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun InfoPrimaryColumn(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onShowDonations: () -> Unit,
    compactLabels: Boolean,
) {
    val themeModes = AppThemeMode.entries
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)
            .padding(horizontal = if (compactLabels) 8.dp else 16.dp, vertical = 4.dp),
    ) {
        themeModes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = themeMode == mode,
                onClick = { onThemeModeChange(mode) },
                modifier = if (compactLabels) {
                    Modifier.fillMaxHeight().height(56.dp)
                } else {
                    Modifier.fillMaxHeight().heightIn(min = 56.dp)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size),
                icon = {},
                label = {
                    val icon: androidx.compose.ui.graphics.vector.ImageVector? = when (mode) {
                        AppThemeMode.LIGHT -> Icons.Outlined.WbSunny
                        AppThemeMode.DARK -> Icons.Outlined.NightsStay
                        AppThemeMode.SYSTEM -> null
                    }
                    val label = stringResource(when (mode) {
                        AppThemeMode.LIGHT -> R.string.theme_light_mode
                        AppThemeMode.SYSTEM -> R.string.theme_system
                        AppThemeMode.DARK -> R.string.theme_dark_mode
                    })
                    if (compactLabels) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
            )
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = if (compactLabels) 8.dp else 16.dp, vertical = if (compactLabels) 4.dp else 8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        if (compactLabels) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(stringResource(R.string.notifications_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.notifications_description), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Switch(checked = notificationsEnabled, onCheckedChange = onNotificationsEnabledChange)
                }
            }
        } else {
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                supportingContent = { Text(stringResource(R.string.notifications_description)) },
                trailingContent = {
                    Switch(checked = notificationsEnabled, onCheckedChange = onNotificationsEnabledChange)
                },
                content = { Text(stringResource(R.string.notifications_title)) },
            )
        }
    }
    Text(
        text = stringResource(R.string.free_no_ads_no_tracking),
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (compactLabels) 16.dp else 24.dp, vertical = 2.dp),
        style = if (compactLabels) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = onShowDonations,
        modifier = Modifier.fillMaxWidth().height(if (compactLabels) 48.dp else 56.dp)
            .padding(horizontal = if (compactLabels) 8.dp else 16.dp, vertical = if (compactLabels) 4.dp else 8.dp),
        shapes = ButtonDefaults.shapes(),
    ) {
        Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.donate))
    }
}

@Composable
private fun InfoBuiltWithColumn(compact: Boolean = false) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { uriHandler.openUri("https://slopsoft.xyz") },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.slopsoft_logo),
                contentDescription = stringResource(R.string.slopsoft_logo_description),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { uriHandler.openUri("https://openai.com/codex/") },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.codex_color),
                contentDescription = stringResource(R.string.codex_logo_description),
                modifier = Modifier.size(48.dp),
            )
            Text(
                stringResource(R.string.built_with_codex),
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { uriHandler.openUri("https://ffmpeg.org/") },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Movie, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.built_on_ffmpeg),
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun InfoDetailsColumn(
    applicationExpanded: Boolean,
    onApplicationExpandedChange: (Boolean) -> Unit,
    licensesExpanded: Boolean,
    onLicensesExpandedChange: (Boolean) -> Unit,
    onLicenseSelected: (LicenseNotice) -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    val applicationSummary = if (versionName.isNotBlank()) stringResource(R.string.version_number, versionName) else ""
    ExpandableInfoSection(
        title = stringResource(R.string.application_info),
        expanded = applicationExpanded,
        onToggle = { onApplicationExpandedChange(!applicationExpanded) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                supportingContent = { Text(applicationSummary) },
                content = { Text(stringResource(R.string.app_name)) },
            )
            OutlinedButton(
                onClick = { uriHandler.openUri("https://github.com/SoftSlop69/transversa") },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(painter = painterResource(R.drawable.github_mark), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.see_the_source))
            }
        }
    }
    ExpandableInfoSection(
        title = stringResource(R.string.software_licenses),
        expanded = licensesExpanded,
        onToggle = { onLicensesExpandedChange(!licensesExpanded) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            licenseNotices.forEachIndexed { index, license ->
                SegmentedListItem(
                    onClick = { onLicenseSelected(license) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = licenseNotices.size),
                    supportingContent = { Text(stringResource(license.summary)) },
                    content = { Text(stringResource(license.title)) },
                )
            }
        }
    }
}

@Composable
private fun ExpandableInfoSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onToggle)
                        .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = onToggle, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = stringResource(
                                if (expanded) R.string.collapse_info_section else R.string.expand_info_section,
                                title,
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
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DonationTier(
    title: Int,
    icon: Int,
    productId: String,
    offer: DonationOffer?,
    state: DonationBillingState,
    onDonate: (String) -> Unit,
) {
    val displayedPrice = offer?.let {
        it.formattedPrice
    } ?: stringResource(if (state.isLoading) R.string.loading_price else R.string.price_unavailable)
    OutlinedButton(
        onClick = { onDonate(productId) },
        enabled = offer != null,
        modifier = Modifier.fillMaxWidth(),
        shapes = ButtonDefaults.shapes(),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                )
                Text(stringResource(title), modifier = Modifier.weight(1f))
                Text(displayedPrice)
            }
            val detailText = offer?.productDetails?.description?.takeIf(String::isNotBlank)
            if (detailText != null) {
                Text(detailText, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall)
            } else if (offer == null && !state.isLoading) {
                Text(
                    stringResource(R.string.product_unavailable_description),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

