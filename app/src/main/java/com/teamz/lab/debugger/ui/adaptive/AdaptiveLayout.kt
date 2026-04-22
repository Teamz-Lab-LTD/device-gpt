package com.teamz.lab.debugger.ui.adaptive

import androidx.activity.ComponentActivity
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowWidthSizeClass

/**
 * Top-level adaptive entry: uses [currentWindowAdaptiveInfo] (window metrics + size class) and
 * delegates to [DeviceGptNavExperience] with the appropriate [DeviceGptChromeMode].
 *
 * See [AdaptiveModels] for category mapping and [DeviceGptNavExperience] for navigation state.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AdaptiveDeviceGptLayout(activity: ComponentActivity) {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val widthClass = adaptiveInfo.windowSizeClass.windowWidthSizeClass
    val chromeMode = when (widthClass) {
        WindowWidthSizeClass.COMPACT -> DeviceGptChromeMode.CompactPhone
        WindowWidthSizeClass.MEDIUM -> DeviceGptChromeMode.MediumListDetail
        WindowWidthSizeClass.EXPANDED -> DeviceGptChromeMode.ExpandedThreePane
        else -> DeviceGptChromeMode.CompactPhone
    }
    DeviceGptNavExperience(activity, chromeMode = chromeMode)
}
