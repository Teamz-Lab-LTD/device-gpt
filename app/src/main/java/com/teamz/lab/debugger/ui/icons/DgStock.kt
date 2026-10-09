package com.teamz.lab.debugger.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The stock Material icons the screens use for generic meanings, under one name each.
 *
 * [DgIcons] holds the app's own drawings; everything generic (a tick, a warning, a search glass) is a stock
 * icon, exactly as [EmojiIcons] maps it. Call sites take both from this package, so the choice of Rounded or
 * Outlined for a meaning is made once, here.
 */
object DgStock {
    val Check: ImageVector get() = Icons.Rounded.Check
    val CheckCircle: ImageVector get() = Icons.Rounded.CheckCircle
    val Warning: ImageVector get() = Icons.Rounded.Warning
    val Cancel: ImageVector get() = Icons.Rounded.Cancel
    val Search: ImageVector get() = Icons.Rounded.Search
    val Refresh: ImageVector get() = Icons.Rounded.Refresh
    val ArrowForward: ImageVector get() = Icons.AutoMirrored.Rounded.ArrowForward
    val Circle: ImageVector get() = Icons.Rounded.Circle
    val Heart: ImageVector get() = Icons.Rounded.Favorite
    val Settings: ImageVector get() = Icons.Rounded.Settings
    val Group: ImageVector get() = Icons.Rounded.Group
    val Location: ImageVector get() = Icons.Rounded.LocationOn
    val Notifications: ImageVector get() = Icons.Rounded.Notifications
    val Clipboard: ImageVector get() = Icons.Rounded.Assignment
    val Box: ImageVector get() = Icons.Rounded.Inventory2
    val Help: ImageVector get() = Icons.AutoMirrored.Rounded.HelpOutline
    val Moon: ImageVector get() = Icons.Rounded.DarkMode
    val Sun: ImageVector get() = Icons.Rounded.LightMode
    val Book: ImageVector get() = Icons.Rounded.MenuBook
    val StarOutline: ImageVector get() = Icons.Rounded.StarBorder
    val StarFilled: ImageVector get() = Icons.Rounded.Star
    val Android: ImageVector get() = Icons.Rounded.Android
    val SystemUpdate: ImageVector get() = Icons.Rounded.SystemUpdate
}
