package com.teamz.lab.debugger.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.teamz.lab.debugger.ui.theme.DesignSystemColors

/**
 * Centralized AI Icon utility
 * 
 * Provides the AI icon (SmartToy) with theme-aware color:
 * - Light mode: DarkII color for visibility
 * - Dark mode: NeonGreen color for visibility
 * 
 * Usage:
 * ```kotlin
 * Icon(
 *     imageVector = AIIcon.icon,
 *     contentDescription = "AI Assistant",
 *     tint = AIIcon.color()
 * )
 * ```
 */
object AIIcon {
    /**
     * The AI icon (SmartToy)
     */
    val icon = Icons.Rounded.SmartToy
    
    /**
     * Get the theme-aware color for the AI icon
     * 
     * @return DarkII in light mode, NeonGreen in dark mode
     */
    @Composable
    fun color(): Color {
        val isDarkMode = MaterialTheme.colorScheme.background == DesignSystemColors.Dark
        return if (isDarkMode) {
            DesignSystemColors.NeonGreen
        } else {
            DesignSystemColors.DarkII
        }
    }
    
    /**
     * Get the theme-aware color for the AI icon (non-composable version)
     * Use this when you need the color outside of a Composable context
     * 
     * @param isDarkMode Whether dark mode is active
     * @return DarkII in light mode, NeonGreen in dark mode
     */
    fun color(isDarkMode: Boolean): Color {
        return if (isDarkMode) {
            DesignSystemColors.NeonGreen
        } else {
            DesignSystemColors.DarkII
        }
    }
}

