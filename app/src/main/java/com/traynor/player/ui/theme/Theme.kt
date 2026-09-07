package com.traynor.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Dark = darkColorScheme(
    primary = Color(0xFF9BB1FF), onPrimary = Color(0xFF10215B), secondary = Color(0xFF9ED8C4),
    background = Color(0xFF090B10), surface = Color(0xFF111522), surfaceVariant = Color(0xFF1B2030),
    onBackground = Color(0xFFF1F3FA), onSurface = Color(0xFFF1F3FA), error = Color(0xFFFFB4AB)
)
private val Light = lightColorScheme(primary = Color(0xFF3158C7), secondary = Color(0xFF006B58), background = Color(0xFFF9F9FF), surface = Color.White)

private val PlayerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable fun PlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Typography(), shapes = PlayerShapes, content = content)
}
