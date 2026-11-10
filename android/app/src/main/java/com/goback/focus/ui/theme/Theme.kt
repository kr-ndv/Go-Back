package com.goback.focus.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.goback.focus.data.db.GoBackDatabase
import kotlinx.coroutines.flow.catch

private val PaletteColors = mapOf(
    "Forest" to Color(0xFF365C32),
    "Blue" to Color(0xFF2563EB),
    "Violet" to Color(0xFF7C3AED),
    "Amber" to Color(0xFFD97706),
    "Rose" to Color(0xFFE11D48),
    "Teal" to Color(0xFF0D9488),
    "Berry" to Color(0xFF9333EA),
    "Sakura" to Color(0xFFEC4899),
    "Graphite" to Color(0xFF4B5563),
    "Coral" to Color(0xFFEA580C),
    "Lavender" to Color(0xFF8B5CF6),
    "Crimson" to Color(0xFFDC2626)
)

@Composable
fun GoBackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("go_back_preferences", Context.MODE_PRIVATE)
    }
    val initialMode = remember(prefs) { prefs.getString("theme.mode", "system") ?: "system" }
    val initialPalette = remember(prefs) { prefs.getString("theme.palette", "Forest") ?: "Forest" }

    val database = remember(context) {
        runCatching { GoBackDatabase.getDatabase(context) }.getOrNull()
    }
    val modeFlow = remember(database) {
        database?.settingDao()?.observeValue("theme.mode")?.catch { emit(initialMode) }
    }
    val paletteFlow = remember(database) {
        database?.settingDao()?.observeValue("theme.palette")?.catch { emit(initialPalette) }
    }

    val savedModeState = modeFlow?.collectAsState(initial = initialMode)
    val savedPaletteState = paletteFlow?.collectAsState(initial = initialPalette)

    val savedMode = savedModeState?.value ?: initialMode
    val savedPalette = savedPaletteState?.value ?: initialPalette

    val useDarkTheme = when (savedMode) {
        "light" -> false
        "dark" -> true
        else -> darkTheme
    }
    val accent = PaletteColors.entries.firstOrNull { it.key.equals(savedPalette, ignoreCase = true) }?.value
        ?: PaletteColors.getValue("Forest")
    val onAccent = if (accent.luminance() > 0.48f) Color(0xFF171717) else Color.White

    val colorScheme = if (useDarkTheme) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.28f),
            onPrimaryContainer = Color.White,
            secondary = accent,
            onSecondary = onAccent,
            background = ForestBackgroundDark,
            surface = ForestSurfaceDark,
            surfaceVariant = ForestBorderDark,
            onSurface = ForestTextDark,
            onSurfaceVariant = ForestMutedDark,
            outline = ForestBorderDark
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.16f),
            onPrimaryContainer = accent,
            secondary = accent,
            onSecondary = onAccent,
            background = ForestBackgroundLight,
            surface = ForestSurfaceLight,
            surfaceVariant = ForestBorderLight,
            onSurface = ForestTextLight,
            onSurfaceVariant = ForestMutedLight,
            outline = ForestBorderLight
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = M3ExpressiveShapes,
        content = content
    )
}
