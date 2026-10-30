package com.atharva.securevault.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val WarmCoolColorScheme = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = SlateDeep,
    primaryContainer = AmberSurface,
    onPrimaryContainer = AmberSoft,
    secondary = SlateLight,
    onSecondary = SlateDeep,
    secondaryContainer = SlateElevated,
    onSecondaryContainer = WarmIvory,
    tertiary = AmberLight,
    onTertiary = SlateDeep,
    background = SlateDeep,
    onBackground = WarmIvory,
    surface = SlateSurface,
    onSurface = WarmIvory,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = SlateLight,
    outline = SlateHighlight,
    outlineVariant = OutlineVariantDark,
    error = WarmCoral,
    onError = SlateDeep
)

@Composable
fun SecureVaultTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = WarmCoolColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
