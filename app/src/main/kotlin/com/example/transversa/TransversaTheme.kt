// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

enum class AppThemeMode {
    LIGHT,
    SYSTEM,
    DARK;

    companion object {
        fun fromPreference(value: String?): AppThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

private val RobotoFlexTitles = FontFamily(
    Font(
        R.font.roboto_flex,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)
private val TransversaTypography = Typography().let { base ->
    val titleStyle = { style: androidx.compose.ui.text.TextStyle ->
        style.copy(fontFamily = RobotoFlexTitles, fontWeight = FontWeight.Bold)
    }
    base.copy(
        displayLarge = titleStyle(base.displayLarge),
        displayMedium = titleStyle(base.displayMedium),
        displaySmall = titleStyle(base.displaySmall),
        headlineLarge = titleStyle(base.headlineLarge),
        headlineMedium = titleStyle(base.headlineMedium),
        headlineSmall = titleStyle(base.headlineSmall),
        titleLarge = titleStyle(base.titleLarge),
        titleMedium = titleStyle(base.titleMedium),
        titleSmall = titleStyle(base.titleSmall),
    )
}

private val CadmiumGreenLight = lightColorScheme(
    primary = Color(0xFF006B3C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA8F2BB),
    onPrimaryContainer = Color(0xFF002110),
    inversePrimary = Color(0xFF8BD6A2),
    secondary = Color(0xFF506356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E8D5),
    onSecondaryContainer = Color(0xFF0D1F13),
    tertiary = Color(0xFF38656A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBEE),
    onTertiaryContainer = Color(0xFF002022),
    background = Color(0xFFF6FBF6),
    onBackground = Color(0xFF171D18),
    surface = Color(0xFFF6FBF6),
    onSurface = Color(0xFF171D18),
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF404941),
    surfaceTint = Color(0xFF006B3C),
    inverseSurface = Color(0xFF2C322D),
    inverseOnSurface = Color(0xFFEDF2ED),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF707970),
    outlineVariant = Color(0xFFC0C9C0),
    scrim = Color(0xFF000000),
)

private val CadmiumGreenDark = darkColorScheme(
    primary = Color(0xFF8BD6A2),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF00522D),
    onPrimaryContainer = Color(0xFFA8F2BB),
    inversePrimary = Color(0xFF006B3C),
    secondary = Color(0xFFB7CCB8),
    onSecondary = Color(0xFF233528),
    secondaryContainer = Color(0xFF394B3D),
    onSecondaryContainer = Color(0xFFD3E8D5),
    tertiary = Color(0xFFA0CFD3),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF1F4D52),
    onTertiaryContainer = Color(0xFFBCEBEE),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDEE4DE),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDEE4DE),
    surfaceVariant = Color(0xFF404941),
    onSurfaceVariant = Color(0xFFC0C9C0),
    surfaceTint = Color(0xFF8BD6A2),
    inverseSurface = Color(0xFFDEE4DE),
    inverseOnSurface = Color(0xFF2C322D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8A938A),
    outlineVariant = Color(0xFF404941),
    scrim = Color(0xFF000000),
)

@Composable
fun TransversaTheme(themeMode: AppThemeMode = AppThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val useDarkTheme = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) CadmiumGreenDark else CadmiumGreenLight,
        typography = TransversaTypography,
        content = content,
    )
}
