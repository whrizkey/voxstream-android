package com.voxstream.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Indigo500,
    secondary = Emerald500,
    background = Slate950,
    surface = Slate900,
    surfaceVariant = Slate800,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Slate200,
    onSurface = Slate200
)

@Composable
fun VoxStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
