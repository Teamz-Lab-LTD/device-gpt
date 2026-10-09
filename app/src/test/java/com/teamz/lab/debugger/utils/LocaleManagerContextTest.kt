package com.teamz.lab.debugger.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.R
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Saving the manual choice, and proof that the contexts handed out by [LocaleManager]
 * resolve string resources in the chosen language.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocaleManagerContextTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
        LocaleManager.resetForTest()
    }

    @After
    fun tearDown() {
        prefs().edit().clear().commit()
        LocaleManager.resetForTest()
    }

    private fun prefs() = context.getSharedPreferences("locale_preferences", Context.MODE_PRIVATE)

    @Test
    fun noChoiceSaved_readsBackNull() {
        assertNull(LocaleManager.getSavedLanguageCode(context))
    }

    @Test
    fun savedChoice_isWrittenAndReadBack() {
        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.BENGALI)
        assertEquals("bn", prefs().getString("selected_language", null))
        assertEquals("bn", LocaleManager.getSavedLanguageCode(context))
        assertEquals(LocaleManager.AppLanguage.BENGALI, LocaleManager.getSelectedLanguage(context))

        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.ENGLISH)
        assertEquals("en", LocaleManager.getSavedLanguageCode(context))
        assertEquals(LocaleManager.AppLanguage.ENGLISH, LocaleManager.getSelectedLanguage(context))
    }

    @Test
    fun savedChoice_survivesAProcessRestart() {
        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.BENGALI)
        LocaleManager.resetForTest()
        assertEquals("bn", LocaleManager.currentLanguageCode(context))
    }

    @Test
    fun languageOutsideThePicker_isNotSaved() {
        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.SPANISH)
        assertNull(prefs().getString("selected_language", null))
        assertEquals("en", LocaleManager.currentLanguageCode(context))
    }

    @Test
    fun restoredUnsupportedCode_isTreatedAsNoChoice() {
        // RestoreCredentialManager writes the raw pref first, then calls this.
        prefs().edit().putString("selected_language", "es").commit()
        LocaleManager.setLanguageFromCodeIfSupported(context, "es")
        assertNull(LocaleManager.getSavedLanguageCode(context))
        assertEquals("en", LocaleManager.currentLanguageCode(context))
    }

    @Test
    fun restoredBangla_isApplied() {
        prefs().edit().putString("selected_language", "bn").commit()
        LocaleManager.setLanguageFromCodeIfSupported(context, "bn")
        assertEquals("bn", LocaleManager.currentLanguageCode(context))
    }

    @Test
    fun resetToAuto_forgetsTheChoice() {
        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.BENGALI)
        LocaleManager.resetToAuto(context)
        assertNull(LocaleManager.getSavedLanguageCode(context))
        assertEquals("en", LocaleManager.currentLanguageCode(context))
    }

    @Test
    fun wrappedContext_resolvesStringsInTheChosenLanguage() {
        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.BENGALI)
        assertEquals("ভাষা", LocaleManager.wrapContext(context).getString(R.string.language))

        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.ENGLISH)
        assertEquals("Language", LocaleManager.wrapContext(context).getString(R.string.language))
    }

    @Test
    fun localizedContext_followsASwitchMadeAfterTheContextWasCreated() {
        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.ENGLISH)
        val createdInEnglish = LocaleManager.wrapContext(context)
        assertEquals("Language", LocaleManager.localizedContext(createdInEnglish).getString(R.string.language))

        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.BENGALI)
        assertEquals("ভাষা", LocaleManager.localizedContext(createdInEnglish).getString(R.string.language))
        assertEquals("ভাষা", LocaleManager.localizedResources(createdInEnglish).getString(R.string.language))

        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.ENGLISH)
        assertEquals("Language", LocaleManager.localizedContext(createdInEnglish).getString(R.string.language))
    }

    @Test
    fun resourcesOverride_followsASwitch_asTheApplicationAndServicesUseIt() {
        // MyApplication and the services return localizedResources(baseContext) from
        // getResources(). MyApplication itself cannot start under Robolectric (Firebase).
        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.ENGLISH)
        val base = LocaleManager.wrapContext(context)
        assertEquals("Language", LocaleManager.localizedResources(base).getString(R.string.language))

        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.BENGALI)
        assertEquals("ভাষা", LocaleManager.localizedResources(base).getString(R.string.language))
        assertEquals("বাদ দিন", LocaleManager.localizedResources(base).getString(R.string.cancel))
    }

    @Test
    fun choosingBangla_doesNotChangeTheDefaultFormattingLocale() {
        val before = Locale.getDefault()
        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.BENGALI)
        LocaleManager.wrapContext(context).getString(R.string.language)
        assertEquals(before, Locale.getDefault())
        assertEquals("12", String.format("%d", 12))
    }

    @Test
    @Config(sdk = [28], qualifiers = "bn-rBD")
    fun banglaDevice_withNoChoice_getsBangla() {
        LocaleManager.resetForTest()
        assertEquals("bn", LocaleManager.currentLanguageCode(context))
        assertEquals("ভাষা", LocaleManager.wrapContext(context).getString(R.string.language))
    }

    @Test
    @Config(sdk = [28], qualifiers = "bn-rBD")
    fun banglaDevice_withSavedEnglish_getsEnglish() {
        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.ENGLISH)
        assertEquals("Language", LocaleManager.wrapContext(context).getString(R.string.language))
        assertEquals("Language", LocaleManager.localizedContext(context).getString(R.string.language))
    }
}
