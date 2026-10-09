package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.telephony.TelephonyManager
import androidx.core.content.edit
import java.util.Locale

/**
 * Chooses the one language the whole app process shows its text in, and hands out contexts
 * that resolve string resources in that language.
 *
 * Choice order (see [decideLanguage]):
 * 1. The person's saved manual choice from the drawer picker.
 * 2. Bangla when the country is Bangladesh or the device's own first language is Bangla.
 * 3. English.
 *
 * Only Bangla and English are offered. The app language is applied through configuration
 * contexts only. It is never pushed into [Locale.setDefault]: dozens of call sites build day
 * keys and numbers with `Locale.getDefault()`, and a Bangla default would write those with
 * Bangla digits. See [pinFormattingLocale].
 */
object LocaleManager {
    private const val PREFS_NAME = "locale_preferences"
    private const val KEY_SELECTED_LANGUAGE = "selected_language"
    private const val COUNTRY_BANGLADESH = "BD"

    /** Default locale for number and date formatting. Not the app language. */
    private val FORMATTING_LOCALE = Locale("en")

    /** Language code for Bangla. */
    const val LANGUAGE_BANGLA = "bn"

    /** Language code for English, the fallback. */
    const val LANGUAGE_ENGLISH = "en"

    /**
     * Every language that has a `values-*` folder. Only [selectableLanguages] can be chosen;
     * the rest stay so a language code restored from another device still parses.
     */
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
            /** Returns the language with this [code], or null when the code is unknown. */
            fun fromCode(code: String): AppLanguage? {
                return values().find { it.code == code }
            }
        }
    }

    /** The languages the drawer picker offers, in display order. */
    val selectableLanguages: List<AppLanguage> = listOf(AppLanguage.BENGALI, AppLanguage.ENGLISH)

    /** The language this process is showing. Resolved once, then changed only by a manual choice. */
    @Volatile
    private var currentCode: String? = null

    /** Application-scoped context in [currentCode], kept so stale contexts can be redirected cheaply. */
    @Volatile
    private var cachedLocalized: Pair<String, Context>? = null

    /**
     * Decides the app language from plain inputs, with no Android access, so it can be unit tested.
     *
     * @param savedChoice the manual choice saved by the picker; anything other than `bn` or `en`
     *   counts as no choice.
     * @param networkIso country of the mobile network the phone is on now (may be blank).
     * @param simIso country of the SIM (may be blank).
     * @param localeCountry country of the device's own locale (may be blank).
     * @param deviceLanguage the device's own first language code.
     * @return [LANGUAGE_BANGLA] or [LANGUAGE_ENGLISH], never anything else.
     */
    fun decideLanguage(
        savedChoice: String?,
        networkIso: String?,
        simIso: String?,
        localeCountry: String?,
        deviceLanguage: String?,
    ): String {
        val saved = savedChoice?.trim()?.lowercase(Locale.ROOT)
        if (saved == LANGUAGE_BANGLA || saved == LANGUAGE_ENGLISH) return saved

        val country = pickCountry(networkIso, simIso, localeCountry)
        if (country == COUNTRY_BANGLADESH) return LANGUAGE_BANGLA
        if (deviceLanguage?.trim()?.lowercase(Locale.ROOT) == LANGUAGE_BANGLA) return LANGUAGE_BANGLA
        return LANGUAGE_ENGLISH
    }

    /**
     * Returns the first non-blank country signal, upper-cased, or "" when all are blank.
     *
     * Blank matters as much as null: `TelephonyManager.networkCountryIso` returns an empty
     * string, not null, on a phone with no SIM or no signal.
     */
    fun pickCountry(networkIso: String?, simIso: String?, localeCountry: String?): String {
        return sequenceOf(networkIso, simIso, localeCountry)
            .map { it?.trim().orEmpty() }
            .firstOrNull { it.isNotEmpty() }
            ?.uppercase(Locale.ROOT)
            .orEmpty()
    }

    /**
     * Returns the language code the process is showing, resolving it on first use.
     *
     * Safe to call from `attachBaseContext`, before the Application object is attached.
     */
    fun currentLanguageCode(context: Context): String {
        currentCode?.let { return it }
        return synchronized(this) {
            currentCode ?: resolveLanguageCode(context).also { currentCode = it }
        }
    }

    /**
     * Wraps [base] so its string resources resolve in the app language. Call this from
     * `attachBaseContext` of the Application, every Activity and every Service.
     */
    fun wrapContext(base: Context): Context {
        return createLocalizedContext(base, currentLanguageCode(base))
    }

    /**
     * Returns a context whose string resources resolve in the app language.
     *
     * Use it where the context at hand may have been created before the person switched
     * language: a running Service, a BroadcastReceiver, a widget provider, or anything that
     * builds a notification or `RemoteViews`. Returns [context] itself when it is already in
     * the right language. The result is not themed, so do not inflate Activity UI with it.
     */
    fun localizedContext(context: Context): Context {
        val code = currentLanguageCode(context)
        if (languageOf(context.resources) == code) return context
        cachedLocalized?.let { (cachedCode, cached) -> if (cachedCode == code) return cached }
        val root = context.applicationContext ?: context
        return createLocalizedContext(root, code).also { cachedLocalized = code to it }
    }

    /**
     * Returns [base]'s resources, or the app-language resources when [base] was created
     * before a language switch. Meant for `getResources()` overrides in long-lived components
     * (the Application and Services), where [base] is their own base context.
     */
    fun localizedResources(base: Context): Resources {
        val resources = base.resources
        val code = currentCode ?: return resources
        if (languageOf(resources) == code) return resources
        return localizedContext(base).resources
    }

    /**
     * Pins the process default locale to English, as the app has always done on launch.
     *
     * This is for number and date formatting only, never for which strings show. It is
     * re-applied after configuration changes because the framework then resets the default
     * locale from the Application's resources, which now carry the app language.
     */
    fun pinFormattingLocale() {
        if (Locale.getDefault() != FORMATTING_LOCALE) Locale.setDefault(FORMATTING_LOCALE)
    }

    /**
     * Saves the person's manual choice and switches the process to it. The caller recreates
     * the Activity. A language outside [selectableLanguages] is ignored.
     */
    fun setLanguage(context: Context, language: AppLanguage) {
        if (language !in selectableLanguages) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_SELECTED_LANGUAGE, language.code)
        }
        synchronized(this) { currentCode = language.code }
        try {
            com.teamz.lab.debugger.restore.RestoreCredentialManager.scheduleSaveAfterStateChange(
                context,
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid,
            )
        } catch (e: Exception) {
            // Firebase not ready: the choice is already saved locally, only the backup is skipped.
            AppLog.w("LocaleManager", "Could not schedule restore save: ${e.message}")
        }
    }

    /** Used after cross-device restore when only a language code string is available. */
    fun setLanguageFromCodeIfSupported(context: Context, code: String?) {
        if (code.isNullOrEmpty()) return
        val lang = AppLanguage.fromCode(code) ?: return
        setLanguage(context, lang)
    }

    /** Returns the saved manual choice (`bn` or `en`), or null when the person never chose. */
    fun getSavedLanguageCode(context: Context): String? {
        val saved = try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SELECTED_LANGUAGE, null)
        } catch (e: Exception) {
            null
        }
        return saved?.takeIf { code -> selectableLanguages.any { it.code == code } }
    }

    /** Returns the language the app is showing now, for the picker's current value. */
    fun getSelectedLanguage(context: Context): AppLanguage {
        return AppLanguage.fromCode(currentLanguageCode(context)) ?: AppLanguage.ENGLISH
    }

    /** Forgets the manual choice and goes back to picking the language automatically. */
    fun resetToAuto(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_SELECTED_LANGUAGE)
        }
        synchronized(this) { currentCode = resolveLanguageCode(context) }
    }

    /** Returns true when the country signals say the phone is in Bangladesh. */
    fun isInBangladesh(context: Context): Boolean {
        return getCountryCode(context) == COUNTRY_BANGLADESH
    }

    /** Drops the in-memory choice so the next call resolves again. For tests only. */
    internal fun resetForTest() {
        synchronized(this) {
            currentCode = null
            cachedLocalized = null
        }
    }

    private fun resolveLanguageCode(context: Context): String {
        val telephony = telephonyManager(context)
        val deviceLocale = deviceLocale()
        return decideLanguage(
            savedChoice = getSavedLanguageCode(context),
            networkIso = runCatching { telephony?.networkCountryIso }.getOrNull(),
            simIso = runCatching { telephony?.simCountryIso }.getOrNull(),
            localeCountry = deviceLocale.country,
            deviceLanguage = deviceLocale.language,
        )
    }

    /** Country code such as "BD" or "US": network first, then SIM, then device locale. */
    private fun getCountryCode(context: Context): String {
        val telephony = telephonyManager(context)
        return pickCountry(
            runCatching { telephony?.networkCountryIso }.getOrNull(),
            runCatching { telephony?.simCountryIso }.getOrNull(),
            deviceLocale().country,
        )
    }

    private fun telephonyManager(context: Context): TelephonyManager? {
        return runCatching {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        }.getOrNull()
    }

    /**
     * The device's own first locale. Read from the system resources so that neither the app
     * language wrapper nor [pinFormattingLocale] can change the answer.
     */
    private fun deviceLocale(): Locale = Resources.getSystem().configuration.locales[0]

    private fun languageOf(resources: Resources): String = resources.configuration.locales[0].language

    /**
     * Overrides the locale and nothing else, so screen size, night mode and font scale keep
     * following the device.
     */
    private fun createLocalizedContext(base: Context, code: String): Context {
        val override = Configuration().apply { setLocale(Locale(code)) }
        return base.createConfigurationContext(override)
    }
}
