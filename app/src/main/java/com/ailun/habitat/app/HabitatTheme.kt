package com.ailun.habitat.app

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── Habitat Teal Palette ──
private val Teal50 = Color(0xFFF0FDFA)
private val Teal100 = Color(0xFFCCFBF1)
private val Teal400 = Color(0xFF2DD4BF)
private val Teal600 = Color(0xFF0D9488)
private val Teal800 = Color(0xFF115E59)
private val Teal900 = Color(0xFF134E4A)
private val Cyan400 = Color(0xFF22D3EE)
private val Cyan600 = Color(0xFF0891B2)
private val Amber400 = Color(0xFFFBBF24)
private val Amber600 = Color(0xFFD97706)

private val Gray50 = Color(0xFFFAFAFA)
private val Gray100 = Color(0xFFF5F5F5)
private val Gray200 = Color(0xFFE5E5E5)
private val Gray300 = Color(0xFFD4D4D4)
private val Gray600 = Color(0xFF525252)
private val Gray800 = Color(0xFF262626)
private val Gray900 = Color(0xFF171717)
private val SurfaceDark = Color(0xFF1E1E1E)
private val SurfaceVariantDark = Color(0xFF2A2A2A)
private val OutlineDark = Color(0xFF3A3A3A)

private val HabitatLight = lightColorScheme(
    primary = Teal600, onPrimary = Color.White, primaryContainer = Teal100, onPrimaryContainer = Teal900,
    secondary = Cyan600, onSecondary = Color.White, secondaryContainer = Color(0xFFCFFAFE), onSecondaryContainer = Color(0xFF164E63),
    tertiary = Amber600, onTertiary = Color.White, tertiaryContainer = Color(0xFFFEF3C7), onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFDC2626), errorContainer = Color(0xFFFEE2E2),
    background = Gray50, onBackground = Gray900, surface = Color.White, onSurface = Gray900,
    surfaceVariant = Gray100, onSurfaceVariant = Gray600, outline = Gray300, outlineVariant = Gray200,
    inverseSurface = Gray800, inverseOnSurface = Gray50
)

private val HabitatDark = darkColorScheme(
    primary = Teal400, onPrimary = Teal900, primaryContainer = Teal800, onPrimaryContainer = Teal100,
    secondary = Cyan400, onSecondary = Color(0xFF164E63), secondaryContainer = Color(0xFF155E75), onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Amber400, onTertiary = Color(0xFF78350F), tertiaryContainer = Color(0xFF92400E), onTertiaryContainer = Color(0xFFFEF3C7),
    error = Color(0xFFFCA5A5), errorContainer = Color(0xFF7F1D1D),
    background = Gray900, onBackground = Gray50, surface = SurfaceDark, onSurface = Gray50,
    surfaceVariant = SurfaceVariantDark, onSurfaceVariant = Gray300, outline = OutlineDark, outlineVariant = SurfaceVariantDark,
    inverseSurface = Gray50, inverseOnSurface = Gray800
)

/** Habitat 青绿色系主题 */
@Composable
fun habitatColorScheme(darkTheme: Boolean? = null): ColorScheme {
    val isDark = darkTheme ?: isSystemInDarkTheme()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("habitat_prefs", android.content.Context.MODE_PRIVATE)
    val useDynamic = prefs.getBoolean("dynamicColorEnabled", false)

    return when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> HabitatDark
        else -> HabitatLight
    }
}
