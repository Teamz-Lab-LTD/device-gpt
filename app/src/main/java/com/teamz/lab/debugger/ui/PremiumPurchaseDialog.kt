package com.teamz.lab.debugger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.teamz.lab.debugger.utils.RevenueCatManager
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import kotlinx.coroutines.launch

/**
 * Premium Purchase Dialog
 * 
 * A user-friendly dialog to encourage users to remove ads by subscribing to premium.
 * Focuses on free ad removal as the main benefit.
 * 
 * Usage:
 * ```
 * var showPremiumDialog by remember { mutableStateOf(false) }
 * 
 * if (showPremiumDialog) {
 *     PremiumPurchaseDialog(
 *         onDismiss = { showPremiumDialog = false },
 *         activity = activity
 *     )
 * }
 * ```
 */
@Composable
fun PremiumPurchaseDialog(
    onDismiss: () -> Unit,
    activity: android.app.Activity,
    title: String = "DeviceGPT Premium",
    subtitle: String? = null, // Will be generated dynamically with price
    benefits: List<String> = listOf(
        "✅ No ads - ever",
        "✅ Faster app performance",
        "✅ Support development",
        "✅ One-time payment - no subscriptions"
    )
) {
    var isPurchasing by remember { mutableStateOf(false) }
    var purchaseError by remember { mutableStateOf<String?>(null) }
    var purchaseSuccess by remember { mutableStateOf(false) }
    var productPrice by remember { mutableStateOf<String?>(null) }
    val premiumStatus by RevenueCatManager.premiumStatusFlow.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Fetch price dynamically from RevenueCat
    LaunchedEffect(Unit) {
        RevenueCatManager.getLifetimeProductPrice(
            onSuccess = { price ->
                productPrice = price
            },
            onError = { error ->
                // No hardcoded fallback — a wrong displayed price is a
                // misleading-price claim. Subtitle renders without price when null.
                android.util.Log.w("PremiumPurchaseDialog", "Failed to fetch price: $error, hiding price")
            }
        )
    }
    
    // Generate subtitle with dynamic price
    val displaySubtitle = subtitle ?: if (productPrice != null) {
        "Remove Ads Forever - $productPrice • Lifetime Access"
    } else {
        "Remove Ads Forever • Lifetime Access"
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f) // 90% of screen width for better visibility
                .padding(8.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Success state
                if (purchaseSuccess || premiumStatus is RevenueCatManager.PremiumStatus.Premium) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = DesignSystemColors.NeonGreen
                        )
                        Text(
                            text = "Welcome to DeviceGPT Premium!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "All ads have been removed. Enjoy your ad-free DeviceGPT experience!",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DesignSystemColors.NeonGreen,
                                contentColor = DesignSystemColors.Dark
                            )
                        ) {
                            Text("Got it!", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Purchase flow - DeviceGPT branded
                    // Star icon with DeviceGPT branding
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = DesignSystemColors.NeonGreen
                    )
                    
                    // DeviceGPT Premium title
                    Text(
                        text = "⭐ DeviceGPT Premium",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Price and value proposition - dynamically fetched
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = DesignSystemColors.NeonGreen.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = displaySubtitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = DesignSystemColors.NeonGreen,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    
                    // Benefits list with DeviceGPT context
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        benefits.forEach { benefit ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(
                                    text = benefit,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    
                    // Error message
                    purchaseError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp) // Increased from 12dp to 16dp
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp), // Increased button height
                            enabled = !isPurchasing
                        ) {
                            Text(
                                "Maybe Later",
                                style = MaterialTheme.typography.bodyLarge // Larger text
                            )
                        }
                        
                        Button(
                            onClick = {
                                isPurchasing = true
                                purchaseError = null
                                // Call purchaseProduct to trigger the purchase flow
                                RevenueCatManager.purchaseProduct(
                                    activity = activity,
                                    onSuccess = {
                                        isPurchasing = false
                                        purchaseSuccess = true
                                    },
                                    onError = { error ->
                                        isPurchasing = false
                                        purchaseError = error
                                    },
                                    onDismiss = {
                                        isPurchasing = false
                                        // User cancelled - no error needed
                                    }
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            enabled = !isPurchasing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DesignSystemColors.NeonGreen,
                                contentColor = DesignSystemColors.Dark
                            )
                        ) {
                            if (isPurchasing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 3.dp,
                                    color = DesignSystemColors.Dark
                                )
                            } else {
                                Text(
                                    text = if (productPrice != null) {
                                        "Get Premium - $productPrice"
                                    } else {
                                        "Get Premium"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    // Restore purchases link
                    TextButton(
                        onClick = {
                            // Track restore attempt
                            com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
                                com.teamz.lab.debugger.utils.AnalyticsEvent.DrawerItemClicked,
                                mapOf("item" to "restore_purchases")
                            )
                            scope.launch {
                                RevenueCatManager.restorePurchases(
                                    onSuccess = {
                                        purchaseError = null
                                        // Check if restore was successful
                                        if (RevenueCatManager.isPremium()) {
                                            purchaseSuccess = true
                                            // Track successful restore
                                            com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
                                                com.teamz.lab.debugger.utils.AnalyticsEvent.DrawerItemClicked,
                                                mapOf("item" to "restore_purchases_success")
                                            )
                                        } else {
                                            purchaseError = "No previous purchases found"
                                            // Track failed restore
                                            com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
                                                com.teamz.lab.debugger.utils.AnalyticsEvent.DrawerItemClicked,
                                                mapOf("item" to "restore_purchases_no_purchases")
                                            )
                                        }
                                    },
                                    onError = { error ->
                                        purchaseError = error
                                        // Track restore error
                                        com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
                                            com.teamz.lab.debugger.utils.AnalyticsEvent.DrawerItemClicked,
                                            mapOf("item" to "restore_purchases_error", "error" to error)
                                        )
                                    }
                                )
                            }
                        },
                        enabled = !isPurchasing
                    ) {
                        Text(
                            text = "Restore Purchases",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simple premium status indicator
 * Shows a badge or icon when user has premium
 */
@Composable
fun PremiumStatusBadge() {
    val premiumStatus by RevenueCatManager.premiumStatusFlow.collectAsState()
    
    if (premiumStatus is RevenueCatManager.PremiumStatus.Premium) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Premium",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Small "Remove Ads" button to show after native ads
 * Compact, unobtrusive button that opens premium purchase dialog
 */
@Composable
fun RemoveAdsButton(
    activity: android.app.Activity,
    onShowDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPremium = RevenueCatManager.isPremium()
    
    if (!isPremium) {
        TextButton(
            onClick = {
                com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
                    com.teamz.lab.debugger.utils.AnalyticsEvent.DrawerItemClicked,
                    mapOf("item" to "remove_ads_button")
                )
                onShowDialog()
            },
            modifier = modifier
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Remove Ads",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

