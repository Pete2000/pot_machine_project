package com.example.plccontroller.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF315C52),
        secondary = Color(0xFFC96E37),
        tertiary = Color(0xFF8D5B2A),
        background = Color(0xFFF6F1E8),
        surface = Color(0xFFFFFBF4),
    )

@Composable
fun PlcControllerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
