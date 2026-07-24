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
import com.teamz.lab.debugger.ui.NativeAdManager
import androidx.compose.runtime.LaunchedEffect
import com.teamz.lab.debugger.R
import kotlinx.coroutines.delay

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
            NativeAdManager.clear() // Clear native ads as well
        }
    }
    
    // Fetch the specific offering to show the custom "device-gpt" paywall
    var offering by remember { mutableStateOf<com.revenuecat.purchases.Offering?>(null) }
    
    // Track paywall shown
    LaunchedEffect(showPaywall) {
        if (showPaywall && !isPremium) {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.PremiumPaywallShown,
                mapOf(
                    "source" to analyticsSource,
                    "offering_id" to RevenueCatManager.OFFERING_ID
                )
            )
        }
    }
    
    // Fetch offering when paywall should be shown
    LaunchedEffect(showPaywall) {
        if (showPaywall && !isPremium && offering == null) {
            if (!RevenueCatManager.isSdkConfigured()) {
                Log.w("RevenueCatPaywall", "RevenueCat not configured — closing paywall request")
                onDismiss()
                return@LaunchedEffect
            }
            com.revenuecat.purchases.Purchases.sharedInstance.getOfferings(
                object : com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback {
                    override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                        // Shared RevenueCat project across all Teamz Lab apps means
                        // `offerings.current` is not ours. Pin, then fall back.
                        val targetOffering = offerings.getOffering(RevenueCatManager.OFFERING_ID)
                            ?: offerings.current
                        if (targetOffering == null) {
                            Log.e("RevenueCatPaywall", "No offering available. IDs: ${offerings.all.keys}")
                            reportOfferingUnavailable(context, analyticsSource, "no_offering_available", onDismiss)
                            return
                        }
                        // 2026-07-25: RC may return an offering whose packages have no local
                        // Play Billing productDetails (product-type mismatch between RC and
                        // Play Console — e.g. `lifetime_premium` declared as `subs` in RC
                        // when Play has it as INAPP). In that case `Paywall(...)` renders an
                        // empty box: the tap looks broken, no error surfaces. Detect the
                        // empty case here and treat it exactly like "no offering" so the
                        // user sees a toast instead of a mystery.
                        if (targetOffering.availablePackages.isEmpty()) {
                            Log.e(
                                "RevenueCatPaywall",
                                "Offering '${targetOffering.identifier}' has zero availablePackages — " +
                                    "Play Billing productDetails lookup failed for every package. " +
                                    "Fix product type mismatch in RC dashboard.",
                            )
                            reportOfferingUnavailable(context, analyticsSource, "offering_has_no_packages", onDismiss)
                            return
                        }
                        offering = targetOffering
                    }

                    override fun onError(purchasesError: com.revenuecat.purchases.PurchasesError) {
                        Log.e("RevenueCatPaywall", "Failed to fetch offerings: ${purchasesError.message}")
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.PremiumPaywallDismissed,
                            mapOf(
                                "source" to analyticsSource,
                                "reason" to "offering_fetch_failed",
                                "error" to purchasesError.message
                            )
                        )
                        Toast.makeText(
                            context,
                            context.getString(R.string.premium_unavailable_try_later),
                            Toast.LENGTH_SHORT
                        ).show()
                        onDismiss()
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

    // Watchdog: if the RC callback never fires (network stall, SDK stuck) OR the
    // offering resolves but the Paywall composable does not paint because internal
    // productDetails-lookup silently no-ops, the user sees a mystery-broken tap.
    // Bound the wait so the user always gets feedback within ~6 seconds.
    LaunchedEffect(showPaywall, offering) {
        if (showPaywall && !isPremium && offering == null) {
            delay(6_000)
            // Re-check inside the coroutine — a fast fetch that completed during
            // the delay flips `offering` non-null and this branch no-ops.
            if (offering == null) {
                Log.e(
                    "RevenueCatPaywall",
                    "Offering fetch did not complete within 6s — dismissing to unstick the UI",
                )
                reportOfferingUnavailable(context, analyticsSource, "offering_fetch_timeout", onDismiss)
            }
        }
    }
    
    // Show Paywall as full screen composable with the specific offering
    if (showPaywall && !isPremium && offering != null) {
        Paywall(
            options = PaywallOptions.Builder(
                dismissRequest = {
                    // Track paywall dismissed
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.PremiumPaywallDismissed,
                        mapOf(
                            "source" to analyticsSource,
                            "reason" to "user_dismissed"
                        )
                    )
                    onDismiss()
                }
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
                            NativeAdManager.clear() // Clear native ads as well
                            
                            // Track purchase completed with detailed info
                            val productId = storeTransaction.productIds.firstOrNull() ?: "unknown"
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.PremiumPurchaseCompleted,
                                mapOf(
                                    "source" to analyticsSource,
                                    "product_id" to productId,
                                    "product_ids" to storeTransaction.productIds.joinToString(",")
                                )
                            )
                            com.teamz.lab.debugger.utils.EngagementTracker.trackSignificantAction(
                                context,
                                com.teamz.lab.debugger.utils.SignificantAction.PREMIUM_PURCHASED,
                                mapOf("product_id" to productId, "source" to analyticsSource)
                            )
                            onDismiss()
                            Toast.makeText(context, "Premium activated! Ads removed.", Toast.LENGTH_SHORT).show()
                        }
                        
                        override fun onPurchaseError(error: com.revenuecat.purchases.PurchasesError) {
                            // error.message is localized by the Play Billing library, so
                            // matching on the English word "cancel" reports every non-English
                            // user's cancellation as a hard failure. Compare the error code.
                            val userCancelled =
                                error.code == com.revenuecat.purchases.PurchasesErrorCode.PurchaseCancelledError
                            AnalyticsUtils.logEvent(
                                if (userCancelled) {
                                    AnalyticsEvent.PremiumPurchaseCancelled
                                } else {
                                    AnalyticsEvent.PremiumPurchaseFailed
                                },
                                mapOf(
                                    "source" to analyticsSource,
                                    "error_code" to error.code.name,
                                    "error_message" to (error.message ?: "unknown"),
                                    "user_cancelled" to userCancelled.toString()
                                )
                            )
                        }
                        
                        override fun onRestoreCompleted(customerInfo: com.revenuecat.purchases.CustomerInfo) {
                            // Update premium status first
                            RevenueCatManager.updatePremiumStatus(customerInfo)
                            
                            if (RevenueCatManager.isPremium()) {
                                // Clear all loaded ads immediately
                                android.util.Log.d("RevenueCatPaywall", "Restore completed - clearing all ads")
                                InterstitialAdManager.clearAd()
                                AppOpenAdManager.clearAd()
                                NativeAdManager.clear() // Clear native ads as well
                                
                                // Track restore completed
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.PremiumRestoreCompleted,
                                    mapOf(
                                        "source" to analyticsSource,
                                        "has_premium" to true
                                    )
                                )
                                onDismiss()
                                Toast.makeText(context, "Premium activated! Ads removed.", Toast.LENGTH_SHORT).show()
                            } else {
                                // Track restore completed but no premium found
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.PremiumRestoreCompleted,
                                    mapOf(
                                        "source" to analyticsSource,
                                        "has_premium" to false
                                    )
                                )
                            }
                        }
                        
                        override fun onRestoreError(error: com.revenuecat.purchases.PurchasesError) {
                            // Track restore error
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.PremiumRestoreFailed,
                                mapOf(
                                    "source" to analyticsSource,
                                    "error_code" to error.code.name,
                                    "error_message" to (error.message ?: "unknown")
                                )
                            )
                        }
                    }
                )
                .build()
        )
    }
}

/**
 * Common shutdown path when the paywall cannot render — either the RC dashboard
 * has no offering for this app, the offering exists but every package failed
 * Play Billing productDetails lookup (RC/Play type mismatch), or the SDK never
 * responded in time. In every case: log analytics with a distinct reason, toast
 * a user-facing message, and dismiss so the caller's `showPaywall` flips back.
 */
private fun reportOfferingUnavailable(
    context: Context,
    analyticsSource: String,
    reason: String,
    onDismiss: () -> Unit,
) {
    AnalyticsUtils.logEvent(
        AnalyticsEvent.PremiumPaywallDismissed,
        mapOf("source" to analyticsSource, "reason" to reason),
    )
    Toast.makeText(
        context,
        context.getString(R.string.premium_unavailable_try_later),
        Toast.LENGTH_SHORT,
    ).show()
    onDismiss()
}
