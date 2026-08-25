package com.teamz.lab.debugger.utils

import android.util.Log
import com.teamz.lab.debugger.BuildConfig

/**
 * Tab identifiers for configuration
 */
enum class TabType {
    LEADERBOARD,
    HEALTH,
    POWER,
    DEVICE_INFO,
    NETWORK_INFO,
    CAMERA,
    SCREEN_TEST,
    AI_BRIDGE,
    APP_DOCTOR,
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
                "camera" -> TabType.CAMERA
                "screen_test", "screentest" -> TabType.SCREEN_TEST
                "ai_bridge", "aibridge", "bridge" -> TabType.AI_BRIDGE
                "app_doctor", "appdoctor", "doctor" -> TabType.APP_DOCTOR
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
        
        // Owner call 2026-07-24: ship AI_BRIDGE to users without a Firebase RC push. If the RC
        // `tab_order` value predates this tab (as prod's still does), append it at the end.
        // Trade-off accepted on the record: this drops the RC kill-switch — if AI Bridge misbehaves
        // in the wild, only a new APK release can hide it. Not a mistake, a deliberate choice.
        if (TabType.AI_BRIDGE !in tabOrder) {
            tabOrder.add(TabType.AI_BRIDGE)
            Log.d(TAG, "Appended AI_BRIDGE (not in RC config)")
        }

        // Same precedent as AI_BRIDGE above, and the same accepted trade-off. Owner call
        // 2026-08-18: 'no need to hide and show, all will be exposed'. Prod's RC tab_order
        // predates this tab, so without this the tab would be invisible until someone
        // edits the console. Inserting at index 2 rather than appending keeps the
        // RC-configured path consistent with getDefaultTabOrder(); appending would put it
        // dead last, which is the opposite of the discovery this placement is for.
        // Cost, stated plainly: there is no RC kill-switch for this tab until the console
        // value is updated to name it. Removing it then needs a new APK.
        if (TabType.APP_DOCTOR !in tabOrder) {
            tabOrder.add(minOf(2, tabOrder.size), TabType.APP_DOCTOR)
            Log.d(TAG, "Inserted APP_DOCTOR (not in RC config)")
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
        // App Doctor sits at position 3 (2026-08-18). Placed high because it is the
        // newest surface and needs discovery; NOT placed by measured reach, because it
        // has none yet. Deliberately inserted WITHOUT reordering any existing tab:
        // reordering and adding at the same time would confound the result, and
        // CAMERA/SCREEN_TEST — the two tabs it displaces — draw 79%/70% of their use
        // from NEW users, the one cohort that is currently growing.
        order.add(TabType.APP_DOCTOR)
        // CAMERA, SCREEN_TEST, and AI_BRIDGE are deliberately NOT in requiredTabs below: they
        // must stay safe kill-switches via RemoteConfig tab_order, since they are newer surfaces
        // than the other four (CAMERA + SCREEN_TEST split 2026-07-24; AI_BRIDGE added 2026-07-24).
        order.add(TabType.CAMERA)
        order.add(TabType.SCREEN_TEST)
        order.add(TabType.AI_BRIDGE)
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
            TabType.CAMERA -> "my_camera_report.txt"
            TabType.SCREEN_TEST -> "my_screen_test_report.txt"
            TabType.APP_DOCTOR -> "my_website_check_report.txt"
            // AI_BRIDGE has no shareable text report (nothing to write to a file); the parent
            // hides the Send FAB on this tab. Filename left generic in case a future change
            // wires a share flow.
            TabType.AI_BRIDGE -> "my_ai_bridge_info.txt"
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
            TabType.CAMERA -> "camera"
            TabType.SCREEN_TEST -> "screen_test"
            TabType.APP_DOCTOR -> "app_doctor"
            TabType.AI_BRIDGE -> "ai_bridge"
        }
    }
}
