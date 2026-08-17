package com.projectsuperhuman.next

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Persistent appearance state shared by every native Project Superhuman screen. */
internal object SuperhumanAppearance {
    private const val PREFS = "project_superhuman_appearance"
    private const val DARK_MODE_KEY = "dark_mode"

    var darkMode by mutableStateOf(false)
        private set

    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        darkMode = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(DARK_MODE_KEY, false)
        initialized = true
    }

    fun setDarkMode(context: Context, enabled: Boolean) {
        darkMode = enabled
        initialized = true
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(DARK_MODE_KEY, enabled)
            .apply()
    }
}

internal data class SuperhumanPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceSoft: Color,
    val textPrimary: Color,
    val brandText: Color,
    val textMuted: Color,
    val border: Color,
    val divider: Color,
    val accent: Color,
    val accentSoft: Color,
    val blue: Color,
    val green: Color,
    val red: Color,
    val warningSurface: Color,
    val errorSurface: Color
)

private val LightSuperhumanPalette = SuperhumanPalette(
    background = Color(0xFFF8FBFD),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFCFDFE),
    surfaceSoft = Color(0xFFF2F7FA),
    textPrimary = Color(0xFF16334E),
    brandText = Color(0xFF123D70),
    textMuted = Color(0xFF748294),
    border = Color(0xFFE1E8EE),
    divider = Color(0xFFE7EDF2),
    accent = Color(0xFF1CC8C8),
    accentSoft = Color(0xFFE8FAFA),
    blue = Color(0xFF0D6CB4),
    green = Color(0xFF168A78),
    red = Color(0xFFCA3A3A),
    warningSurface = Color(0xFFFFF8E8),
    errorSurface = Color(0xFFFFF1F1)
)

private val DarkSuperhumanPalette = SuperhumanPalette(
    background = Color(0xFF07111B),
    surface = Color(0xFF0E1B27),
    surfaceElevated = Color(0xFF122230),
    surfaceSoft = Color(0xFF152532),
    textPrimary = Color(0xFFEAF2F8),
    brandText = Color(0xFFD7E9F7),
    textMuted = Color(0xFF96A8B8),
    border = Color(0xFF263A4A),
    divider = Color(0xFF203341),
    accent = Color(0xFF35D3D0),
    accentSoft = Color(0xFF123537),
    blue = Color(0xFF68B8F0),
    green = Color(0xFF59C8AF),
    red = Color(0xFFFF8B8B),
    warningSurface = Color(0xFF302A18),
    errorSurface = Color(0xFF351D23)
)

/**
 * Semantic palette for the app's custom Compose UI. Reading this inside composition tracks the
 * appearance state, so existing screens can migrate without each owning another theme state.
 */
internal val superhumanPalette: SuperhumanPalette
    get() = if (SuperhumanAppearance.darkMode) DarkSuperhumanPalette else LightSuperhumanPalette

internal val superhumanBackground: Color get() = superhumanPalette.background
internal val superhumanSurface: Color get() = superhumanPalette.surface
internal val superhumanSurfaceElevated: Color get() = superhumanPalette.surfaceElevated
internal val superhumanSurfaceSoft: Color get() = superhumanPalette.surfaceSoft
internal val superhumanTextPrimary: Color get() = superhumanPalette.textPrimary
internal val superhumanBrandText: Color get() = superhumanPalette.brandText
internal val superhumanTextMuted: Color get() = superhumanPalette.textMuted
internal val superhumanBorder: Color get() = superhumanPalette.border
internal val superhumanDivider: Color get() = superhumanPalette.divider
internal val superhumanAccent: Color get() = superhumanPalette.accent
internal val superhumanAccentSoft: Color get() = superhumanPalette.accentSoft
internal val superhumanBlue: Color get() = superhumanPalette.blue
internal val superhumanGreen: Color get() = superhumanPalette.green
internal val superhumanRed: Color get() = superhumanPalette.red
internal val superhumanWarningSurface: Color get() = superhumanPalette.warningSurface
internal val superhumanErrorSurface: Color get() = superhumanPalette.errorSurface

@Composable
internal fun ProjectSuperhumanTheme(content: @Composable () -> Unit) {
    val palette = superhumanPalette
    val scheme = if (SuperhumanAppearance.darkMode) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF002020),
            secondary = palette.blue,
            onSecondary = Color(0xFF07111B),
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceSoft,
            onSurfaceVariant = palette.textMuted,
            outline = palette.border,
            error = palette.red,
            onError = Color(0xFF270007)
        )
    } else {
        lightColorScheme(
            primary = palette.blue,
            onPrimary = Color.White,
            secondary = palette.accent,
            onSecondary = Color(0xFF002020),
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceSoft,
            onSurfaceVariant = palette.textMuted,
            outline = palette.border,
            error = palette.red,
            onError = Color.White
        )
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
