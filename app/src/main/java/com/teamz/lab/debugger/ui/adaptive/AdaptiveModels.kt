package com.teamz.lab.debugger.ui.adaptive

import com.teamz.lab.debugger.utils.TabOrderManager
import com.teamz.lab.debugger.utils.TabType

/**
 * Chrome mode for [DeviceGptNavExperience], derived from [androidx.compose.material3.adaptive.currentWindowAdaptiveInfo].
 */
enum class DeviceGptChromeMode {
    CompactPhone,
    MediumListDetail,
    ExpandedThreePane,
}

/**
 * High-level scan categories for multi-pane navigation (tablet / desktop window).
 */
enum class ScanCategory(val displayLabel: String) {
    BATTERY("Battery"),
    NETWORK("Network"),
    HARDWARE("Hardware"),
    PRIVACY("Privacy"),
    AI("AI"),
    ;

    fun tabIndex(): Int = when (this) {
        BATTERY -> TabOrderManager.getTabIndex(TabType.POWER)
        NETWORK -> TabOrderManager.getTabIndex(TabType.NETWORK_INFO)
        HARDWARE -> TabOrderManager.getTabIndex(TabType.DEVICE_INFO)
        PRIVACY -> TabOrderManager.getTabIndex(TabType.HEALTH)
        AI -> TabOrderManager.getLeaderboardIndex()
            ?: TabOrderManager.getTabIndex(TabType.HEALTH)
    }

    companion object {
        fun fromTabType(type: TabType?): ScanCategory = when (type) {
            TabType.POWER -> BATTERY
            TabType.NETWORK_INFO -> NETWORK
            TabType.DEVICE_INFO -> HARDWARE
            TabType.HEALTH -> PRIVACY
            TabType.LEADERBOARD, null -> AI
        }
    }
}
