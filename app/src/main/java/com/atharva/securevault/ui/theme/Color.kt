package com.atharva.securevault.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// TWO-COLOR WARM & COOL DESIGN SYSTEM
// Color 1 (Cool): Slate Navy & Cool Steel shades for calm, trustworthy depth
// Color 2 (Warm): Amber Gold & Warm Sand/Ivory shades for human warmth & focus
// ============================================================================

// --- Cool Palette: Slate Navy Shades (Darker to Lighter) ---
val SlateDeep = Color(0xFF0F172A)             // Main app background (Cool Slate 900)
val SlateDark = Color(0xFF162032)             // Recessed surface / input background
val SlateSurface = Color(0xFF1E293B)          // Primary card surface (Cool Slate 800)
val SlateElevated = Color(0xFF263449)         // Elevated card & resting keypad button
val SlateHighlight = Color(0xFF334155)        // Borders, dividers & subtle containers
val SlateMuted = Color(0xFF64748B)            // Muted labels & helper text
val SlateLight = Color(0xFF94A3B8)            // Secondary body text & keypad letters
val SlateIce = Color(0xFFCBD5E1)              // Cool light text for subtle contrast

// --- Warm Palette: Amber Gold & Warm Ivory Shades (Darker to Lighter) ---
val AmberDeep = Color(0xFF292014)             // Deep warm container tint
val AmberSurface = Color(0xFF3B2D1A)          // Warm badge / icon container surface
val AmberBorder = Color(0xFF785522)           // Subtle warm border accent
val AmberDark = Color(0xFFD97706)             // Deeper warm amber shade
val AmberPrimary = Color(0xFFF59E0B)          // Primary warm amber accent & CTAs
val AmberLight = Color(0xFFFBBF24)            // Lighter warm honey highlight
val AmberSoft = Color(0xFFFDE68A)             // Soft warm sand for tags & active states
val WarmIvory = Color(0xFFFAF8F5)             // Warm primary text (replaces harsh pure white)
val WarmCream = Color(0xFFF3EFEA)             // Secondary warm heading text
val WarmCoral = Color(0xFFF87171)             // Warm alert / error shade

// Semantic mappings for Theme & Screens
val BackgroundDark = SlateDeep
val SurfaceDark = SlateSurface
val SurfaceVariantDark = SlateElevated
val SurfaceElevatedDark = SlateElevated
val OutlineDark = SlateHighlight
val OutlineVariantDark = Color(0xFF243044)

val PrimaryAccent = AmberPrimary
val PrimaryAccentHover = AmberLight
val PrimaryContainerDark = AmberSurface
val OnPrimaryDark = SlateDeep

val TextPrimary = WarmIvory
val TextSecondary = SlateLight
val TextMuted = SlateMuted
