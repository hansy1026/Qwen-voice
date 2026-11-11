package com.qwen.tts.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AppColors = lightColorScheme(
    primary = Color(0xFF13ADA0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF2EC),
    onPrimaryContainer = Color(0xFF00695F),
    secondary = Color(0xFF45C4BE),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF5F1),
    background = Color(0xFFF4E6DD),
    onBackground = Color(0xFF18232B),
    surface = Color(0xFFFFF9F7),
    onSurface = Color(0xFF18232B),
    surfaceVariant = Color(0xFFF7EFEC),
    onSurfaceVariant = Color(0xFF5C6971),
    outline = Color(0xFFDBD8D6),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun QwenTtsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, shapes = AppShapes, content = content)
}
