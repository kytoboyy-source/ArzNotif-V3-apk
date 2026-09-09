package com.arznotif.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0879F9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF003B7A),
    secondary = Color(0xFF10B981),
    onSecondary = Color.White,
    tertiary = Color(0xFFFFB000),
    background = Color(0xFFF5F8FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEFF4FA),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    error = Color(0xFFEF4444)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF65A9FF),
    secondary = Color(0xFF34D399),
    tertiary = Color(0xFFFFC857),
    background = Color(0xFF07111F),
    surface = Color(0xFF0E1B2B),
    surfaceVariant = Color(0xFF15263B),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    error = Color(0xFFF87171)
)

@Composable
fun ArzNotifTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
