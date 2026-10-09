package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.AIClickHandler
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Review 2026-10-09 M6: the AI soft-gate paywall fired inside the 72 h quiet period and in
 * countries where Play billing cannot charge (Iran), contradicting the vc52 release note
 * "no upgrade pop-ups you did not ask for".
 */
class AiSoftGateQuietPeriodTest {
    @Test fun `soft gate fires after the free uses when an unsolicited paywall is allowed`() {
        assertTrue(AIClickHandler.shouldSoftGate(isPremium = false, useCount = 4, shownThisSession = false, paywallAllowed = true))
    }

    @Test fun `no soft-gate paywall in the quiet period or where billing cannot charge`() {
        assertFalse(AIClickHandler.shouldSoftGate(isPremium = false, useCount = 4, shownThisSession = false, paywallAllowed = false))
    }

    @Test fun `existing rules still hold`() {
        assertFalse(AIClickHandler.shouldSoftGate(isPremium = true, useCount = 9, shownThisSession = false, paywallAllowed = true))
        assertFalse(AIClickHandler.shouldSoftGate(isPremium = false, useCount = 3, shownThisSession = false, paywallAllowed = true))
        assertFalse(AIClickHandler.shouldSoftGate(isPremium = false, useCount = 9, shownThisSession = true, paywallAllowed = true))
    }
}
