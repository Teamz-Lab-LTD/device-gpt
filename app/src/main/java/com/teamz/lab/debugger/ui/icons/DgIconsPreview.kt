package com.teamz.lab.debugger.ui.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.teamz.lab.debugger.ui.theme.AppTheme
import com.teamz.lab.debugger.ui.theme.DebuggerTheme
import com.teamz.lab.debugger.ui.theme.dgSemanticColors

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "DgIcons at 24dp and 20dp", showBackground = true, backgroundColor = 0xFF12151A, widthDp = 360)
@Composable
private fun DgIconsGalleryPreview() {
    DebuggerTheme(AppTheme.DESIGN_SYSTEM_DARK) {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DgIcons.all.values.forEach { icon ->
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                }
            }
            FlowRow(
                modifier = Modifier.padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DgIcons.all.values.forEach { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = dgSemanticColors().accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Preview(name = "DgLabelRow", showBackground = true, backgroundColor = 0xFF1D1F25)
@Composable
private fun DgLabelRowPreview() {
    DebuggerTheme(AppTheme.DESIGN_SYSTEM_DARK) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp).width(280.dp)) {
                DgLabelLines(
                    text = "✅ Camera looks fine\n⚠️ Battery is warm\n❌ Microphone did not respond\n" +
                        "🔋 Battery health 92%\nNo icon on this row\n💡 Close unused apps to save power",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
