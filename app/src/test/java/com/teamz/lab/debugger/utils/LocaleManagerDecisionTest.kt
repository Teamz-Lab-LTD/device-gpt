package com.teamz.lab.debugger.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The language decision is a pure function, so these run without Android. They are also the
 * only proof of the Bangladesh auto-detection: the test emulator reports country "us".
 */
class LocaleManagerDecisionTest {

    private fun decide(
        saved: String? = null,
        network: String? = "",
        sim: String? = "",
        localeCountry: String? = "US",
        deviceLanguage: String? = "en",
    ): String = LocaleManager.decideLanguage(saved, network, sim, localeCountry, deviceLanguage)

    @Test
    fun savedBangla_winsOverNonBangladeshSignals() {
        assertEquals("bn", decide(saved = "bn", network = "us", sim = "us"))
    }

    @Test
    fun savedEnglish_winsOverBangladeshSignals() {
        assertEquals("en", decide(saved = "en", network = "bd", sim = "bd", localeCountry = "BD", deviceLanguage = "bn"))
    }

    @Test
    fun savedGarbage_isTreatedAsNoChoice() {
        assertEquals("en", decide(saved = "es"))
        assertEquals("en", decide(saved = "xx-garbage"))
        assertEquals("en", decide(saved = ""))
        assertEquals("bn", decide(saved = "es", network = "bd"))
    }

    @Test
    fun bangladeshByNetwork_givesBangla() {
        assertEquals("bn", decide(network = "bd", sim = "us"))
        assertEquals("bn", decide(network = "BD"))
    }

    @Test
    fun bangladeshBySimOnly_givesBangla() {
        assertEquals("bn", decide(network = null, sim = "bd"))
    }

    @Test
    fun bangladeshByLocaleOnly_givesBangla() {
        assertEquals("bn", decide(network = "", sim = "", localeCountry = "BD", deviceLanguage = "en"))
    }

    @Test
    fun blankNetwork_fallsThroughToBangladeshSim() {
        assertEquals("bn", decide(network = "", sim = "bd"))
        assertEquals("bn", decide(network = "   ", sim = "bd"))
    }

    @Test
    fun networkCountry_outranksSimAndLocale() {
        assertEquals("en", decide(network = "in", sim = "bd", localeCountry = "BD"))
    }

    @Test
    fun nonBangladesh_withBanglaDeviceLanguage_givesBangla() {
        assertEquals("bn", decide(network = "in", sim = "in", localeCountry = "IN", deviceLanguage = "bn"))
    }

    @Test
    fun nonBangladesh_englishDevice_givesEnglish() {
        assertEquals("en", decide(network = "us", sim = "us", localeCountry = "US", deviceLanguage = "en"))
    }

    @Test
    fun nothingKnown_givesEnglish() {
        assertEquals("en", LocaleManager.decideLanguage(null, null, null, null, null))
        assertEquals("en", LocaleManager.decideLanguage("", "", "", "", ""))
    }

    @Test
    fun otherDeviceLanguages_stayEnglish() {
        assertEquals("en", decide(deviceLanguage = "hi", localeCountry = "IN"))
        assertEquals("en", decide(deviceLanguage = "es", localeCountry = "ES"))
    }

    @Test
    fun pickCountry_skipsBlanksAndUppercases() {
        assertEquals("BD", LocaleManager.pickCountry("", "bd", "US"))
        assertEquals("US", LocaleManager.pickCountry(null, " ", "us"))
        assertEquals("", LocaleManager.pickCountry(null, "", " "))
    }
}
