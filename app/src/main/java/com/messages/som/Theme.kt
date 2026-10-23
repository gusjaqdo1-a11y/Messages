package com.messages.som

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MessagesDarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF0A305F),
    primaryContainer = Color(0xFF1D4678),
    onPrimaryContainer = Color(0xFFD6E7FF),
    secondary = Color(0xFFBEC6D7),
    background = Color(0xFF090B0F),
    surface = Color(0xFF11151B),
    surfaceVariant = Color(0xFF20242C),
    onBackground = Color(0xFFE5E7EB),
    onSurface = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFFC2C7D0)
)

@Composable
fun MessagesSomTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MessagesDarkColors, content = content)
}
