package com.teamz.lab.debugger.utils

/**
 * Last App Doctor report, so the GLOBAL "Ask AI" / share export can include it.
 *
 * Why a holder rather than a parameter: [AiPromptGenerator.generateMainPrompt] is a
 * tab-index dispatcher that receives no Context and no result object, and every other
 * tab that has a live result works around this the same way. Threading the result
 * through would mean changing that signature and all of its call sites for one tab.
 *
 * Without this the global export silently drops the finding — someone sends us "the
 * report" and the probe result, the reason they ran the tool at all, is missing.
 *
 * Process-scoped and deliberately not persisted: a stale reachability result from a
 * different network days ago is worse than no result, because it reads as current.
 */
object AppDoctorReportHolder {
    @Volatile
    var latest: String? = null
}
