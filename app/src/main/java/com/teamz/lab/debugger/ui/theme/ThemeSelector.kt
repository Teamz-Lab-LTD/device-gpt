package com.teamz.lab.debugger.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.string

@Composable
private fun ThemeOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.theme_selected_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Quick theme switcher for FAB or toolbar
@Composable
fun QuickThemeSwitcher(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeManager = useThemeManager()
    
    var showMenu by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        IconButton(
            onClick = { showMenu = !showMenu }
        ) {
            Icon(
                imageVector = when (themeManager.currentTheme) {
                    AppTheme.DESIGN_SYSTEM_LIGHT -> Icons.Default.LightMode
                    AppTheme.DESIGN_SYSTEM_DARK -> Icons.Default.DarkMode
                },
                contentDescription = androidx.compose.ui.res.stringResource(R.string.theme_button_cd),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(LocalContext.current.string(R.string.light)) },
                leadingIcon = { Icon(Icons.Default.LightMode, null) },
                onClick = {
                    context.switchToDesignSystemLight()
                    themeManager.setDarkMode(false, context)
                    showMenu = false
                }
            )
            
            DropdownMenuItem(
                text = { Text(LocalContext.current.string(R.string.dark)) },
                leadingIcon = { Icon(Icons.Default.DarkMode, null) },
                onClick = {
                    context.switchToDesignSystemDark()
                    themeManager.setDarkMode(true, context)
                    showMenu = false
                }
            )

        }
    }
} 