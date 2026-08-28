package com.projectsuperhuman.next

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Native appearance modes. SYSTEM is the default so Project Superhuman follows Android without
 * requiring a separate app toggle. LIGHT and DARK remain available as explicit user overrides.
 */
internal enum class SuperhumanThemeMode { SYSTEM, LIGHT, DARK }

/** Persistent appearance state shared by every native Project Superhuman screen. */
internal object SuperhumanAppearance {
    private const val PREFS = "project_superhuman_appearance"
    private const val THEME_MODE_KEY = "theme_mode"
    private const val LEGACY_DARK_MODE_KEY = "dark_mode"

    var themeMode by mutableStateOf(SuperhumanThemeMode.SYSTEM)
        private set

    /*
     * This is intentionally a plain value rather than independent Compose state. ProjectSuperhumanTheme
     * resolves it from isSystemInDarkTheme() before composing the screen tree, so all of the existing
     * semantic palette getters see the same effective appearance during that composition.
     */
    private var effectiveDarkMode = false
    val darkMode: Boolean get() = effectiveDarkMode
    val followsSystem: Boolean get() = themeMode == SuperhumanThemeMode.SYSTEM

    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(THEME_MODE_KEY, null)

        themeMode = when {
            stored != null -> runCatching { SuperhumanThemeMode.valueOf(stored) }
                .getOrDefault(SuperhumanThemeMode.SYSTEM)
            // Previous builds stored false by default, which unintentionally forced light mode.
            // Preserve an intentional old dark=true choice, but migrate false/missing to SYSTEM.
            prefs.getBoolean(LEGACY_DARK_MODE_KEY, false) -> SuperhumanThemeMode.DARK
            else -> SuperhumanThemeMode.SYSTEM
        }

        effectiveDarkMode = resolved(systemDarkFromConfiguration(appContext), themeMode)
        prefs.edit()
            .putString(THEME_MODE_KEY, themeMode.name)
            .remove(LEGACY_DARK_MODE_KEY)
            .apply()
        initialized = true
    }

    fun setThemeMode(context: Context, mode: SuperhumanThemeMode) {
        themeMode = mode
        effectiveDarkMode = resolved(systemDarkFromConfiguration(context.applicationContext), mode)
        initialized = true
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(THEME_MODE_KEY, mode.name)
            .remove(LEGACY_DARK_MODE_KEY)
            .apply()
    }

    /**
     * Compatibility entry point for the existing Settings switch. If the requested state matches the
     * device, we return to SYSTEM rather than creating an unnecessary permanent override.
     */
    fun setDarkMode(context: Context, enabled: Boolean) {
        val systemDark = systemDarkFromConfiguration(context.applicationContext)
        val mode = when {
            enabled == systemDark -> SuperhumanThemeMode.SYSTEM
            enabled -> SuperhumanThemeMode.DARK
            else -> SuperhumanThemeMode.LIGHT
        }
        setThemeMode(context, mode)
    }

    fun followSystem(context: Context) = setThemeMode(context, SuperhumanThemeMode.SYSTEM)

    /** Resolve the effective theme for this composition and make it visible to semantic getters. */
    fun resolve(systemDark: Boolean): Boolean {
        effectiveDarkMode = resolved(systemDark, themeMode)
        return effectiveDarkMode
    }

    private fun resolved(systemDark: Boolean, mode: SuperhumanThemeMode): Boolean = when (mode) {
        SuperhumanThemeMode.SYSTEM -> systemDark
        SuperhumanThemeMode.LIGHT -> false
        SuperhumanThemeMode.DARK -> true
    }

    private fun systemDarkFromConfiguration(context: Context): Boolean {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
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

/* Keep the existing light appearance pixel-compatible with the current app. */
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

/** Semantic palette for the app's custom Compose UI. */
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
    val darkTheme = SuperhumanAppearance.resolve(isSystemInDarkTheme())
    val palette = if (darkTheme) DarkSuperhumanPalette else LightSuperhumanPalette

    val scheme = if (darkTheme) {
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
            outlineVariant = palette.divider,
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
            outlineVariant = palette.divider,
            error = palette.red,
            onError = Color.White
        )
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
