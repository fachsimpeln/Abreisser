package io.github.fachsimpeln.abreisser.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1F6FEB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FE),
    onPrimaryContainer = Color(0xFF0B3A86),
    secondaryContainer = Color(0xFFE8F0FE),
    onSecondaryContainer = Color(0xFF0B3A86),
    background = Color(0xFFEEF0F4),
    surface = Color.White,
    surfaceContainer = Color(0xFFF6F7F9),
    surfaceContainerLow = Color(0xFFF6F7F9),
    onSurface = Color(0xFF161B26),
    onSurfaceVariant = Color(0xFF5D6679),
    outline = Color(0xFFC4C9D2),
    outlineVariant = Color(0xFFDDE1E8),
    errorContainer = Color(0xFFFFF4E5),
    onErrorContainer = Color(0xFF8A4B00),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7AA7F7),
    onPrimary = Color(0xFF00296B),
    primaryContainer = Color(0xFF1A3A6E),
    onPrimaryContainer = Color(0xFFDCE6FC),
    secondaryContainer = Color(0xFF1A3A6E),
    onSecondaryContainer = Color(0xFFDCE6FC),
    background = Color(0xFF111418),
    surface = Color(0xFF181C22),
    errorContainer = Color(0xFF4A2D00),
    onErrorContainer = Color(0xFFFFDDB3),
)

@Composable
fun AbreisserTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
