package com.teamz.lab.debugger.restore

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreCredentialManagerTest {

    @Test
    fun encryptDecrypt_roundTrip_preservesRestoredState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = RestoredState(
            userId = "firebase-test-uid",
            prefs = mapOf(
                "locale_preferences|selected_language" to "es",
                "theme_preferences|is_dark_mode" to "false",
            ),
            restoredAt = 1_700_000_000_000L,
        )
        val wrapped = RestoreCredentialManager.encryptRestoredStateForTest(context, original)
        val decoded = RestoreCredentialManager.decryptRestoredStateForTest(context, wrapped)
        assertEquals(original.userId, decoded.userId)
        assertEquals(original.restoredAt, decoded.restoredAt)
        assertEquals(original.prefs, decoded.prefs)
    }
}
