package com.teamz.lab.debugger.utils

/**
 * The review-prompt gate, as a pure function.
 *
 * This lived inside ReviewPromptManager.shouldShowReviewPrompt(), which needs a Context and a
 * SharedPreferences, so the only way to check it was to read its source text. That is how a
 * 24-hour `delay()` on an unscoped coroutine survived in the first-launch path: nothing could
 * assert that a prompt was ever actually reachable.
 *
 * Every input here is a plain value, so the JVM tests state the rules directly.
 */

/** Why the prompt is or is not being shown. Named so a log line explains itself. */
enum class ReviewGate {
    /** Ask now. */
    SHOW,

    /** The very first session, or the app is younger than the configured minimum age. */
    TOO_SOON_AFTER_INSTALL,

    /** Asked recently; Play also enforces its own quota on top of this. */
    ASKED_RECENTLY,

    /** Nothing has happened yet that would make an ask reasonable. */
    NOT_ENOUGH_USE,

    /** Already rated, here or on another device. */
    ALREADY_REVIEWED,
}

/**
 * @param alreadyReviewed local flag, kept in sync from Firestore across devices
 * @param msSinceInstall now minus the stored first-launch timestamp; use a negative value when
 *   no install timestamp has been recorded yet, which means this IS the first session
 * @param minInstallAgeMs `review_delay_first_launch_ms`. Read as a minimum AGE, not as a
 *   duration to sleep for — the difference is the whole point of this function existing.
 * @param msSinceLastPrompt now minus the last prompt timestamp, or negative if never prompted
 */
fun reviewGate(
    alreadyReviewed: Boolean,
    msSinceInstall: Long,
    minInstallAgeMs: Long,
    msSinceLastPrompt: Long,
    minMsBetweenPrompts: Long,
    appOpenCount: Int,
    meaningfulInteractions: Int,
    minAppOpens: Int,
    minInteractions: Int,
): ReviewGate {
    if (alreadyReviewed) return ReviewGate.ALREADY_REVIEWED
    if (msSinceInstall < 0L) return ReviewGate.TOO_SOON_AFTER_INSTALL
    if (msSinceInstall < minInstallAgeMs) return ReviewGate.TOO_SOON_AFTER_INSTALL
    if (msSinceLastPrompt in 0 until minMsBetweenPrompts) return ReviewGate.ASKED_RECENTLY
    val enoughUse = appOpenCount >= minAppOpens || meaningfulInteractions >= minInteractions
    if (!enoughUse) return ReviewGate.NOT_ENOUGH_USE
    return ReviewGate.SHOW
}
