package com.seblful.klas.ui.theme

import android.app.UiModeManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = md_primary,
    onPrimary = md_onPrimary,
    primaryContainer = md_primaryContainer,
    onPrimaryContainer = md_onPrimaryContainer,
    secondary = md_secondary,
    onSecondary = md_onSecondary,
    secondaryContainer = md_secondaryContainer,
    onSecondaryContainer = md_onSecondaryContainer,
    tertiary = md_tertiary,
    onTertiary = md_onTertiary,
    tertiaryContainer = md_tertiaryContainer,
    onTertiaryContainer = md_onTertiaryContainer,
    error = md_error,
    onError = md_onError,
    errorContainer = md_errorContainer,
    onErrorContainer = md_onErrorContainer,
    background = md_background,
    onBackground = md_onBackground,
    surface = md_surface,
    onSurface = md_onSurface,
    surfaceVariant = md_surfaceVariant,
    onSurfaceVariant = md_onSurfaceVariant,
    outline = md_outline,
    outlineVariant = md_outlineVariant,
    surfaceDim = md_surfaceDim,
    surfaceBright = md_surfaceBright,
    surfaceContainerLowest = md_surfaceContainerLowest,
    surfaceContainerLow = md_surfaceContainerLow,
    surfaceContainer = md_surfaceContainer,
    surfaceContainerHigh = md_surfaceContainerHigh,
    surfaceContainerHighest = md_surfaceContainerHighest,
    inverseSurface = md_inverseSurface,
    inverseOnSurface = md_inverseOnSurface,
    inversePrimary = md_inversePrimary,
    primaryFixed = md_primaryFixed,
    primaryFixedDim = md_primaryFixedDim,
    onPrimaryFixed = md_onPrimaryFixed,
    onPrimaryFixedVariant = md_onPrimaryFixedVariant,
    secondaryFixed = md_secondaryFixed,
    secondaryFixedDim = md_secondaryFixedDim,
    onSecondaryFixed = md_onSecondaryFixed,
    onSecondaryFixedVariant = md_onSecondaryFixedVariant,
    tertiaryFixed = md_tertiaryFixed,
    tertiaryFixedDim = md_tertiaryFixedDim,
    onTertiaryFixed = md_onTertiaryFixed,
    onTertiaryFixedVariant = md_onTertiaryFixedVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = md_primary_d,
    onPrimary = md_onPrimary_d,
    primaryContainer = md_primaryContainer_d,
    onPrimaryContainer = md_onPrimaryContainer_d,
    secondary = md_secondary_d,
    onSecondary = md_onSecondary_d,
    secondaryContainer = md_secondaryContainer_d,
    onSecondaryContainer = md_onSecondaryContainer_d,
    tertiary = md_tertiary_d,
    onTertiary = md_onTertiary_d,
    tertiaryContainer = md_tertiaryContainer_d,
    onTertiaryContainer = md_onTertiaryContainer_d,
    error = md_error_d,
    onError = md_onError_d,
    errorContainer = md_errorContainer_d,
    onErrorContainer = md_onErrorContainer_d,
    background = md_background_d,
    onBackground = md_onBackground_d,
    surface = md_surface_d,
    onSurface = md_onSurface_d,
    surfaceVariant = md_surfaceVariant_d,
    onSurfaceVariant = md_onSurfaceVariant_d,
    outline = md_outline_d,
    outlineVariant = md_outlineVariant_d,
    surfaceDim = md_surfaceDim_d,
    surfaceBright = md_surfaceBright_d,
    surfaceContainerLowest = md_surfaceContainerLowest_d,
    surfaceContainerLow = md_surfaceContainerLow_d,
    surfaceContainer = md_surfaceContainer_d,
    surfaceContainerHigh = md_surfaceContainerHigh_d,
    surfaceContainerHighest = md_surfaceContainerHighest_d,
    inverseSurface = md_inverseSurface_d,
    inverseOnSurface = md_inverseOnSurface_d,
    inversePrimary = md_inversePrimary_d,
    primaryFixed = md_primaryFixed,
    primaryFixedDim = md_primaryFixedDim,
    onPrimaryFixed = md_onPrimaryFixed,
    onPrimaryFixedVariant = md_onPrimaryFixedVariant,
    secondaryFixed = md_secondaryFixed,
    secondaryFixedDim = md_secondaryFixedDim,
    onSecondaryFixed = md_onSecondaryFixed,
    onSecondaryFixedVariant = md_onSecondaryFixedVariant,
    tertiaryFixed = md_tertiaryFixed,
    tertiaryFixedDim = md_tertiaryFixedDim,
    onTertiaryFixed = md_onTertiaryFixed,
    onTertiaryFixedVariant = md_onTertiaryFixedVariant,
)

/**
 * The colour roles this app needs and [androidx.compose.material3.ColorScheme] has no slot for — currently
 * one: **the green a taught lesson is said in**.
 */
@Immutable
data class KlasColors(
    /** The strong tone — for a *word*: the «Проведено» badge (`classStatusColor`). */
    val success: Color,
    /** The container tone — for a *panel*: what the schedule's swipe reveals behind a lesson card. */
    val successContainer: Color,
    /** What is legible on [successContainer]. */
    val onSuccessContainer: Color,
    /** The amber a lesson **in progress** is badged «Идёт» in (`classPhaseColor`). */
    val live: Color,
    /**
     * Whether the theme in force is the dark one — *the tutor's* answer, for a colour the app mixes itself
     * (an avatar's tint and letter). `isSystemInDarkTheme()` is the phone's, and a light app on a dark phone
     * drew its monograms for the dark.
     */
    val isDark: Boolean,
    /** The wallpaper's primary while dynamic color is on: the hue the app's own colours lean toward. */
    val harmonizeWith: Color? = null,
) {
    /** [color] turned toward the wallpaper's primary under dynamic color, as Material harmonizes custom colours. */
    fun harmonized(color: Color): Color = harmonizeWith?.let { harmonize(color, toward = it) } ?: color
}

/** Material's three contrast levels, chosen in the phone's settings (Android 14+). */
enum class ContrastLevel {
    Standard,
    Medium,
    High,
    ;

    companion object {
        /** `UiModeManager.getContrast()`'s 0…1, split where Material's generated themes split it. */
        fun of(contrast: Float): ContrastLevel = when {
            contrast >= HighFrom -> High
            contrast >= MediumFrom -> Medium
            else -> Standard
        }

        private const val MediumFrom = 0.5f
        private const val HighFrom = 1f
    }
}

private val LightKlasColors = KlasColors(
    success = md_success,
    successContainer = md_successContainer,
    onSuccessContainer = md_onSuccessContainer,
    live = md_live,
    isDark = false,
)

private val DarkKlasColors = KlasColors(
    success = md_success_d,
    successContainer = md_successContainer_d,
    onSuccessContainer = md_onSuccessContainer_d,
    live = md_live_d,
    isDark = true,
)

fun klasColorScheme(darkTheme: Boolean, contrast: ContrastLevel): ColorScheme = when (contrast) {
    ContrastLevel.Standard -> if (darkTheme) DarkColorScheme else LightColorScheme
    ContrastLevel.Medium -> if (darkTheme) DarkMediumContrastScheme else LightMediumContrastScheme
    ContrastLevel.High -> if (darkTheme) DarkHighContrastScheme else LightHighContrastScheme
}

fun klasColors(darkTheme: Boolean, contrast: ContrastLevel): KlasColors = when (contrast) {
    ContrastLevel.Standard -> if (darkTheme) DarkKlasColors else LightKlasColors
    ContrastLevel.Medium -> if (darkTheme) DarkMediumKlasColors else LightMediumKlasColors
    ContrastLevel.High -> if (darkTheme) DarkHighKlasColors else LightHighKlasColors
}

private val LocalKlasColors = staticCompositionLocalOf { LightKlasColors }

/**
 * The app's own colour roles, resolved for the theme in force — the counterpart of
 * `MaterialTheme.colorScheme`, and read the same way at the point of use.
 */
val klasColors: KlasColors
    @Composable @ReadOnlyComposable
    get() = LocalKlasColors.current

/**
 * Whether this phone can derive a scheme from the wallpaper at all — Material You arrived in Android 12,
 * and `minSdk` here is 24.
 */
val dynamicColorAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** The phone's contrast setting, followed live: it changes without a configuration change. */
@Composable
fun systemContrast(): ContrastLevel {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return ContrastLevel.Standard
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(UiModeManager::class.java) }
    var contrast by remember(manager) { mutableFloatStateOf(manager.contrast) }
    DisposableEffect(manager) {
        val listener = UiModeManager.ContrastChangeListener { contrast = it }
        manager.addContrastChangeListener(context.mainExecutor, listener)
        onDispose { manager.removeContrastChangeListener(listener) }
    }
    return ContrastLevel.of(contrast)
}

@Composable
fun KlasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default so the app keeps its own identity; opt in to follow the wallpaper (Android 12+).
    dynamicColor: Boolean = false,
    contrast: ContrastLevel = systemContrast(),
    content: @Composable () -> Unit,
) {
    val dynamic = dynamicColor && dynamicColorAvailable
    // Remembered: some sixty colour lookups, and a new scheme each pass would recompose the whole app with
    // every change of a setting. The configuration is a key because a new wallpaper arrives as one; the
    // wallpaper scheme already follows the phone's contrast setting.
    val colorScheme = if (dynamic) {
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        remember(context, configuration, darkTheme, contrast) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
    } else {
        klasColorScheme(darkTheme, contrast)
    }
    val customColors = remember(darkTheme, contrast, dynamic, colorScheme.primary) {
        klasColors(darkTheme, contrast).let { colors ->
            if (!dynamic) return@let colors
            val accent = colorScheme.primary
            colors.copy(
                success = harmonize(colors.success, accent),
                successContainer = harmonize(colors.successContainer, accent),
                onSuccessContainer = harmonize(colors.onSuccessContainer, accent),
                live = harmonize(colors.live, accent),
                harmonizeWith = accent,
            )
        }
    }

    /*
     * The status- and navigation-bar icons follow **the app's** resolved theme, not the phone's.
     *
     * The XML themes state a light/dark answer per configuration, which is right for the launch window
     * and wrong the moment the two disagree: the theme setting is the tutor's (`ThemeChoice`), so a
     * light app forced onto a dark phone was drawing white system icons over its own white background —
     * an invisible clock and an invisible back gesture hint. `darkTheme` here is the answer *after*
     * «follow the system» has been resolved, which is the only place both facts are known.
     *
     * A `SideEffect` rather than a `LaunchedEffect`: this is a write to the window on every successful
     * composition, not work to be cancelled, and it has to have landed before the frame is presented.
     */
    val view = LocalView.current
    val activity = LocalActivity.current
    if (!view.isInEditMode && activity != null) {
        SideEffect {
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalKlasColors provides customColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = klasTypography(),
            shapes = Shapes,
            content = content,
        )
    }
}
