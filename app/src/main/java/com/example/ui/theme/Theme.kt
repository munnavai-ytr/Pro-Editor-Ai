package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PlayNexaColorScheme = darkColorScheme(
  primary = NexaCyan,
  onPrimary = NexaBlack,
  primaryContainer = NexaPurple,
  onPrimaryContainer = NexaTextPrimary,
  secondary = NexaPurple,
  onSecondary = NexaTextPrimary,
  secondaryContainer = NexaSurfaceElevated,
  onSecondaryContainer = NexaTextPrimary,
  tertiary = NexaRed,
  onTertiary = NexaTextPrimary,
  background = NexaBlack,
  onBackground = NexaTextPrimary,
  surface = NexaSurface,
  onSurface = NexaTextPrimary,
  surfaceVariant = NexaSurfaceElevated,
  onSurfaceVariant = NexaTextSecondary,
  surfaceContainer = NexaSurface,
  surfaceContainerHigh = NexaSurfaceElevated,
  surfaceContainerHighest = NexaTrackLane,
  outline = NexaBorderSubtle,
  outlineVariant = NexaTextDisabled,
  error = NexaRed,
  onError = NexaTextPrimary
)

val PlayNexaShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(8.dp),       // 8dp for buttons
  medium = RoundedCornerShape(12.dp),     // 12dp for cards
  large = RoundedCornerShape(20.dp),      // 20dp for bottom sheets
  extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PlayNexaTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = PlayNexaColorScheme,
    typography = Typography,
    shapes = PlayNexaShapes,
    content = content
  )
}

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
  PlayNexaTheme(content = content)
}
