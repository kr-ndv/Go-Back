package com.goback.focus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = ForestPrimaryLight,
    onPrimary = ForestOnPrimaryLight,
    primaryContainer = ForestPrimaryContainerLight,
    onPrimaryContainer = ForestOnPrimaryContainerLight,
    background = ForestBackgroundLight,
    surface = ForestSurfaceLight,
    onSurface = ForestTextLight,
    surfaceVariant = ForestBorderLight,
    onSurfaceVariant = ForestMutedLight,
    outline = ForestBorderLight
)

private val DarkColorScheme = darkColorScheme(
    primary = ForestPrimaryDark,
    onPrimary = ForestOnPrimaryDark,
    primaryContainer = ForestPrimaryContainerDark,
    onPrimaryContainer = ForestOnPrimaryContainerDark,
    background = ForestBackgroundDark,
    surface = ForestSurfaceDark,
    onSurface = ForestTextDark,
    surfaceVariant = ForestBorderDark,
    onSurfaceVariant = ForestMutedDark,
    outline = ForestBorderDark
)

@Composable
fun GoBackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = M3ExpressiveShapes,
        content = content
    )
}
