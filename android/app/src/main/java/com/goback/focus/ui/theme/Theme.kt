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

data class PaletteColorsDef(
    val light: Color,
    val dark: Color,
    val secondary: Color
)

val ThemePalettes = mapOf(
    "Forest" to PaletteColorsDef(Color(0xFF365C32), Color(0xFFB8F36B), Color(0xFFD9F7B5)),
    "Forest Lime" to PaletteColorsDef(Color(0xFF365C32), Color(0xFFB8F36B), Color(0xFFD9F7B5)),
    "Blue" to PaletteColorsDef(Color(0xFF245CA8), Color(0xFF91C2FF), Color(0xFFC4DDFF)),
    "Ocean Blue" to PaletteColorsDef(Color(0xFF245CA8), Color(0xFF91C2FF), Color(0xFFC4DDFF)),
    "Violet" to PaletteColorsDef(Color(0xFF6542A6), Color(0xFFD0B8FF), Color(0xFFE5D8FF)),
    "Violet Pulse" to PaletteColorsDef(Color(0xFF6542A6), Color(0xFFD0B8FF), Color(0xFFE5D8FF)),
    "Amber" to PaletteColorsDef(Color(0xFF875000), Color(0xFFFFD18A), Color(0xFFFFE3BA)),
    "Sunset Amber" to PaletteColorsDef(Color(0xFF875000), Color(0xFFFFD18A), Color(0xFFFFE3BA)),
    "Rose" to PaletteColorsDef(Color(0xFFA33D62), Color(0xFFFFB1C8), Color(0xFFFFD9E3)),
    "Rose Quartz" to PaletteColorsDef(Color(0xFFA33D62), Color(0xFFFFB1C8), Color(0xFFFFD9E3)),
    "Teal" to PaletteColorsDef(Color(0xFF006A68), Color(0xFF72DAD5), Color(0xFFB9F1EC)),
    "Arctic Teal" to PaletteColorsDef(Color(0xFF006A68), Color(0xFF72DAD5), Color(0xFFB9F1EC)),
    "Berry" to PaletteColorsDef(Color(0xFF8A3478), Color(0xFFF6A9E8), Color(0xFFFAD7F1)),
    "Midnight Berry" to PaletteColorsDef(Color(0xFF8A3478), Color(0xFFF6A9E8), Color(0xFFFAD7F1)),
    "Sakura" to PaletteColorsDef(Color(0xFFA33B78), Color(0xFFFFB0D8), Color(0xFFFFD8E9)),
    "Sakura Pink" to PaletteColorsDef(Color(0xFFA33B78), Color(0xFFFFB0D8), Color(0xFFFFD8E9)),
    "Graphite" to PaletteColorsDef(Color(0xFF42665A), Color(0xFFA8DCC8), Color(0xFFD1F0E3)),
    "Graphite Mint" to PaletteColorsDef(Color(0xFF42665A), Color(0xFFA8DCC8), Color(0xFFD1F0E3)),
    "Coral" to PaletteColorsDef(Color(0xFFB44732), Color(0xFFFFB4A3), Color(0xFFFFDAD2)),
    "Coral Bloom" to PaletteColorsDef(Color(0xFFB44732), Color(0xFFFFB4A3), Color(0xFFFFDAD2)),
    "Lavender" to PaletteColorsDef(Color(0xFF5758A5), Color(0xFFC4C3FF), Color(0xFFE3E1FF)),
    "Lavender Sky" to PaletteColorsDef(Color(0xFF5758A5), Color(0xFFC4C3FF), Color(0xFFE3E1FF)),
    "Crimson" to PaletteColorsDef(Color(0xFFA42D3A), Color(0xFFFFB3B8), Color(0xFFFFDADB)),
    "Crimson Ember" to PaletteColorsDef(Color(0xFFA42D3A), Color(0xFFFFB3B8), Color(0xFFFFDADB))
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
    val initialPalette = remember(prefs) { prefs.getString("theme.palette", "Forest Lime") ?: "Forest Lime" }

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
    val paletteDef = ThemePalettes.entries.firstOrNull { it.key.equals(savedPalette, ignoreCase = true) }?.value
        ?: ThemePalettes.getValue("Forest")
    val accent = if (useDarkTheme) paletteDef.dark else paletteDef.light
    val onAccent = if (accent.luminance() > 0.48f) Color(0xFF10201B) else Color.White

    val colorScheme = if (useDarkTheme) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.20f),
            onPrimaryContainer = accent,
            secondary = paletteDef.secondary,
            onSecondary = Color(0xFF10201B),
            background = ForestBackgroundDark,
            surface = ForestSurfaceDark,
            surfaceVariant = ForestSurfaceElevatedDark,
            onSurface = ForestTextDark,
            onSurfaceVariant = ForestMutedDark,
            outline = ForestBorderDark
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = paletteDef.secondary,
            onPrimaryContainer = accent,
            secondary = paletteDef.secondary,
            onSecondary = Color(0xFF10201B),
            background = ForestBackgroundLight,
            surface = ForestSurfaceLight,
            surfaceVariant = ForestSurfaceElevatedLight,
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
