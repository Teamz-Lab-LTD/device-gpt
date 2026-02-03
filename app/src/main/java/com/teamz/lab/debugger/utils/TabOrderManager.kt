package com.teamz.lab.debugger.utils

import android.util.Log

/**
 * Tab identifiers for configuration
 */
enum class TabType {
    LEADERBOARD,
    HEALTH,
    POWER,
    DEVICE_INFO,
    NETWORK_INFO
}

/**
 * Manages tab order configuration from RemoteConfig
 * 
 * Default order (IAP-optimized):
 * 1. Leaderboard (most premium gates)
 * 2. Health (engaging, actionable insights)
 * 3. Power (power consumption data)
 * 4. Device Info (technical details)
 * 5. Network Info (least engaging)
 */
object TabOrderManager {
    private const val TAG = "TabOrderManager"
    
    /**
     * Get the configured tab order from RemoteConfig
     * Format: comma-separated string like "leaderboard,health,power,device_info,network_info"
     * Returns default order if config is invalid
     */
    fun getTabOrder(): List<TabType> {
        val configString = RemoteConfigUtils.getTabOrderConfig()
        
        if (configString.isBlank()) {
            Log.d(TAG, "Tab order config is blank, using default order")
            return getDefaultTabOrder()
        }
        
        val tabNames = configString.split(",").map { it.trim().lowercase() }
        val tabOrder = mutableListOf<TabType>()
        
        for (tabName in tabNames) {
            val tabType = when (tabName) {
                "leaderboard" -> TabType.LEADERBOARD
                "health" -> TabType.HEALTH
                "power" -> TabType.POWER
                "device_info", "deviceinfo" -> TabType.DEVICE_INFO
                "network_info", "networkinfo" -> TabType.NETWORK_INFO
                else -> {
                    Log.w(TAG, "Unknown tab name in config: $tabName, skipping")
                    null
                }
            }
            if (tabType != null) {
                tabOrder.add(tabType)
            }
        }
        
        // Validate: must have at least 4 tabs (leaderboard is optional)
        if (tabOrder.size < 4) {
            Log.w(TAG, "Invalid tab order config (too few tabs), using default order")
            return getDefaultTabOrder()
        }
        
        // Ensure all required tabs are present
        val requiredTabs = setOf(TabType.HEALTH, TabType.POWER, TabType.DEVICE_INFO, TabType.NETWORK_INFO)
        val presentTabs = tabOrder.toSet()
        if (!presentTabs.containsAll(requiredTabs)) {
            Log.w(TAG, "Missing required tabs in config, using default order")
            return getDefaultTabOrder()
        }
        
        Log.d(TAG, "Tab order from config: ${tabOrder.map { it.name }}")
        return tabOrder
    }
    
    /**
     * Get default tab order (IAP-optimized)
     */
    private fun getDefaultTabOrder(): List<TabType> {
        val isLeaderboardEnabled = RemoteConfigUtils.isLeaderboardEnabled()
        val order = mutableListOf<TabType>()
        
        if (isLeaderboardEnabled) {
            order.add(TabType.LEADERBOARD)
        }
        
        // Core tabs in IAP-optimized order
        order.add(TabType.HEALTH)
        order.add(TabType.POWER)
        order.add(TabType.DEVICE_INFO)
        order.add(TabType.NETWORK_INFO)
        
        return order
    }
    
    /**
     * Get the index of a specific tab type in the current order
     * Returns -1 if tab is not found
     */
    fun getTabIndex(tabType: TabType): Int {
        val order = getTabOrder()
        return order.indexOf(tabType)
    }
    
    /**
     * Get tab type at a specific index
     * Returns null if index is out of bounds
     */
    fun getTabTypeAt(index: Int): TabType? {
        val order = getTabOrder()
        return if (index in order.indices) order[index] else null
    }
    
    /**
     * Get total number of tabs
     */
    fun getTotalTabs(): Int {
        return getTabOrder().size
    }
    
    /**
     * Check if leaderboard is enabled and get its index
     */
    fun getLeaderboardIndex(): Int? {
        val isEnabled = RemoteConfigUtils.isLeaderboardEnabled()
        if (!isEnabled) return null
        return getTabIndex(TabType.LEADERBOARD)
    }
    
    /**
     * Get file name for sharing based on tab index
     */
    fun getShareFileName(tabIndex: Int): String {
        val tabType = getTabTypeAt(tabIndex) ?: return "my_device_info.txt"
        return when (tabType) {
            TabType.LEADERBOARD -> "my_leaderboard.txt"
            TabType.HEALTH -> "my_health_report.txt"
            TabType.POWER -> "my_power_report.txt"
            TabType.DEVICE_INFO -> "my_device_info.txt"
            TabType.NETWORK_INFO -> "my_network_info.txt"
        }
    }
    
    /**
     * Get tab name for analytics based on tab index
     */
    fun getTabNameForAnalytics(tabIndex: Int): String {
        val tabType = getTabTypeAt(tabIndex) ?: return "unknown"
        return when (tabType) {
            TabType.LEADERBOARD -> "leaderboard"
            TabType.HEALTH -> "health"
            TabType.POWER -> "power"
            TabType.DEVICE_INFO -> "device_info"
            TabType.NETWORK_INFO -> "network_info"
        }
    }
}
