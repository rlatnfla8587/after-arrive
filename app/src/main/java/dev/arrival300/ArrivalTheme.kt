package dev.arrival300

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF202428)
val Page = Color(0xFFFDFDFE)
val Panel = Color(0xFFEDF1F6)
val Raised = Color(0xFFC2E6FA)
val Border = Color(0xFFD8DEE6)
val White = Color.White
val Muted = Color(0xFF515A62)
val Blue = Color(0xFF005779)
val Gold = Color(0xFF82510B)
val Indigo = Color(0xFF243F89)
val VioletTile = Color(0xFFD8E1FC)
val HoneyTile = Color(0xFFFFE8A3)
val PeachTile = Color(0xFFFFDACC)
val PeachButton = Color(0xFFFFBBA4)
val PeachInk = Color(0xFF73341C)

private val colors = lightColorScheme(
    primary = Blue,
    onPrimary = White,
    primaryContainer = Raised,
    onPrimaryContainer = Blue,
    secondary = Indigo,
    secondaryContainer = VioletTile,
    onSecondaryContainer = Indigo,
    surface = Page,
    onSurface = Ink,
    surfaceVariant = Panel,
    onSurfaceVariant = Muted,
    surfaceContainerHigh = Panel,
    surfaceContainerHighest = Panel,
    background = Page,
    onBackground = Ink,
    outline = Color(0xFF747D85),
    outlineVariant = Border
)

@Composable
fun ArrivalTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
