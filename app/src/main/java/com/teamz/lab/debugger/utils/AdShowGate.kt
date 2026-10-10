package com.teamz.lab.debugger.utils

/**
 * The session gates for full-screen ads, as pure functions so the LOAD path and the SHOW path
 * give the same answer.
 *
 * Before 2026-10-10 only the show paths asked. Application.onStart loaded an interstitial on
 * every foreground and AppOpenAdManager loaded an app-open ad before checking the session, so
 * with ads_grace_sessions = app_open_ad_min_session = 10 (RC v17+) nearly every request a new
 * user made was filled and then could not be shown. AdMob, 30 d, DeviceGPT 3.x builds:
 * interstitial 124 matched / 32 shown, app_open 70 matched / 5 shown.
 *
 * The numbers stay the owner's Remote Config values; this only moves the check earlier.
 */
object AdShowGate {

    /** RC ads_grace_sessions: no interstitial in sessions 1..graceSessions. */
    fun interstitialInGrace(sessionCount: Int, graceSessions: Int): Boolean =
        graceSessions > 0 && sessionCount in 1..graceSessions

    /** RC app_open_ad_min_session: no app-open ad below that session. */
    fun appOpenBelowMinSession(sessionCount: Int, minSession: Int): Boolean =
        sessionCount < minSession
}
