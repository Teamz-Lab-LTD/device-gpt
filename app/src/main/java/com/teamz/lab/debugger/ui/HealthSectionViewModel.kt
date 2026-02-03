package com.teamz.lab.debugger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for Health Section
 * Persists scroll state across activity lifecycle changes (like when ads show/close)
 */
class HealthSectionViewModel(application: Application) : AndroidViewModel(application) {
    
    // Scroll position - persist across activity recreation
    private val _lazyListFirstVisibleItemIndex = MutableStateFlow(0)
    val lazyListFirstVisibleItemIndex: StateFlow<Int> = _lazyListFirstVisibleItemIndex.asStateFlow()
    
    private val _lazyListFirstVisibleItemScrollOffset = MutableStateFlow(0)
    val lazyListFirstVisibleItemScrollOffset: StateFlow<Int> = _lazyListFirstVisibleItemScrollOffset.asStateFlow()
    
    /**
     * Save LazyColumn scroll position
     */
    fun saveLazyListScrollPosition(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        _lazyListFirstVisibleItemIndex.value = firstVisibleItemIndex
        _lazyListFirstVisibleItemScrollOffset.value = firstVisibleItemScrollOffset
    }
}
