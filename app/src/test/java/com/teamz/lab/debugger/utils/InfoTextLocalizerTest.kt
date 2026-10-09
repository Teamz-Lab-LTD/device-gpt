package com.teamz.lab.debugger.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The phone-info and network-info builders return English, because scores, reports, AI prompts
 * and tests read that text. [InfoTextLocalizer] turns it into the app language only where it is
 * drawn. These tests feed it the real output of the pure builders, so a reworded English line
 * that no longer matches its pattern fails here instead of silently showing English on a Bangla
 * screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InfoTextLocalizerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
        LocaleManager.resetForTest()
        InfoTextLocalizer.resetForTest()
    }

    @After
    fun tearDown() {
        prefs().edit().clear().commit()
        LocaleManager.resetForTest()
        InfoTextLocalizer.resetForTest()
    }

    private fun prefs() = context.getSharedPreferences("locale_preferences", Context.MODE_PRIVATE)

    private fun bangla(english: String): String {
        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.BENGALI)
        return InfoTextLocalizer.localize(context, english)
    }

    /** English words left in [text]. Digits, units inside values and marks do not count. */
    private fun latinWords(text: String): List<String> =
        Regex("[A-Za-z]{2,}").findAll(text).map { it.value }.toList()

    @Test
    fun english_app_gets_the_text_back_untouched() {
        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.ENGLISH)
        val text = "🔋 Battery Status: Battery Full 🎉\n🔌 Charge Type: Not Charging"
        assertEquals(text, InfoTextLocalizer.localize(context, text))
    }

    @Test
    fun label_and_value_rows_are_shown_in_bangla_and_keep_the_phones_own_values() {
        val shown = bangla(
            """
            🔋 Battery Status: Charging at 5.20 W
            🔌 Charge Type: USB ⚡
            🔥 Battery Temp: 31.5°C
            📊 Battery Health: Good ✅
            🔬 Battery Tech: Li-ion
            ⏳ Estimated Full Charge: 12 min left
            🔋 Estimated Cycles (Approximation): ~92 cycles
            📅 Predicted Battery Life Remaining: ~1+ year (Estimated based on capacity)
            """.trimIndent()
        )
        val lines = shown.lines()
        assertEquals("🔋 ব্যাটারি এখন: চার্জ হচ্ছে, 5.20 W গতিতে", lines[0])
        assertEquals("🔥 ব্যাটারি কত গরম: 31.5°C", lines[2])
        assertEquals("📊 ব্যাটারির অবস্থা: ভালো ✅", lines[3])
        assertEquals("⏳ পুরো চার্জ হতে আনুমানিক: আর 12 মিনিট", lines[5])
        assertEquals("🔋 আনুমানিক কতবার চার্জ হয়েছে: প্রায় 92 বার", lines[6])
        // "W" and "Li-ion" are the phone's own values; every other word is Bangla.
        assertEquals(listOf("Li", "ion"), latinWords(shown))
    }

    @Test
    fun yes_no_rows_and_marked_values_are_translated() {
        val shown = bangla(
            "✅ GPS (Satellite) Enabled: Yes\n\n🔆 HDR Support: No\n\n📷 Back Camera: ✅ Available\n" +
                "🔄 Autofocus: ❌ No Autofocus\n🛜 SIM State: ❌ No SIM\n📏 Screen Size: 6.1 inches"
        )
        assertEquals(
            "✅ জিপিএস চালু: হ্যাঁ\n\n🔆 উজ্জ্বল রঙের ভিডিও চলে: না\n\n📷 পেছনের ক্যামেরা: ✅ আছে\n" +
                "🔄 নিজে নিজে ছবি পরিষ্কার করে: ❌ নেই\n🛜 সিমের অবস্থা: ❌ সিম নেই\n📏 স্ক্রিন কত বড়: 6.1 ইঞ্চি",
            shown,
        )
    }

    @Test
    fun every_line_of_the_pure_builders_has_a_bangla_pattern() {
        val built = listOf(
            clipboardLine(29), clipboardLine(28),
            selinuxStatusLine("Enforcing"), selinuxStatusLine("Permissive"), selinuxStatusLine(null),
            deviceAdminLine(emptyList()), deviceAdminLine(listOf("Find Hub")), deviceAdminLine(listOf("Find Hub", "Work")),
            hackabilityLine(rooted = false, usbDebugging = false), hackabilityLine(rooted = true, usbDebugging = false),
            hackabilityLine(rooted = true, usbDebugging = true),
            faceUnlockLine(true), faceUnlockLine(false),
            aiReadinessLine(false, 2.0), aiReadinessLine(true, 8.0), aiReadinessLine(true, 4.0),
            aiReadinessLine(true, 2.0),
            dpiLineFromPing(""), dpiLineFromPing("1 packets transmitted, 1 received, 0% packet loss"),
            dpiLineFromPing("1 packets transmitted, 0 received, 100% packet loss"),
            ispLineFromCodes(204, null), ispLineFromCodes(null, null), ispLineFromCodes(302, 302),
            dnsLineFromAnswers(listOf("8.8.8.8"), listOf("1.1.1.1")), dnsLineFromAnswers(emptyList(), emptyList()),
            dnsLineFromAnswers(listOf("10.0.0.1"), listOf("10.0.0.1")),
            calculateInternetHealthScore(20.0, 5.0, 0, 50.0, 10.0),
            calculateInternetHealthScore(300.0, 80.0, 30, 1.0, 0.5),
            isCameraOrMicActive(context), OTHER_APPS_MIC_NOTE,
        )
        // Names the phone or the person supplied, which no pattern may translate, and "VPN", which the
        // Bangla keeps beside ভিপিএন because that is the word printed in the phone's own settings.
        val ownWords = setOf("Find", "Hub", "Work", "GB", "VPN")
        for (english in built) {
            val shown = bangla(english)
            val left = latinWords(shown).filterNot { it in ownWords }
            assertTrue("still English after localizing:\n$english\n→ $shown", left.isEmpty())
        }
    }

    @Test
    fun scorer_names_details_and_advice_are_translated() {
        for (english in listOf(
            "DNS Integrity", "Your DNS requests are not being redirected",
            "Private DNS active via dns.google", "Storage Encryption", "Device is not rooted",
            "3 app(s) can read all your notifications", "Low", "Moderate", "High",
            "Review: Settings → Accessibility → Installed services",
        )) {
            val shown = bangla(english)
            assertFalse("not translated: $english", shown == english)
        }
        assertEquals("গোপনে ঠিকানা খোঁজা চালু, dns.google দিয়ে", bangla("Private DNS active via dns.google"))
        assertEquals("4 বারে 3 বার খুলেছে, গড়ে 240ms", bangla("3/4 OK, avg 240ms"))
        assertEquals("4 বারে একবারও খোলেনি", bangla("0/4 OK"))
    }

    @Test
    fun a_finding_with_app_names_keeps_the_names_and_translates_the_rest() {
        val shown = bangla("2 app(s) were installed from outside an app store: Foo (com.foo) — installed by com.bar; Baz (com.baz) — no install source recorded")
        assertEquals(
            "2 টা অ্যাপ অ্যাপের দোকানের বাইরে থেকে বসানো: Foo (com.foo) — com.bar থেকে বসানো; " +
                "Baz (com.baz) — কোথা থেকে এসেছে জানা নেই",
            shown,
        )
    }

    @Test
    fun text_with_no_pattern_is_shown_as_the_phone_reported_it() {
        assertEquals("🌡️ cpu-0-0-usr: 34.2°C", bangla("🌡️ cpu-0-0-usr: 34.2°C"))
        assertEquals("arm64-v8a, armeabi-v7a", bangla("arm64-v8a, armeabi-v7a"))
    }

    @Test
    fun system_log_lines_are_left_alone_in_exact_mode() {
        LocaleManager.setLanguage(context, LocaleManager.AppLanguage.BENGALI)
        val log = "10-09 09:14:02.123 1234 1234 E Foo: Error: No\n10-09 09:14:02.200 I Bar: Enabled"
        assertEquals(log, InfoTextLocalizer.localize(context, log, exactOnly = true))
        assertEquals(
            "এই ফোন খবরগুলো দেখতে দেয় না",
            InfoTextLocalizer.localize(context, "Logcat access restricted", exactOnly = true),
        )
    }

    @Test
    fun premium_teaser_keeps_the_star_the_list_splits_on() {
        val shown = bangla("✅ Every installed app shows an icon in your app drawer.\n\n⭐ Unlock Premium to see full hidden apps list & removal guide")
        assertTrue(shown, shown.contains("\n\n⭐ "))
        assertTrue(shown, latinWords(shown).isEmpty())
    }
}
