package com.aikipunk.betteraudiobookplayer.presentation.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** App palette. Screens read it through the theme-aware getters below. */
@Immutable
data class AppColors(
    val surface: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceBright: Color,
    val primary: Color,
    val primaryVariant: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentYellow: Color,
    val accentGreen: Color,
    val isDark: Boolean,
)

// Velvet Spoken - warm dark palette. Text colors meet WCAG AA (4.5:1) on every surface up to surfaceContainer
val DarkAppColors = AppColors(
    surface = Color(0xFF161311),
    surfaceContainerLowest = Color(0xFF110D0C),
    surfaceContainerLow = Color(0xFF1F1B19),
    surfaceContainer = Color(0xFF272320),
    surfaceBright = Color(0xFF3D3836),
    primary = Color(0xFFD96B43),
    primaryVariant = Color(0xFFE58158),
    primaryContainer = Color(0xFF381F17),
    // Near-black: white on this terracotta is only 3.4:1
    onPrimary = Color(0xFF1A0B06),
    textPrimary = Color(0xFFF3ECE7),
    textSecondary = Color(0xFFABA19C),
    textMuted = Color(0xFF96908A),
    accentYellow = Color(0xFFF3B464),
    accentGreen = Color(0xFF6FCE93),
    isDark = true,
)

// Warm light palette; primary is darkened so white text on it, and it on surfaces up to surfaceContainer, meet 4.5:1
val LightAppColors = AppColors(
    surface = Color(0xFFFBF6F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3EAE4),
    surfaceContainer = Color(0xFFECE0D9),
    surfaceBright = Color(0xFFD8C9C1),
    primary = Color(0xFFA6441F),
    primaryVariant = Color(0xFFC75A33),
    primaryContainer = Color(0xFFFFDBCF),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF231A16),
    textSecondary = Color(0xFF55483F),
    textMuted = Color(0xFF6E615A),
    accentYellow = Color(0xFF8A5800),
    accentGreen = Color(0xFF276E45),
    isDark = false,
)

/** Builds the app palette from a Material You (wallpaper) color scheme. */
fun appColorsFrom(scheme: ColorScheme, isDark: Boolean): AppColors = AppColors(
    surface = scheme.surface,
    surfaceContainerLowest = scheme.surfaceContainerLowest,
    surfaceContainerLow = scheme.surfaceContainerLow,
    surfaceContainer = scheme.surfaceContainer,
    surfaceBright = scheme.surfaceContainerHighest,
    primary = scheme.primary,
    primaryVariant = scheme.secondary,
    primaryContainer = scheme.primaryContainer,
    onPrimary = scheme.onPrimary,
    textPrimary = scheme.onSurface,
    textSecondary = scheme.onSurfaceVariant,
    textMuted = scheme.outline,
    accentYellow = scheme.tertiary,
    accentGreen = if (isDark) DarkAppColors.accentGreen else LightAppColors.accentGreen,
    isDark = isDark,
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

val SurfaceDark: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
val SurfaceContainerLow: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceContainerLow
val SurfaceContainer: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceContainer
val SurfaceBright: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceBright

val TerracottaPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.primary
val TerracottaPrimaryVariant: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.primaryVariant
val TerracottaContainer: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.primaryContainer
val OnPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.onPrimary

val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textSecondary
val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textMuted

val AccentYellow: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentYellow
val AccentGreen: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentGreen

/** Colors for text and icons drawn over the dark scrim on top of cover art (same in every theme). */
val OverlayText = Color(0xFFF3ECE7)
val OverlayTextSecondary = Color(0xFFD0C6C1)
val OverlayAccent = Color(0xFFF3B464)
