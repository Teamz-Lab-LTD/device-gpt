package com.teamz.lab.debugger.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.revenuecat.purchases.ui.revenuecatui.ExperimentalPreviewRevenueCatUIPurchasesAPI
import com.revenuecat.purchases.ui.revenuecatui.Paywall
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import com.revenuecat.purchases.ui.revenuecatui.PaywallOptions
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.RevenueCatManager
import com.teamz.lab.debugger.utils.InterstitialAdManager
import com.teamz.lab.debugger.utils.AppOpenAdManager
import androidx.compose.runtime.LaunchedEffect

/**
 * Reusable RevenueCat Paywall composable
 * Displays the "device-gpt" paywall designed in RevenueCat console as a full screen
 * 
 * @param showPaywall Whether to show the paywall
 * @param onDismiss Callback when paywall is dismissed
 * @param analyticsSource Source identifier for analytics tracking (e.g., "revenuecat_paywall", "revenuecat_paywall_drawer")
 */
@OptIn(ExperimentalPreviewRevenueCatUIPurchasesAPI::class)
@Composable
fun RevenueCatPaywall(
    showPaywall: Boolean,
    onDismiss: () -> Unit,
    analyticsSource: String = "revenuecat_paywall"
) {
    val context = LocalContext.current
    val premiumStatus by RevenueCatManager.premiumStatusFlow.collectAsState()
    val currentPremiumStatus = premiumStatus
    val isPremium = currentPremiumStatus is RevenueCatManager.PremiumStatus.Premium && 
        (currentPremiumStatus as? RevenueCatManager.PremiumStatus.Premium)?.isActive == true
    
    // Clear all ads when premium is activated (reactive)
    LaunchedEffect(isPremium) {
        if (isPremium) {
            android.util.Log.d("RevenueCatPaywall", "Premium activated - clearing all ads")
            InterstitialAdManager.clearAd()
            AppOpenAdManager.clearAd()
        }
    }
    
    // Fetch the specific offering to show the custom "device-gpt" paywall
    var offering by remember { mutableStateOf<com.revenuecat.purchases.Offering?>(null) }
    
    // Fetch offering when paywall should be shown
    LaunchedEffect(showPaywall) {
        if (showPaywall && !isPremium && offering == null) {
            com.revenuecat.purchases.Purchases.sharedInstance.getOfferings(
                object : com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback {
                    override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                        val targetOffering = offerings.getOffering(RevenueCatManager.OFFERING_ID)
                        if (targetOffering != null) {
                            offering = targetOffering
                        } else {
                            Log.e("RevenueCatPaywall", "Offering '${RevenueCatManager.OFFERING_ID}' not found. Available: ${offerings.all.keys}")
                        }
                    }
                    
                    override fun onError(purchasesError: com.revenuecat.purchases.PurchasesError) {
                        Log.e("RevenueCatPaywall", "Failed to fetch offerings: ${purchasesError.message}")
                    }
                }
            )
        }
    }
    
    // Reset offering when paywall is dismissed
    LaunchedEffect(showPaywall) {
        if (!showPaywall) {
            offering = null
        }
    }
    
    // Show Paywall as full screen composable with the specific offering
    if (showPaywall && !isPremium && offering != null) {
        Paywall(
            options = PaywallOptions.Builder(
                dismissRequest = onDismiss
            )
                .setOffering(offering!!) // Use the fetched "device-gpt-offering"
                .setListener(
                    object : PaywallListener {
                        override fun onPurchaseCompleted(
                            customerInfo: com.revenuecat.purchases.CustomerInfo,
                            storeTransaction: com.revenuecat.purchases.models.StoreTransaction
                        ) {
                            // Update premium status first
                            RevenueCatManager.updatePremiumStatus(customerInfo)
                            
                            // Clear all loaded ads immediately
                            android.util.Log.d("RevenueCatPaywall", "Purchase completed - clearing all ads")
                            InterstitialAdManager.clearAd()
                            AppOpenAdManager.clearAd()
                            
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.DrawerItemClicked,
                                mapOf("item" to "purchase_success", "source" to analyticsSource)
                            )
                            onDismiss()
                            Toast.makeText(context, "Premium activated! Ads removed.", Toast.LENGTH_SHORT).show()
                        }
                        
                        override fun onRestoreCompleted(customerInfo: com.revenuecat.purchases.CustomerInfo) {
                            // Update premium status first
                            RevenueCatManager.updatePremiumStatus(customerInfo)
                            
                            if (RevenueCatManager.isPremium()) {
                                // Clear all loaded ads immediately
                                android.util.Log.d("RevenueCatPaywall", "Restore completed - clearing all ads")
                                InterstitialAdManager.clearAd()
                                AppOpenAdManager.clearAd()
                                
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.DrawerItemClicked,
                                    mapOf("item" to "restore_purchases_success", "source" to analyticsSource)
                                )
                                onDismiss()
                                Toast.makeText(context, "Premium activated! Ads removed.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                .build()
        )
    }
}
