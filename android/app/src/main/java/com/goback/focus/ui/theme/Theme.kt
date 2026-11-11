package com.goback.focus.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.goback.focus.data.db.GoBackDatabase
import kotlinx.coroutines.flow.catch

data class PaletteColorsDef(
    val light: Color,
    val dark: Color,
    val secondary: Color
)

// Uplifting, vibrant color palettes matching Material 3 Expressive guidelines
val ThemePalettes = mapOf(
    "Forest" to PaletteColorsDef(Color(0xFF2E7D32), Color(0xFFB8F36B), Color(0xFFD9F7B5)),
    "Forest Lime" to PaletteColorsDef(Color(0xFF2E7D32), Color(0xFFB8F36B), Color(0xFFD9F7B5)),
    "Blue" to PaletteColorsDef(Color(0xFF1565C0), Color(0xFF82B1FF), Color(0xFFC4DDFF)),
    "Ocean Blue" to PaletteColorsDef(Color(0xFF1565C0), Color(0xFF82B1FF), Color(0xFFC4DDFF)),
    "Violet" to PaletteColorsDef(Color(0xFF7B1FA2), Color(0xFFD0B8FF), Color(0xFFE5D8FF)),
    "Violet Pulse" to PaletteColorsDef(Color(0xFF7B1FA2), Color(0xFFD0B8FF), Color(0xFFE5D8FF)),
    "Amber" to PaletteColorsDef(Color(0xFFE65100), Color(0xFFFFD180), Color(0xFFFFE3BA)),
    "Sunset Amber" to PaletteColorsDef(Color(0xFFE65100), Color(0xFFFFD180), Color(0xFFFFE3BA)),
    "Rose" to PaletteColorsDef(Color(0xFFC2185B), Color(0xFFFF80AB), Color(0xFFFFD9E3)),
    "Rose Quartz" to PaletteColorsDef(Color(0xFFC2185B), Color(0xFFFF80AB), Color(0xFFFFD9E3)),
    "Teal" to PaletteColorsDef(Color(0xFF00897B), Color(0xFF64FFDA), Color(0xFFB9F1EC)),
    "Arctic Teal" to PaletteColorsDef(Color(0xFF00897B), Color(0xFF64FFDA), Color(0xFFB9F1EC)),
    "Berry" to PaletteColorsDef(Color(0xFF8E24AA), Color(0xFFFF80DF), Color(0xFFFAD7F1)),
    "Midnight Berry" to PaletteColorsDef(Color(0xFF8E24AA), Color(0xFFFF80DF), Color(0xFFFAD7F1)),
    "Sakura" to PaletteColorsDef(Color(0xFFD81B60), Color(0xFFFF85C0), Color(0xFFFFD8E9)),
    "Sakura Pink" to PaletteColorsDef(Color(0xFFD81B60), Color(0xFFFF85C0), Color(0xFFFFD8E9)),
    "Graphite" to PaletteColorsDef(Color(0xFF00796B), Color(0xFFA7FFEB), Color(0xFFD1F0E3)),
    "Graphite Mint" to PaletteColorsDef(Color(0xFF00796B), Color(0xFFA7FFEB), Color(0xFFD1F0E3)),
    "Coral" to PaletteColorsDef(Color(0xFFD84315), Color(0xFFFF8A80), Color(0xFFFFDAD2)),
    "Coral Bloom" to PaletteColorsDef(Color(0xFFD84315), Color(0xFFFF8A80), Color(0xFFFFDAD2)),
    "Lavender" to PaletteColorsDef(Color(0xFF3949AB), Color(0xFFB388FF), Color(0xFFE3E1FF)),
    "Lavender Sky" to PaletteColorsDef(Color(0xFF3949AB), Color(0xFFB388FF), Color(0xFFE3E1FF)),
    "Crimson" to PaletteColorsDef(Color(0xFFC62828), Color(0xFFFF8A80), Color(0xFFFFDADB)),
    "Crimson Ember" to PaletteColorsDef(Color(0xFFC62828), Color(0xFFFF8A80), Color(0xFFFFDADB))
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

    val isMonochrome = savedMode.equals("monochrome", ignoreCase = true)
    val useDarkTheme = when (savedMode) {
        "light" -> false
        "dark" -> true
        "monochrome" -> darkTheme
        else -> darkTheme
    }

    val colorScheme = if (isMonochrome) {
        if (useDarkTheme) {
            darkColorScheme(
                primary = MonoPrimaryDark,
                onPrimary = MonoOnPrimaryDark,
                primaryContainer = Color(0xFF2A2A2A),
                onPrimaryContainer = MonoPrimaryDark,
                secondary = Color(0xFF9E9E9E),
                onSecondary = Color(0xFF121212),
                background = MonoBackgroundDark,
                surface = MonoSurfaceDark,
                surfaceVariant = MonoSurfaceElevatedDark,
                onSurface = MonoTextDark,
                onSurfaceVariant = MonoMutedDark,
                outline = MonoBorderDark
            )
        } else {
            lightColorScheme(
                primary = MonoPrimaryLight,
                onPrimary = MonoOnPrimaryLight,
                primaryContainer = Color(0xFFE0E0E0),
                onPrimaryContainer = MonoPrimaryLight,
                secondary = Color(0xFF616161),
                onSecondary = Color(0xFFFFFFFF),
                background = MonoBackgroundLight,
                surface = MonoSurfaceLight,
                surfaceVariant = MonoSurfaceElevatedLight,
                onSurface = MonoTextLight,
                onSurfaceVariant = MonoMutedLight,
                outline = MonoBorderLight
            )
        }
    } else {
        val paletteDef = ThemePalettes.entries.firstOrNull { it.key.equals(savedPalette, ignoreCase = true) }?.value
            ?: ThemePalettes.getValue("Forest")
        val accent = if (useDarkTheme) paletteDef.dark else paletteDef.light
        val onAccent = if (accent.luminance() > 0.48f) Color(0xFF10201B) else Color.White

        // Tint the full Material surface system with the selected palette. Keeping the
        // tint subtle preserves the premium dark/light neutrals while making dialogs,
        // menus, sheets, cards and navigation surfaces feel like one coherent theme.
        if (useDarkTheme) {
            val background = lerp(ForestBackgroundDark, accent, 0.055f)
            val surface = lerp(ForestSurfaceDark, accent, 0.075f)
            val elevatedSurface = lerp(ForestSurfaceElevatedDark, accent, 0.12f)
            val outline = lerp(ForestBorderDark, accent, 0.22f)
            darkColorScheme(
                primary = accent,
                onPrimary = onAccent,
                primaryContainer = lerp(Color(0xFF1A342B), accent, 0.28f),
                onPrimaryContainer = accent,
                secondary = paletteDef.secondary,
                onSecondary = Color(0xFF10201B),
                tertiary = paletteDef.secondary,
                onTertiary = Color(0xFF10201B),
                background = background,
                surface = surface,
                surfaceVariant = elevatedSurface,
                onSurface = ForestTextDark,
                onSurfaceVariant = ForestMutedDark,
                outline = outline,
                outlineVariant = lerp(ForestBorderDark, accent, 0.12f),
                surfaceTint = accent,
                inversePrimary = paletteDef.light
            )
        } else {
            val background = lerp(ForestBackgroundLight, accent, 0.025f)
            val surface = lerp(ForestSurfaceLight, accent, 0.018f)
            val elevatedSurface = lerp(ForestSurfaceElevatedLight, accent, 0.045f)
            val outline = lerp(ForestBorderLight, accent, 0.10f)
            lightColorScheme(
                primary = accent,
                onPrimary = onAccent,
                primaryContainer = paletteDef.secondary,
                onPrimaryContainer = accent,
                secondary = paletteDef.secondary,
                onSecondary = Color(0xFF10201B),
                tertiary = paletteDef.secondary,
                onTertiary = Color(0xFF10201B),
                background = background,
                surface = surface,
                surfaceVariant = elevatedSurface,
                onSurface = ForestTextLight,
                onSurfaceVariant = ForestMutedLight,
                outline = outline,
                outlineVariant = lerp(ForestBorderLight, accent, 0.06f),
                surfaceTint = accent,
                inversePrimary = paletteDef.dark
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = M3ExpressiveShapes,
        content = content
    )
}
