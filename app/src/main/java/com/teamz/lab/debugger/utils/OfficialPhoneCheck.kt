package com.teamz.lab.debugger.utils

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.util.Locale

/**
 * The "Is this phone official?" helper for Bangladesh.
 *
 * Bangladesh's regulator BTRC keeps the NEIR register of handsets. A person asks it about a phone by SMS
 * (`KYD <IMEI>` to 16002), by dialling `*16161#`, or on `neir.btrc.gov.bd`. This object holds everything
 * about that which needs no screen: tidying the typed number, the offline check-digit test, and the intents
 * that open the dialer, the SMS app, the browser and the phone's own "About phone" page.
 *
 * Three things it never does, by design:
 * - it never reads the IMEI from the phone (an ordinary app cannot, on Android 10 and later): the person
 *   types it;
 * - it never keeps, prints or sends the number: every function here takes it in and hands it straight back;
 * - it never says a phone is official. A number that passes [isValidImei] is only well-formed. BTRC's reply
 *   is the one answer.
 *
 * The intents need no permission: they open another app with the text filled in, and the person presses
 * call or send there.
 */
object OfficialPhoneCheck {

    /** An IMEI is always this many digits. */
    const val IMEI_LENGTH = 15

    /** The code every phone answers with its own IMEI. */
    const val SHOW_IMEI_CODE = "*#06#"

    /** BTRC's short number for the SMS check. */
    const val BTRC_SMS_NUMBER = "16002"

    /** The word that starts the SMS: "Know Your Device". */
    const val BTRC_SMS_KEYWORD = "KYD"

    /** BTRC's dial code for the same check. */
    const val BTRC_USSD_CODE = "*16161#"

    /** BTRC's NEIR website. */
    const val BTRC_WEB_URL = "https://neir.btrc.gov.bd"

    /** ISO country code of Bangladesh. */
    const val COUNTRY_BANGLADESH = "BD"

    private const val LANGUAGE_BANGLA = "bn"
    private const val GROUP_SIZE = 5

    /** Where a typed number stands. */
    enum class ImeiState {
        /** Nothing typed yet. */
        EMPTY,

        /** Fewer than [IMEI_LENGTH] digits. */
        TOO_SHORT,

        /** [IMEI_LENGTH] digits, but the last digit does not match the rest: most likely a typing mistake. */
        INVALID,

        /** [IMEI_LENGTH] digits with a correct check digit. Well-formed; says nothing about being official. */
        VALID,
    }

    /**
     * Keeps only the digits of what was typed or pasted, at most [IMEI_LENGTH] of them.
     *
     * Spaces, dashes and slashes go (a box prints `35-693803-564380-9`), and digits typed on a Bangla
     * keyboard become 0 to 9, because the SMS to BTRC has to carry plain digits.
     *
     * @param raw the text as typed.
     * @return 0 to [IMEI_LENGTH] characters, each `0`..`9`.
     */
    fun normalizeImei(raw: String): String {
        val out = StringBuilder(IMEI_LENGTH)
        for (ch in raw) {
            if (out.length == IMEI_LENGTH) break
            val digit = Character.digit(ch, 10)
            if (digit >= 0) out.append(('0' + digit))
        }
        return out.toString()
    }

    /**
     * True when [imei] is [IMEI_LENGTH] digits and its last digit is the Luhn check digit of the first
     * fourteen. Fifteen zeros pass the sum but are no handset's number, so they are refused.
     *
     * This is an offline test of the number's shape. It cannot tell an official phone from an unofficial one.
     *
     * @param imei digits only, as returned by [normalizeImei].
     */
    fun isValidImei(imei: String): Boolean {
        if (imei.length != IMEI_LENGTH || imei.any { it !in '0'..'9' }) return false
        if (imei.all { it == '0' }) return false
        var sum = 0
        // From the right: the check digit counts once, the next is doubled, and so on.
        imei.reversed().forEachIndexed { index, ch ->
            val digit = ch - '0'
            sum += if (index % 2 == 1) (digit * 2).let { if (it > 9) it - 9 else it } else digit
        }
        return sum % 10 == 0
    }

    /**
     * Where a typed number stands.
     *
     * @param imei digits only, as returned by [normalizeImei].
     */
    fun imeiState(imei: String): ImeiState = when {
        imei.isEmpty() -> ImeiState.EMPTY
        imei.length < IMEI_LENGTH -> ImeiState.TOO_SHORT
        isValidImei(imei) -> ImeiState.VALID
        else -> ImeiState.INVALID
    }

    /**
     * How many digits are still to be typed, never below zero.
     *
     * @param imei digits only, as returned by [normalizeImei].
     */
    fun digitsRemaining(imei: String): Int = (IMEI_LENGTH - imei.length).coerceAtLeast(0)

    /**
     * The number in groups of five with a space between, for reading against a box or a sticker:
     * `49015 42032 37518`. Display only; never send this form.
     *
     * @param imei digits only, as returned by [normalizeImei].
     */
    fun groupImei(imei: String): String = imei.chunked(GROUP_SIZE).joinToString(" ")

    /**
     * The text of the SMS BTRC expects: the letters KYD, one space, the 15 digits.
     *
     * @param imei digits only, as returned by [normalizeImei].
     */
    fun kydSmsBody(imei: String): String = "$BTRC_SMS_KEYWORD $imei"

    /**
     * Opens the dialer with [code] typed in. The person presses call. `#` is sent as `%23`, because a bare
     * `#` in a `tel:` address cuts off everything after it.
     *
     * @param code a dial code such as [SHOW_IMEI_CODE] or [BTRC_USSD_CODE].
     */
    fun dialIntent(code: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the dialer with `*#06#` typed in; the phone then shows its own IMEI. */
    fun showImeiIntent(): Intent = dialIntent(SHOW_IMEI_CODE)

    /** Opens the dialer with BTRC's `*16161#` typed in. The person enters the IMEI in the menu that follows. */
    fun btrcUssdIntent(): Intent = dialIntent(BTRC_USSD_CODE)

    /**
     * Opens the SMS app with a message to [BTRC_SMS_NUMBER] already written. Nothing is sent: the person
     * presses send, and the mobile operator may charge for the message.
     *
     * @param imei digits only, as returned by [normalizeImei].
     */
    fun btrcSmsIntent(imei: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$BTRC_SMS_NUMBER"))
            .putExtra("sms_body", kydSmsBody(imei))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens BTRC's NEIR website in the browser. No number is passed in the address. */
    fun btrcWebIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(BTRC_WEB_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the phone's own "About phone" page, where the IMEI is listed. */
    fun aboutPhoneIntent(): Intent =
        Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the phone's main settings; used when a phone has no direct "About phone" page. */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Whether the card is shown at all. BTRC's register covers handsets used in Bangladesh, so the card is
     * for people there: the phone's country is Bangladesh, or the person reads the app in Bangla.
     *
     * @param countryCode ISO country of the phone (`RemoteConfigUtils.countryCode()`); may be null or blank.
     * @param appLanguage the language the app is showing (`LocaleManager.currentLanguageCode`).
     */
    fun showOfficialPhoneCheck(countryCode: String?, appLanguage: String): Boolean =
        countryCode?.trim()?.uppercase(Locale.ROOT) == COUNTRY_BANGLADESH ||
            appLanguage.trim().lowercase(Locale.ROOT) == LANGUAGE_BANGLA
}
