package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.content.edit
import java.util.Locale

/**
 * Locale Manager - Handles app language based on country detection and user preference
 * 
 * Priority:
 * 1. User's manual selection (stored in SharedPreferences)
 * 2. Country-based detection (e.g., BD → Bengali)
 * 3. Device language
 * 4. Default: English
 */
object LocaleManager {
    private const val PREFS_NAME = "locale_preferences"
    private const val KEY_SELECTED_LANGUAGE = "selected_language"
    
    // Supported languages
    enum class AppLanguage(val code: String, val displayName: String) {
        ENGLISH("en", "English"),
        BENGALI("bn", "বাংলা"),
        SPANISH("es", "Español"),
        FRENCH("fr", "Français"),
        GERMAN("de", "Deutsch"),
        CHINESE("zh", "中文"),
        ARABIC("ar", "العربية"),
        HINDI("hi", "हिन्दी"),
        PORTUGUESE("pt", "Português"),
        JAPANESE("ja", "日本語"),
        KOREAN("ko", "한국어"),
        ITALIAN("it", "Italiano"),
        RUSSIAN("ru", "Русский"),
        TURKISH("tr", "Türkçe"),
        DUTCH("nl", "Nederlands"),
        POLISH("pl", "Polski"),
        VIETNAMESE("vi", "Tiếng Việt"),
        THAI("th", "ไทย"),
        INDONESIAN("id", "Bahasa Indonesia");
        
        companion object {
            fun fromCode(code: String): AppLanguage? {
                return values().find { it.code == code }
            }
        }
    }
    
    /**
     * Country to language mapping
     * If user is in this country, use this language by default
     */
    private val countryLanguageMap = mapOf(
        "BD" to "bn", // Bangladesh → Bengali
        "IN" to "hi", // India → Hindi (can be overridden)
        // Add more country mappings as needed
    )
    
    /**
     * Initialize locale on app start
     * Default: English (language selection disabled)
     */
    fun setLocale(context: Context) {
        // Always default to English
        updateLocale(context, "en")
    }
    
    /**
     * Detect language based on country code
     * Returns language code if country matches, null otherwise
     */
    private fun detectLanguageByCountry(context: Context): String? {
        val countryCode = getCountryCode(context)
        return countryLanguageMap[countryCode]
    }
    
    /**
     * Get device country code (e.g., "BD", "US", "IN")
     */
    private fun getCountryCode(context: Context): String {
        return try {
            // Try to get from TelephonyManager (more accurate)
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            telephonyManager?.networkCountryIso?.uppercase() 
                ?: Locale.getDefault().country
        } catch (e: Exception) {
            // Fallback to locale
            Locale.getDefault().country
        }
    }
    
    /**
     * Get device language code
     */
    private fun getDeviceLanguage(context: Context): String {
        return context.resources.configuration.locales[0].language
    }
    
    /**
     * Set user's preferred language (manual selection)
     */
    fun setLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_SELECTED_LANGUAGE, language.code)
        }
        updateLocale(context, language.code)
        com.teamz.lab.debugger.restore.RestoreCredentialManager.scheduleSaveAfterStateChange(
            context,
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid,
        )
    }

    /** Used after cross-device restore when only a language code string is available. */
    fun setLanguageFromCodeIfSupported(context: Context, code: String?) {
        if (code.isNullOrEmpty()) return
        val lang = AppLanguage.fromCode(code) ?: return
        setLanguage(context, lang)
    }
    
    /**
     * Get current selected language
     */
    fun getSelectedLanguage(context: Context): AppLanguage {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val languageCode = prefs.getString(KEY_SELECTED_LANGUAGE, null)
        return AppLanguage.fromCode(languageCode ?: getDeviceLanguage(context)) ?: AppLanguage.ENGLISH
    }
    
    /**
     * Reset to auto-detect (remove manual selection)
     */
    fun resetToAuto(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_SELECTED_LANGUAGE)
        }
        setLocale(context) // Re-detect
    }
    
    /**
     * Update app locale
     */
    private fun updateLocale(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val resources: Resources = context.resources
        val configuration: Configuration = resources.configuration
        configuration.setLocale(locale)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
    }
    
    /**
     * Create a new context with updated locale
     * Use this in activities to apply locale changes
     * Default: English
     */
    fun createContextWithLocale(context: Context): Context {
        // Always use English
        val locale = Locale("en")
        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
            context
        }
    }
    
    /**
     * Check if device is in Bangladesh
     */
    fun isInBangladesh(context: Context): Boolean {
        return getCountryCode(context) == "BD"
    }
}

