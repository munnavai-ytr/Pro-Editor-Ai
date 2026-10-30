package com.example.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// CapCut-inspired premium dark studio palette
val NexaBlack = Color(0xFF0A0A0A)
val NexaSurface = Color(0xFF151515)
val NexaSurfaceElevated = Color(0xFF1E1E1E)
val NexaTrackBg = Color(0xFF111114)
val NexaTrackLane = Color(0xFF191A20)
val NexaBorderSubtle = Color(0xFF2A2B33)

// Vivid Cyan -> Purple primary accent
val NexaCyan = Color(0xFF00F0FF)
val NexaPurple = Color(0xFF7C3AED)
val NexaCyanDim = Color(0x3300F0FF)
val NexaPurpleDim = Color(0x337C3AED)

// Destructive / Record secondary accent
val NexaRed = Color(0xFFFF4D4D)
val NexaRedDim = Color(0x2EFF4D4D)
val NexaSuccess = Color(0xFF10B981)

// Text hierarchy
val NexaTextPrimary = Color(0xFFFFFFFF)
val NexaTextSecondary = Color(0xFFA0A0A0)
val NexaTextDisabled = Color(0xFF6B6B6B)

val NexaAccentGradient = Brush.horizontalGradient(
  colors = listOf(NexaCyan, NexaPurple)
)

val NexaDiagonalGradient = Brush.linearGradient(
  colors = listOf(NexaCyan, NexaPurple),
  start = Offset.Zero,
  end = Offset(400f, 400f)
)
