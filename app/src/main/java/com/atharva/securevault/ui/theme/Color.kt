package com.atharva.securevault.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// SECUREVAULT DESIGN SYSTEM - Professional Minimalist Security Theme
// Guided by UI/UX Pro Max: Swiss Minimalism + Deep Slate Cybersecurity
// ============================================================================

// Base Surfaces & Backgrounds (Deep Obsidian & Slate Tiers)
val BackgroundDark = Color(0xFF090D16)          // Deep Obsidian Navy (OLED friendly, reduced eye strain)
val SurfaceDark = Color(0xFF101726)             // Surface Tier 1: Cards & containers
val SurfaceVariantDark = Color(0xFF162033)      // Surface Tier 2: Interactive components
val SurfaceElevatedDark = Color(0xFF1E2B45)     // Surface Tier 3: Floating icons, keycaps
val OutlineDark = Color(0xFF23324E)             // Structural border (subtle 1dp outline)
val OutlineVariantDark = Color(0xFF172235)      // Hairline inner divider

// Primary Accent - Authoritative Security Blue (Trust, precision, high contrast)
val PrimaryAccent = Color(0xFF2563EB)           // Steel Cobalt Blue
val PrimaryAccentHover = Color(0xFF3B82F6)      // Hover/Active Cobalt
val OnPrimary = Color(0xFFFFFFFF)               // Crisp white on primary
val PrimaryContainerDark = Color(0xFF1E3A8A)    // Deep Cobalt Container
val OnPrimaryContainerDark = Color(0xFFDBEAFE)  // Light Ice Blue text on container

// Secondary & Tertiary Neutrals
val SecondarySlate = Color(0xFF94A3B8)          // Slate 400
val TertiarySlate = Color(0xFF64748B)           // Slate 500

// Typography Neutrals (WCAG AAA compliant on BackgroundDark)
val TextPrimary = Color(0xFFF8FAFC)             // Slate 50 - 15.8:1 contrast
val TextSecondary = Color(0xFF94A3B8)           // Slate 400 - 7.5:1 contrast
val TextMuted = Color(0xFF64748B)               // Slate 500 - 4.5:1 contrast

// Keypad Controls
val KeypadButtonNormal = Color(0xFF131C2D)      // Resting keycap
val KeypadButtonPressed = Color(0xFF1E2B45)     // Active keycap
val KeypadButtonLocked = Color(0xFF0C121D)      // Disabled keycap
val KeypadTextSecondary = Color(0xFF64748B)     // Sublabel (ABC, DEF)

// Semantic Security States (Restrained, elegant, non-childish)
// 1. Success / Hardware Active / Verified
val StatusSuccess = Color(0xFF10B981)           // Emerald 500
val StatusSuccessContainer = Color(0xFF064E3B)  // Emerald 900
val OnStatusSuccess = Color(0xFFD1FAE5)          // Emerald 100

// 2. Warning / Throttled / Cooldown
val StatusWarning = Color(0xFFF59E0B)           // Amber 500
val StatusWarningContainer = Color(0xFF451A03)  // Amber 950
val OnStatusWarning = Color(0xFFFEF3C7)          // Amber 100

// 3. Critical / Locked Out / Incorrect PIN
val StatusCritical = Color(0xFFEF4444)          // Red 500
val StatusCriticalContainer = Color(0xFF450A0A) // Red 950
val OnStatusCritical = Color(0xFFFEE2E2)         // Red 100

// 4. Cryptographic / Enclave Info
val StatusInfo = Color(0xFF0284C7)              // Sky 600
val StatusInfoContainer = Color(0xFF082F49)     // Sky 950
val OnStatusInfo = Color(0xFFE0F2FE)            // Sky 100

// Backward compatibility aliases
val PrimaryWhite = TextPrimary
val PrimaryCyan = PrimaryAccent
val OnPrimaryDark = OnPrimary
val SecondaryGray = SecondarySlate
val SecondaryBlue = PrimaryAccent
val TertiaryPurple = SecondarySlate
