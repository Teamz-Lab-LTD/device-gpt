package com.teamz.lab.debugger.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Caches the result of [getSecurityInfo], which forks a `getenforce` subprocess and reads
 * its stdout (device_utils.kt).
 *
 * Before this existed, three non-suspend functions in HealthScoreUtils reached that
 * subprocess through `runBlocking { getSecurityInfo(context) }`. Two of them are called
 * from Compose — `getImprovementSuggestions` from a `LazyColumn` item body and from a
 * `LaunchedEffect` — so the subprocess ran on the main thread, on every recomposition of
 * the Health tab. Same failure mode as the AI chooser ANR.
 *
 * The rule now: the blocking read happens exactly once, on IO, via [refresh]. Synchronous
 * callers take whatever is cached and simply omit security-derived advice until it lands.
 * Every consumer treats the security text additively (`when { info.contains(..) -> add(..) }`),
 * so an empty cache degrades to "no security suggestions", never to a wrong suggestion.
 *
 * Compose callers should observe [state] so the UI recomposes when the value arrives.
 */
object SecurityInfoCache {

    private val _state = MutableStateFlow<String?>(null)

    /** Null until the first [refresh] completes. */
    val state: StateFlow<String?> = _state.asStateFlow()

    /** Non-blocking. Empty string means "not known yet" — callers must degrade, not guess. */
    fun cachedOrEmpty(): String = _state.value ?: ""

    /** Performs the blocking read on IO and publishes it. Safe to call repeatedly. */
    suspend fun refresh(context: Context): String = withContext(Dispatchers.IO) {
        val info = getSecurityInfo(context)
        _state.value = info
        info
    }

    /** Test hook. */
    internal fun resetForTest() {
        _state.value = null
    }
}
