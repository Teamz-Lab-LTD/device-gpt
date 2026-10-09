package com.teamz.lab.debugger.ui

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.ui.icons.DgIcons
import com.teamz.lab.debugger.ui.icons.DgStock
import com.teamz.lab.debugger.ui.icons.DgText
import com.teamz.lab.debugger.ui.theme.dgSemanticColors
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.LocaleManager
import com.teamz.lab.debugger.utils.OfficialPhoneCheck
import com.teamz.lab.debugger.utils.OfficialPhoneCheck.ImeiState
import com.teamz.lab.debugger.utils.RemoteConfigUtils

/**
 * What the card remembers while its screen is alive: the digits typed so far.
 *
 * Create it with `remember` above the list the card sits in, so the number survives the card scrolling off
 * screen. It is held in memory only and is gone when the screen is: nothing is written to disk or to the
 * saved instance state, on purpose.
 */
@Stable
class OfficialPhoneCheckState {
    /** The digits typed so far, already tidied by [OfficialPhoneCheck.normalizeImei]. */
    var imei by mutableStateOf("")
        internal set

    /** So "shown" is counted once per screen, not once per scroll. */
    internal var shownLogged = false
}

/**
 * True when the card should be on screen for this person: the phone is in Bangladesh or the app is in Bangla.
 *
 * @param context any context; used to read the app language.
 */
fun shouldShowOfficialPhoneCheck(context: Context): Boolean =
    OfficialPhoneCheck.showOfficialPhoneCheck(
        countryCode = RemoteConfigUtils.countryCode(),
        appLanguage = LocaleManager.currentLanguageCode(context),
    )

/**
 * "Is this phone official?": walks a person in Bangladesh through asking BTRC about a handset before buying
 * or selling it.
 *
 * Three steps: see the IMEI (dial `*#06#`), type it here, ask BTRC (SMS `KYD <IMEI>` to 16002, or dial
 * `*16161#`, or the NEIR website). The card only opens the dialer, the SMS app or the browser with the text
 * filled in; the person presses call or send. It asks for no permission, does not read the IMEI from the
 * phone, and does not keep or send what is typed. The answer is BTRC's, never this app's.
 *
 * @param state the typed digits; hoist it above a lazy list so scrolling does not clear the field.
 * @param surface where the card sits, for analytics only: `health` or `device_info`.
 * @param modifier applied to the card.
 */
@Composable
fun OfficialPhoneCheckCard(
    state: OfficialPhoneCheckState,
    surface: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val semantic = dgSemanticColors()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val imeiState = OfficialPhoneCheck.imeiState(state.imei)
    val valid = imeiState == ImeiState.VALID
    var notice by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        if (!state.shownLogged) {
            state.shownLogged = true
            AnalyticsUtils.logEvent(AnalyticsEvent.OfficialCheckShown, mapOf("surface" to surface))
        }
    }

    /** Opens [intent]; on a device with no app for it, shows [missing] instead of crashing. */
    fun open(intent: Intent, @StringRes missing: Int, event: AnalyticsEvent?) {
        if (launchSafely(context, intent)) {
            notice = null
            event?.let { AnalyticsUtils.logEvent(it, mapOf("surface" to surface)) }
        } else {
            notice = missing
        }
    }

    AppCard(modifier = modifier, bottomPadding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                DgIcons.PhoneCheck,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                DgText(
                    stringResource(R.string.opc_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                DgText(
                    stringResource(R.string.opc_subtitle),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── 1. See the IMEI ──────────────────────────────────────────────────
        StepHeader(1, stringResource(R.string.opc_step1_title))
        BodyText(stringResource(R.string.opc_step1_body))
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                open(
                    OfficialPhoneCheck.showImeiIntent(),
                    R.string.opc_no_dialer,
                    AnalyticsEvent.OfficialCheckDialImeiOpened,
                )
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            contentPadding = ButtonPadding,
        ) { ButtonLabel(Icons.Rounded.Dialpad, stringResource(R.string.opc_dial_imei)) }
        TextButton(
            onClick = {
                if (!launchSafely(context, OfficialPhoneCheck.aboutPhoneIntent())) {
                    open(OfficialPhoneCheck.settingsIntent(), R.string.opc_no_settings, null)
                } else {
                    notice = null
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            contentPadding = ButtonPadding,
        ) { ButtonLabel(DgStock.Settings, stringResource(R.string.opc_about_phone)) }
        NoteRow(DgIcons.Tip, stringResource(R.string.opc_step1_tip), muted)

        // ── 2. Type it ───────────────────────────────────────────────────────
        StepHeader(2, stringResource(R.string.opc_step2_title))
        OutlinedTextField(
            value = state.imei,
            onValueChange = { typed ->
                val next = OfficialPhoneCheck.normalizeImei(typed)
                val becameValid = !valid && OfficialPhoneCheck.isValidImei(next)
                state.imei = next
                if (becameValid) {
                    // Only the fact that a well-formed number was typed. Never the number.
                    AnalyticsUtils.logEvent(AnalyticsEvent.OfficialCheckImeiValid, mapOf("surface" to surface))
                    focusManager.clearFocus()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { DgText(stringResource(R.string.opc_imei_label)) },
            singleLine = true,
            isError = imeiState == ImeiState.INVALID,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            visualTransformation = ImeiGroups,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
        Spacer(Modifier.height(4.dp))
        when (imeiState) {
            ImeiState.EMPTY -> NoteRow(null, stringResource(R.string.opc_state_empty), muted)
            ImeiState.TOO_SHORT -> NoteRow(
                null,
                stringResource(R.string.opc_state_short, OfficialPhoneCheck.digitsRemaining(state.imei).toString()),
                muted,
            )
            ImeiState.INVALID -> NoteRow(
                Icons.Rounded.ErrorOutline,
                stringResource(R.string.opc_state_invalid),
                MaterialTheme.colorScheme.error,
            )
            ImeiState.VALID -> NoteRow(DgStock.CheckCircle, stringResource(R.string.opc_state_valid), semantic.good)
        }
        NoteRow(DgIcons.Sim, stringResource(R.string.opc_two_sims), muted)

        // ── 3. Ask BTRC ──────────────────────────────────────────────────────
        StepHeader(3, stringResource(R.string.opc_step3_title))
        if (!valid) {
            BodyText(stringResource(R.string.opc_step3_locked))
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = {
                open(
                    OfficialPhoneCheck.btrcSmsIntent(state.imei),
                    R.string.opc_no_sms,
                    AnalyticsEvent.OfficialCheckSmsOpened,
                )
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            contentPadding = ButtonPadding,
        ) { ButtonLabel(Icons.Rounded.Sms, stringResource(R.string.opc_send_sms)) }
        Spacer(Modifier.height(6.dp))
        BodyText(stringResource(R.string.opc_sms_note), small = true)
        Spacer(Modifier.height(12.dp))
        BodyText(stringResource(R.string.opc_other_ways), small = true)
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                open(
                    OfficialPhoneCheck.btrcUssdIntent(),
                    R.string.opc_no_dialer,
                    AnalyticsEvent.OfficialCheckUssdOpened,
                )
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            contentPadding = ButtonPadding,
        ) { ButtonLabel(Icons.Rounded.Dialpad, stringResource(R.string.opc_dial_ussd)) }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                open(
                    OfficialPhoneCheck.btrcWebIntent(),
                    R.string.opc_no_browser,
                    AnalyticsEvent.OfficialCheckWebOpened,
                )
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            contentPadding = ButtonPadding,
        ) { ButtonLabel(DgIcons.Globe, stringResource(R.string.opc_open_web)) }

        notice?.let { message ->
            NoteRow(Icons.Rounded.ErrorOutline, stringResource(message), MaterialTheme.colorScheme.error)
        }

        // ── What the answer means, and where the number goes ─────────────────
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        DgText(
            stringResource(R.string.opc_meaning_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        BodyText(stringResource(R.string.opc_meaning_body))
        NoteRow(DgIcons.Lock, stringResource(R.string.opc_privacy), muted)
    }
}

private val ButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

/** Starts [intent]; false when the device has no app that can take it. */
private fun launchSafely(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (_: Exception) {
    // No dialer, no SMS app or no browser (a tablet, a locked-down work phone): the card says so.
    false
}

/** A numbered step title with room above it. */
@Composable
private fun StepHeader(number: Int, title: String) {
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            DgText(
                number.toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.width(10.dp))
        DgText(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun BodyText(text: String, small: Boolean = false) {
    DgText(
        text,
        fontSize = if (small) 12.sp else 13.sp,
        lineHeight = if (small) 18.sp else 20.sp,
        color = if (small) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

/** A line of advice or feedback with an optional small icon, both in [color]. */
@Composable
private fun NoteRow(icon: ImageVector?, text: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(top = 1.dp).size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        DgText(text, fontSize = 12.sp, lineHeight = 18.sp, color = color, modifier = Modifier.weight(1f))
    }
}

/** Icon and label for a full-width button; the label wraps instead of clipping on a narrow phone. */
@Composable
private fun ButtonLabel(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    DgText(text, fontSize = 14.sp, textAlign = TextAlign.Center)
}

/** Shows the digits in groups of five. The value itself stays plain digits. */
private object ImeiGroups : VisualTransformation {
    private const val GROUP = 5

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (offset <= 0) 0 else offset + (offset - 1) / GROUP

            override fun transformedToOriginal(offset: Int): Int =
                (offset - offset / (GROUP + 1)).coerceIn(0, raw.length)
        }
        return TransformedText(AnnotatedString(OfficialPhoneCheck.groupImei(raw)), mapping)
    }
}
