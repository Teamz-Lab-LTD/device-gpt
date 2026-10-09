package com.teamz.lab.debugger.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Completes when the first Remote Config fetchAndActivate of this process finishes, whether it
 * succeeded or not. The first-scan score waits on it (bounded) so the first-screen A/B can read a
 * server value instead of the bundled default.
 */
object RcFetchGate {
    @Volatile private var done = CompletableDeferred<Unit>()

    fun markDone() { done.complete(Unit) }

    /** True if the fetch finished within [timeoutMs]. */
    suspend fun await(timeoutMs: Long): Boolean = withTimeoutOrNull(timeoutMs) { done.await() } != null

    internal fun resetForTest() { done = CompletableDeferred() }
}
