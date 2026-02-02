package com.teamz.lab.debugger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.teamz.lab.debugger.ui.theme.DesignSystemColors

// Design System Colors from the image
object DesignSystemColors {
    // Light Theme Colors
    val White = Color(0xFFFFFFFF)
    val Light = Color(0xFFF4F5F5)
    val Border = Color(0xFFDDDDDD)
    val NeonGreen = Color(0xFFD9FE06)
    val Dark = Color(0xFF12151A)

    // Dark Theme Colors
    val DarkII = Color(0xFF1D1F25)
}


// New Design System Theme - Light Mode
val DesignSystemLightColorScheme = lightColorScheme(
    primary = DesignSystemColors.Dark,
    secondary = DesignSystemColors.NeonGreen,
    tertiary = DesignSystemColors.DarkII,
    background = DesignSystemColors.White,
    surface = DesignSystemColors.White,
    surfaceVariant = DesignSystemColors.Light,
    onPrimary = DesignSystemColors.White,
    onSecondary = DesignSystemColors.Dark,
    onTertiary = DesignSystemColors.White,
    onBackground = DesignSystemColors.Dark,
    onSurface = DesignSystemColors.Dark,
    onSurfaceVariant = DesignSystemColors.Dark,
    outline = DesignSystemColors.Border,
    outlineVariant = DesignSystemColors.Border.copy(alpha = 0.5f),
    primaryContainer = DesignSystemColors.Dark.copy(alpha = 0.05f),
    onPrimaryContainer = DesignSystemColors.Dark,
    secondaryContainer = DesignSystemColors.NeonGreen.copy(alpha = 0.1f),
    onSecondaryContainer = DesignSystemColors.Dark,
    tertiaryContainer = DesignSystemColors.NeonGreen,
    onTertiaryContainer = DesignSystemColors.Dark
)

// New Design System Theme - Dark Mode
val DesignSystemDarkColorScheme = darkColorScheme(
    primary = DesignSystemColors.White,
    secondary = DesignSystemColors.White,
    tertiary = DesignSystemColors.Border,
    background = DesignSystemColors.Dark,
    surface = DesignSystemColors.DarkII,
    onPrimary = DesignSystemColors.Dark,
    onSecondary = DesignSystemColors.Dark,
    onTertiary = DesignSystemColors.White,
    onBackground = DesignSystemColors.White,
    onSurface = DesignSystemColors.White,
    outline = DesignSystemColors.Border,
    outlineVariant = DesignSystemColors.Border.copy(alpha = 0.5f),
    surfaceVariant = DesignSystemColors.DarkII,
    onSurfaceVariant = DesignSystemColors.White,
    tertiaryContainer = DesignSystemColors.NeonGreen,
    )

// Theme enum for easy switching
enum class AppTheme {
    DESIGN_SYSTEM_LIGHT,
    DESIGN_SYSTEM_DARK,
}

@Composable
fun DebuggerTheme(
    theme: AppTheme = AppTheme.DESIGN_SYSTEM_DARK, // Default to dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        AppTheme.DESIGN_SYSTEM_LIGHT -> DesignSystemLightColorScheme
        AppTheme.DESIGN_SYSTEM_DARK -> DesignSystemDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

