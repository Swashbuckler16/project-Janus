package com.atharva.securevault.ui.theme

import androidx.compose.ui.graphics.Color

// Core Theme - Minimalist Dichromatic Palette (Shades of Black, Graphite & Crisp White)
val BackgroundDark = Color(0xFF000000)        // Pure OLED Black
val SurfaceDark = Color(0xFF0D0D0D)           // Ultra Deep Onyx
val SurfaceVariantDark = Color(0xFF161616)    // Refined Dark Charcoal
val SurfaceElevatedDark = Color(0xFF202020)   // Elevated Slate
val OutlineDark = Color(0xFF2C2C2C)           // Subtle Structural Border
val OutlineVariantDark = Color(0xFF1A1A1A)    // Hairline Separator

// Monochromatic Accent & Contrast System (Pure Crisp White & Grayscale)
val PrimaryWhite = Color(0xFFFFFFFF)
val PrimaryCyan = Color(0xFFFFFFFF)           // Alias for backward compatibility (strictly white)
val OnPrimaryDark = Color(0xFF000000)
val PrimaryContainerDark = Color(0xFF1E1E1E)
val OnPrimaryContainerDark = Color(0xFFFFFFFF)

val SecondaryGray = Color(0xFFD4D4D4)
val SecondaryBlue = Color(0xFFD4D4D4)         // Alias strictly grayscale
val TertiaryPurple = Color(0xFFA3A3A3)        // Alias strictly grayscale

// Semantic States (Strictly Dichromatic - No bright red, green, blue, purple, or amber)
val StatusSuccess = Color(0xFFFFFFFF)
val StatusSuccessContainer = Color(0xFF1C1C1C)
val OnStatusSuccess = Color(0xFFFFFFFF)

val StatusWarning = Color(0xFFD4D4D4)
val StatusWarningContainer = Color(0xFF1C1C1C)
val OnStatusWarning = Color(0xFFFFFFFF)

val StatusCritical = Color(0xFFFFFFFF)
val StatusCriticalContainer = Color(0xFF222222)
val OnStatusCritical = Color(0xFFFFFFFF)

// Keypad & Tactile Controls
val KeypadButtonNormal = Color(0xFF111111)
val KeypadButtonPressed = Color(0xFF262626)
val KeypadButtonLocked = Color(0xFF080808)
val KeypadTextSecondary = Color(0xFF737373)

// High-Fidelity Typography Neutrals
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA3A3A3)
val TextMuted = Color(0xFF555555)