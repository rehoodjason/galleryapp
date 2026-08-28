package com.example.smartgallery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF070913)
val CardGlassBg = Color(0x1AFFFFFF)
val CyanPrimary = Color(0xFF22D3EE)
val IndigoSecondary = Color(0xFF818CF8)
val PinkAccent = Color(0xFFF43F5E)

@Composable
fun SmartGalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = BgDark,
            surface = CardGlassBg,
            primary = CyanPrimary,
            secondary = IndigoSecondary
        ),
        content = content
    )
}