package com.teamz.lab.debugger.ui.components

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The smallest area a finger should have to hit. */
val MinTouchTarget: Dp = 48.dp

/**
 * A small control with a full-size touch area: it takes [visual] of room in the layout, so nothing around
 * it moves, but a finger can hit a [MinTouchTarget] square centred on it. Use it on icon buttons, info dots
 * and close buttons that are drawn smaller than 48dp.
 */
fun Modifier.touchTarget(visual: Dp): Modifier = size(visual).requiredSize(MinTouchTarget)
