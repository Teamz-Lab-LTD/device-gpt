package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.OfficialPhoneCheck.ImeiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "Is this phone official?" helper: the offline number check, the message BTRC expects, the intents
 * that open the dialer, the SMS app and the browser, who sees the card, and the promises the feature makes
 * about the IMEI (not read, not kept, not sent, no new permission).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OfficialPhoneCheckTest {

    private val validImeis = listOf("490154203237518", "356938035643809")

    // ---- the number ----

    @Test fun `known IMEIs pass the check digit test`() {
        validImeis.forEach { assertTrue(it, OfficialPhoneCheck.isValidImei(it)) }
    }

    @Test fun `one changed digit anywhere fails the check digit test`() {
        for (imei in validImeis) {
            for (position in imei.indices) {
                val changed = imei.toCharArray().also { it[position] = '0' + ((it[position] - '0' + 1) % 10) }
                val number = String(changed)
                assertFalse("$number (digit ${position + 1} changed)", OfficialPhoneCheck.isValidImei(number))
            }
        }
    }

    @Test fun `wrong length, letters and fifteen zeros are not valid`() {
        assertFalse(OfficialPhoneCheck.isValidImei(""))
        assertFalse(OfficialPhoneCheck.isValidImei("49015420323751"))
        assertFalse(OfficialPhoneCheck.isValidImei("4901542032375180"))
        assertFalse(OfficialPhoneCheck.isValidImei("49015420323751a"))
        assertFalse(OfficialPhoneCheck.isValidImei("000000000000000"))
    }

    @Test fun `normalize strips spaces, dashes and slashes and stops at fifteen digits`() {
        assertEquals("490154203237518", OfficialPhoneCheck.normalizeImei("49 015420 323751 8"))
        assertEquals("356938035643809", OfficialPhoneCheck.normalizeImei("35-693803-564380-9"))
        assertEquals("356938035643809", OfficialPhoneCheck.normalizeImei(" 356938/03/564380/9\n"))
        assertEquals("490154203237518", OfficialPhoneCheck.normalizeImei("4901542032375189999"))
        assertEquals("", OfficialPhoneCheck.normalizeImei("IMEI: "))
    }

    @Test fun `normalize turns Bangla digits into plain digits`() {
        assertEquals("490154203237518", OfficialPhoneCheck.normalizeImei("৪৯০১৫৪২০৩২৩৭৫১৮"))
    }

    @Test fun `state follows what has been typed`() {
        assertEquals(ImeiState.EMPTY, OfficialPhoneCheck.imeiState(""))
        assertEquals(ImeiState.TOO_SHORT, OfficialPhoneCheck.imeiState("4901"))
        assertEquals(ImeiState.TOO_SHORT, OfficialPhoneCheck.imeiState("49015420323751"))
        assertEquals(ImeiState.INVALID, OfficialPhoneCheck.imeiState("490154203237519"))
        assertEquals(ImeiState.VALID, OfficialPhoneCheck.imeiState("490154203237518"))
    }

    @Test fun `digits remaining counts down to zero`() {
        assertEquals(15, OfficialPhoneCheck.digitsRemaining(""))
        assertEquals(11, OfficialPhoneCheck.digitsRemaining("4901"))
        assertEquals(0, OfficialPhoneCheck.digitsRemaining("490154203237518"))
    }

    @Test fun `the number is shown in groups of five`() {
        assertEquals("", OfficialPhoneCheck.groupImei(""))
        assertEquals("49015", OfficialPhoneCheck.groupImei("49015"))
        assertEquals("49015 4", OfficialPhoneCheck.groupImei("490154"))
        assertEquals("49015 42032 37518", OfficialPhoneCheck.groupImei("490154203237518"))
    }

    // ---- the message and the intents ----

    @Test fun `the SMS body is KYD, one space, the fifteen digits`() {
        assertEquals("KYD 490154203237518", OfficialPhoneCheck.kydSmsBody("490154203237518"))
    }

    @Test fun `the SMS intent opens a draft to 16002 and sends nothing`() {
        val intent = OfficialPhoneCheck.btrcSmsIntent("490154203237518")
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto:16002", intent.dataString)
        assertEquals("KYD 490154203237518", intent.getStringExtra("sms_body"))
        assertEquals(setOf("sms_body"), intent.extras!!.keySet())
    }

    @Test fun `the show-IMEI intent opens the dialer with the hash signs encoded`() {
        val intent = OfficialPhoneCheck.showImeiIntent()
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel:*%2306%23", intent.dataString)
        assertEquals("*#06#", intent.data!!.schemeSpecificPart)
        assertNull(intent.extras)
    }

    @Test fun `the BTRC dial intent opens the dialer, it does not place a call`() {
        val intent = OfficialPhoneCheck.btrcUssdIntent()
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel:*16161%23", intent.dataString)
        assertEquals("*16161#", intent.data!!.schemeSpecificPart)
        assertNull(intent.extras)
    }

    @Test fun `the web intent opens the NEIR site with no number in the address`() {
        val intent = OfficialPhoneCheck.btrcWebIntent()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://neir.btrc.gov.bd", intent.dataString)
        assertNull(intent.extras)
    }

    @Test fun `about-phone and its fallback open settings pages`() {
        assertEquals(Settings.ACTION_DEVICE_INFO_SETTINGS, OfficialPhoneCheck.aboutPhoneIntent().action)
        assertEquals(Settings.ACTION_SETTINGS, OfficialPhoneCheck.settingsIntent().action)
    }

    @Test fun `every intent can be started from a non-activity context`() {
        listOf(
            OfficialPhoneCheck.showImeiIntent(),
            OfficialPhoneCheck.btrcUssdIntent(),
            OfficialPhoneCheck.btrcSmsIntent("490154203237518"),
            OfficialPhoneCheck.btrcWebIntent(),
            OfficialPhoneCheck.aboutPhoneIntent(),
            OfficialPhoneCheck.settingsIntent(),
        ).forEach { assertTrue(it.toString(), it.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0) }
    }

    // ---- who sees the card ----

    @Test fun `the card is for Bangladesh or for Bangla readers`() {
        assertTrue(OfficialPhoneCheck.showOfficialPhoneCheck("BD", "en"))
        assertTrue(OfficialPhoneCheck.showOfficialPhoneCheck("bd", "en"))
        assertTrue(OfficialPhoneCheck.showOfficialPhoneCheck(" bd ", "en"))
        assertTrue(OfficialPhoneCheck.showOfficialPhoneCheck(null, "bn"))
        assertTrue(OfficialPhoneCheck.showOfficialPhoneCheck("US", "bn"))
        assertFalse(OfficialPhoneCheck.showOfficialPhoneCheck("US", "en"))
        assertFalse(OfficialPhoneCheck.showOfficialPhoneCheck(null, "en"))
        assertFalse(OfficialPhoneCheck.showOfficialPhoneCheck("", "en"))
    }

    // ---- the words on screen name the same codes the intents use ----

    @Test fun `button labels carry the codes the intents use`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(context.getString(R.string.opc_dial_imei).contains(OfficialPhoneCheck.SHOW_IMEI_CODE))
        assertTrue(context.getString(R.string.opc_dial_ussd).contains(OfficialPhoneCheck.BTRC_USSD_CODE))
        assertTrue(context.getString(R.string.opc_send_sms).contains(OfficialPhoneCheck.BTRC_SMS_NUMBER))
        assertTrue(context.getString(R.string.opc_sms_note).contains(OfficialPhoneCheck.BTRC_SMS_KEYWORD))
        assertEquals("11 more digits to type", context.getString(R.string.opc_state_short, "11"))
    }

    @Test fun `no string says the app verifies the phone`() {
        val text = locate("src/main/res/values/strings_official.xml").readText().lowercase()
        listOf("devicegpt verif", "we verif", "app verif", "verified by", "is official.", "guarantee").forEach {
            assertFalse("strings_official.xml claims \"$it\"", text.contains(it))
        }
    }

    // ---- the promises: not read, not kept, not sent, no new permission ----

    private fun locate(rel: String): File {
        val root = System.getProperty("user.dir") ?: "."
        return listOf(File(root, rel), File(root, "app/$rel"), File("../app/$rel"))
            .firstOrNull { it.exists() } ?: File(root, rel)
    }

    private val featureSources by lazy {
        listOf(
            "src/main/java/com/teamz/lab/debugger/utils/OfficialPhoneCheck.kt",
            "src/main/java/com/teamz/lab/debugger/ui/OfficialPhoneCheckCard.kt",
        ).map { locate(it).also { f -> assertTrue("${f.path} not found", f.exists()) } }
    }

    @Test fun `the feature never stores or prints the number and reads nothing from the phone`() {
        val banned = listOf(
            "SharedPreferences", "getSharedPreferences", "DataStore", "rememberSaveable", "Log.", "println",
            "Timber", "Crashlytics", "TelephonyManager", "getImei", "getDeviceId", "ClipboardManager",
            "READ_PHONE_STATE", "READ_PRIVILEGED_PHONE_STATE", "SEND_SMS", "CALL_PHONE", "SmsManager",
            "ACTION_CALL", "requestPermission", "HttpURLConnection", "OkHttp",
        )
        for (file in featureSources) {
            val text = file.readText()
            banned.forEach { assertFalse("${file.name} references $it", text.contains(it)) }
        }
    }

    @Test fun `no analytics call in the card is given the number`() {
        val card = featureSources.last().readText()
        val calls = Regex("""logEvent\((?:[^()]|\([^()]*\))*\)""").findAll(card).map { it.value }.toList()
        assertTrue("expected the card's analytics calls, found ${calls.size}", calls.size >= 3)
        calls.forEach { call ->
            // The event's own name may say "Imei"; what is passed with it may not.
            val passed = call.replace(Regex("""AnalyticsEvent\.\w+"""), "")
            assertFalse("analytics call is given the number: $call", passed.contains("imei", ignoreCase = true))
            assertFalse("analytics call reads the card's state: $call", passed.contains("state."))
        }
    }

    @Test fun `the six events exist with the agreed names`() {
        assertEquals(
            listOf(
                "opc_shown", "opc_imei_valid", "opc_sms_opened", "opc_ussd_opened", "opc_web_opened",
                "opc_dial_imei_opened",
            ),
            AnalyticsEvent.values().map { it.eventName }.filter { it.startsWith("opc_") },
        )
    }

    @Test fun `the manifest gained no SMS, call or phone-state permission`() {
        val manifest = locate("src/main/AndroidManifest.xml").readText()
        assertFalse("SEND_SMS declared", manifest.contains("android.permission.SEND_SMS"))
        assertFalse("CALL_PHONE declared", manifest.contains("android.permission.CALL_PHONE"))
        assertFalse("READ_PRIVILEGED_PHONE_STATE declared", manifest.contains("READ_PRIVILEGED_PHONE_STATE"))
        // READ_PHONE_STATE was already declared once, for the SIM and network rows of the phone-info tab,
        // before this feature existed. This feature does not use it and must not add another.
        assertEquals(1, Regex("android\\.permission\\.READ_PHONE_STATE\"").findAll(manifest).count())
    }
}
