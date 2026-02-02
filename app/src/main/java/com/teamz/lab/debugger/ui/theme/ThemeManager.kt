package com.teamz.lab.debugger.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit

// Theme Manager for easy theme switching
object ThemeManager {
    private const val PREFS_NAME = "theme_preferences"
    private const val KEY_SELECTED_THEME = "selected_theme"
    private const val KEY_IS_DARK_MODE = "is_dark_mode"
    
    private var _currentTheme by mutableStateOf(AppTheme.DESIGN_SYSTEM_DARK)
    private var _isDarkMode by mutableStateOf(true)
    
    val currentTheme: AppTheme get() = _currentTheme
    val isDarkMode: Boolean get() = _isDarkMode
    
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTheme = prefs.getString(KEY_SELECTED_THEME, AppTheme.DESIGN_SYSTEM_DARK.name)
        val savedDarkMode = prefs.getBoolean(KEY_IS_DARK_MODE, true)
        
        _currentTheme = AppTheme.valueOf(savedTheme ?: AppTheme.DESIGN_SYSTEM_DARK.name)
        _isDarkMode = savedDarkMode
    }
    
    fun setTheme(theme: AppTheme, context: Context) {
        _currentTheme = theme
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_SELECTED_THEME, theme.name)
            }
    }
    
    fun setDarkMode(isDark: Boolean, context: Context) {
        _isDarkMode = isDark
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_DARK_MODE, isDark)
            .apply()
    }
    
    fun toggleDarkMode(context: Context) {
        setDarkMode(!_isDarkMode, context)
    }
    
    fun getEffectiveTheme(): AppTheme {
        return when (_currentTheme) {
            AppTheme.DESIGN_SYSTEM_LIGHT -> if (_isDarkMode) AppTheme.DESIGN_SYSTEM_DARK else AppTheme.DESIGN_SYSTEM_LIGHT
            AppTheme.DESIGN_SYSTEM_DARK -> if (_isDarkMode) AppTheme.DESIGN_SYSTEM_DARK else AppTheme.DESIGN_SYSTEM_LIGHT
        }
    }
}

// Composition Local for theme state
val LocalThemeManager = staticCompositionLocalOf { ThemeManager }

// Composable for theme-aware content
@Composable
fun ThemeAwareContent(
    content: @Composable () -> Unit
) {
    val themeManager = LocalThemeManager.current
    val effectiveTheme = themeManager.getEffectiveTheme()
    
    DebuggerTheme(theme = effectiveTheme) {
        content()
    }
}

// Convenience functions for theme switching
@Composable
fun useThemeManager(): ThemeManager {
    return LocalThemeManager.current
}

// Extension functions for easy theme access
fun Context.getThemeManager(): ThemeManager {
    ThemeManager.initialize(this)
    return ThemeManager
}

// Theme switching utilities
fun Context.switchToDesignSystemLight() {
    ThemeManager.setTheme(AppTheme.DESIGN_SYSTEM_LIGHT, this)
}

fun Context.switchToDesignSystemDark() {
    ThemeManager.setTheme(AppTheme.DESIGN_SYSTEM_DARK, this)
}


fun Context.toggleDarkMode() {
    ThemeManager.toggleDarkMode(this)
} 