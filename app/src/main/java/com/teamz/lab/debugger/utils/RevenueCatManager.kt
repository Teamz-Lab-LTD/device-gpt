package com.teamz.lab.debugger.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.Store
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.LogInCallback
import com.revenuecat.purchases.models.StoreProduct
import com.revenuecat.purchases.models.StoreTransaction
import com.teamz.lab.debugger.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RevenueCat Manager for managing subscriptions and ad removal
 * 
 * This manager provides:
 * - Premium status checking (for ad removal)
 * - Subscription purchase flow
 * - Real-time subscription status updates
 * - Free subscription support (for ad removal)
 * 
 * Usage:
 * - Call initialize() in Application.onCreate()
 * - Check isPremium() before showing ads
 * - Use purchasePremium() to show purchase flow
 * - Observe premiumStatusFlow for reactive UI updates
 */
object RevenueCatManager {
    private const val TAG = "RevenueCatManager"
    
    // Entitlement identifier for premium features (configure in RevenueCat dashboard)
    // This should match the entitlement ID you set up in RevenueCat dashboard
    const val PREMIUM_ENTITLEMENT_ID = "device-gpt-lifetime"

    // Offering ID - this should match your RevenueCat offering ID
    const val OFFERING_ID = "device-gpt-offering"
    
    // Paywall identifier - the name of the paywall designed in RevenueCat console
    const val PAYWALL_IDENTIFIER = "device-gpt"

    // State flow for reactive premium status updates
    private val _premiumStatusFlow = MutableStateFlow<PremiumStatus>(PremiumStatus.Unknown)

    /**
     * Premium status flow that respects DEBUG_FORCE_FREE_USER flag.
     * All Compose UI should use this (not _premiumStatusFlow directly).
     */
    val premiumStatusFlow: StateFlow<PremiumStatus>
        get() = if (DEBUG_FORCE_FREE_USER) {
            MutableStateFlow(PremiumStatus.NotPremium)
        } else {
            _premiumStatusFlow.asStateFlow()
        }
    
    private var isInitialized = false
    private var customerInfo: CustomerInfo? = null
    
    /**
     * Premium status states
     */
    sealed class PremiumStatus {
        object Unknown : PremiumStatus()
        object Loading : PremiumStatus()
        data class Premium(val isActive: Boolean, val expirationDate: String? = null) : PremiumStatus()
        object NotPremium : PremiumStatus()
    }
    
    /**
     * Initialize RevenueCat SDK
     * 
     * @param context Application context
     * @param apiKey RevenueCat API key (get from RevenueCat dashboard)
     * 
     * Note: For security, consider storing API key in local_config.properties
     */
    fun initialize(context: Context, apiKey: String? = null) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return
        }
        
        try {
            // Get API key from local config or use provided one
            val revenueCatApiKey = apiKey ?: getApiKeyFromConfig()
            
            if (revenueCatApiKey.isNullOrEmpty()) {
                Log.w(TAG, "RevenueCat API key not found - premium features will be disabled")
                _premiumStatusFlow.value = PremiumStatus.NotPremium
                // Don't set isInitialized = true here since Purchases.configure() won't be called
                // This prevents setUserId() from trying to access uninitialized Purchases.sharedInstance
                return
            }
            
            // Check if Firebase Auth user already exists - use their UID for cross-device sync
            // This ensures purchases are linked immediately if user is already authenticated
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            val initialUserId = firebaseUser?.uid
            
            if (initialUserId != null) {
                Log.d(TAG, "Firebase user detected at initialization: $initialUserId - using as RevenueCat user ID")
            } else {
                Log.d(TAG, "No Firebase user at initialization - RevenueCat will use anonymous ID (will link when user authenticates)")
            }
            
            val configuration = PurchasesConfiguration.Builder(context, revenueCatApiKey)
                .appUserID(initialUserId) // Use Firebase UID if available, otherwise anonymous
                .store(Store.PLAY_STORE)
                .build()
            
            Purchases.configure(configuration)
            
            // Set up listener for subscription status updates
            Purchases.sharedInstance.updatedCustomerInfoListener = object : UpdatedCustomerInfoListener {
                override fun onReceived(customerInfo: CustomerInfo) {
                    Log.d(TAG, "Customer info updated")
                    updatePremiumStatus(customerInfo)
                }
            }
            
            isInitialized = true
            Log.d(TAG, "✅ RevenueCat initialized successfully${if (initialUserId != null) " with user ID: $initialUserId" else " (anonymous)"}")
            
            // Fetch initial customer info after a short delay to ensure Google Play is ready
            // This is especially important for anonymous users on new devices
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                fetchCustomerInfo()
            }, 500) // Small delay to ensure Google Play services are ready
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize RevenueCat", e)
            ErrorHandler.handleError(e, context = "RevenueCatManager.initialize")
            _premiumStatusFlow.value = PremiumStatus.NotPremium
            // Don't set isInitialized = true here - Purchases.configure() might not have been called
            // This prevents setUserId() and other methods from trying to access uninitialized Purchases.sharedInstance
        }
    }
    
    /**
     * Get API key from BuildConfig (set from local_config.properties at build time)
     */
    private fun getApiKeyFromConfig(): String? {
        val apiKey = BuildConfig.REVENUECAT_API_KEY
        return apiKey.ifEmpty { null }
    }
    
    /**
     * Fetch current customer info and update premium status
     * For anonymous users, this will automatically sync with Google Play purchases
     * and restore any purchases tied to the current Google account
     * 
     * IMPORTANT: Google Play purchases are tied to the Google account, not RevenueCat user ID.
     * So even if user is anonymous on a new device, purchases can be restored from Google Play.
     */
    private fun fetchCustomerInfo() {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized - cannot fetch customer info")
            return
        }
        
        _premiumStatusFlow.value = PremiumStatus.Loading
        
        // Check if user is anonymous - this helps us understand the context
        val isAnonymous = FirebaseAuth.getInstance().currentUser?.isAnonymous ?: true
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        Log.d(TAG, "Fetching customer info (user: $currentUserId, anonymous: $isAnonymous)")
        
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                this@RevenueCatManager.customerInfo = customerInfo
                updatePremiumStatus(customerInfo)
                
                // Check if premium exists
                val hasPremium = customerInfo.entitlements.active[PREMIUM_ENTITLEMENT_ID] != null
                
                if (hasPremium) {
                    Log.d(TAG, "✅ Premium found in customer info")
                } else {
                    Log.d(TAG, "No premium found in customer info, attempting automatic restore from Google Play...")
                    Log.d(TAG, "Note: Google Play purchases are tied to Google account, so restore should work even for anonymous users")
                    
                    // CRITICAL: Always attempt to restore purchases from Google Play if no premium found
                    // This works for both anonymous and authenticated users because:
                    // 1. Google Play purchases are tied to the Google account signed into the device
                    // 2. RevenueCat's restorePurchases() queries Google Play directly
                    // 3. This handles cross-device scenarios where user is anonymous on new device
                    Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                        override fun onReceived(restoredCustomerInfo: CustomerInfo) {
                            val restoredPremium = restoredCustomerInfo.entitlements.active[PREMIUM_ENTITLEMENT_ID]
                            if (restoredPremium != null) {
                                Log.d(TAG, "✅ Premium automatically restored from Google Play!")
                                Log.d(TAG, "This purchase was restored even though user is ${if (isAnonymous) "anonymous" else "authenticated"}")
                                this@RevenueCatManager.customerInfo = restoredCustomerInfo
                                updatePremiumStatus(restoredCustomerInfo)
                            } else {
                                Log.d(TAG, "No purchases found in Google Play for this Google account")
                                Log.d(TAG, "This could mean: 1) User never purchased, 2) Different Google account, or 3) Purchase not yet synced")
                            }
                        }
                        
                        override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                            // Log the error but don't fail - this is expected if user never purchased
                            Log.d(TAG, "Restore attempt completed: ${error.message}")
                            Log.d(TAG, "Error code: ${error.code.name}")
                            // Don't set premium status here - keep it as NotPremium
                        }
                    })
                }
            }
            
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                Log.e(TAG, "Failed to fetch customer info: ${error.message}")
                Log.e(TAG, "Error code: ${error.code.name}")
                
                // Even if fetch fails, try to restore purchases directly from Google Play
                // This is a fallback for cases where RevenueCat customer info fetch fails
                Log.d(TAG, "Attempting direct restore from Google Play as fallback...")
                Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                    override fun onReceived(restoredCustomerInfo: CustomerInfo) {
                        val restoredPremium = restoredCustomerInfo.entitlements.active[PREMIUM_ENTITLEMENT_ID]
                        if (restoredPremium != null) {
                            Log.d(TAG, "✅ Premium restored from Google Play (fallback restore)!")
                            this@RevenueCatManager.customerInfo = restoredCustomerInfo
                            updatePremiumStatus(restoredCustomerInfo)
                        } else {
                            Log.d(TAG, "No purchases found in fallback restore")
                            _premiumStatusFlow.value = PremiumStatus.NotPremium
                        }
                    }
                    
                    override fun onError(restoreError: com.revenuecat.purchases.PurchasesError) {
                        Log.e(TAG, "Fallback restore also failed: ${restoreError.message}")
                        _premiumStatusFlow.value = PremiumStatus.NotPremium
                    }
                })
            }
        })
    }
    
    /**
     * Update premium status from customer info
     */
    fun updatePremiumStatus(customerInfo: CustomerInfo) {
        val entitlement = customerInfo.entitlements.active[PREMIUM_ENTITLEMENT_ID]
        
        if (entitlement != null) {
            val isActive = entitlement.isActive
            val expirationDate = entitlement.expirationDate?.toString()
            _premiumStatusFlow.value = PremiumStatus.Premium(isActive, expirationDate)
            Log.d(TAG, "User has premium: active=$isActive, expires=$expirationDate")
        } else {
            _premiumStatusFlow.value = PremiumStatus.NotPremium
            Log.d(TAG, "User does not have premium entitlement")
        }
    }
    
    /**
     * DEBUG ONLY: Force non-premium mode for testing paywall flows.
     * Set to true to simulate a free user even if you have premium.
     * ⚠️ MUST be false before release!
     */
    var DEBUG_FORCE_FREE_USER = false

    /**
     * Check if user has premium (ads-free) status
     *
     * @return true if user has active premium subscription, false otherwise
     */
    fun isPremium(): Boolean {
        if (DEBUG_FORCE_FREE_USER) {
            Log.w(TAG, "⚠️ DEBUG_FORCE_FREE_USER is ON — treating user as non-premium for testing")
            return false
        }
        return when (val status = _premiumStatusFlow.value) {
            is PremiumStatus.Premium -> status.isActive
            else -> false
        }
    }

    /**
     * Restore purchases (for users who already purchased on another device)
     * 
     * This method queries Google Play directly for purchases tied to the current Google account.
     * It works even for anonymous users because Google Play purchases are tied to the Google account,
     * not the RevenueCat user ID.
     * 
     * @param onSuccess Callback when restore succeeds
     * @param onError Callback when restore fails
     */
    fun restorePurchases(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized - cannot restore")
            onError("RevenueCat not initialized")
            return
        }
        
        // Check if user is anonymous - this helps with debugging
        val isAnonymous = FirebaseAuth.getInstance().currentUser?.isAnonymous ?: true
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        Log.d(TAG, "Manual restore initiated (user: $currentUserId, anonymous: $isAnonymous)")
        Log.d(TAG, "Note: Restore works for anonymous users because Google Play purchases are tied to Google account")
        
        // Track restore initiated
        AnalyticsUtils.logEvent(
            AnalyticsEvent.PremiumRestoreInitiated,
            mapOf(
                "source" to "manual_restore",
                "is_anonymous" to isAnonymous.toString()
            )
        )
        
        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                Log.d(TAG, "Restore completed - checking for premium...")
                this@RevenueCatManager.customerInfo = customerInfo
                updatePremiumStatus(customerInfo)
                
                // Check if premium was actually restored
                val hasPremium = RevenueCatManager.isPremium()
                if (hasPremium) {
                    Log.d(TAG, "✅ Premium successfully restored!")
                } else {
                    Log.d(TAG, "⚠️ Restore completed but no premium found")
                    Log.d(TAG, "This could mean: 1) User never purchased, 2) Different Google account, or 3) Purchase not yet synced")
                }
                
                // Track restore completed
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.PremiumRestoreCompleted,
                    mapOf(
                        "has_premium" to hasPremium,
                        "source" to "manual_restore",
                        "is_anonymous" to isAnonymous.toString()
                    )
                )
                
                onSuccess()
            }
            
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                Log.e(TAG, "Failed to restore purchases: ${error.message}")
                Log.e(TAG, "Error code: ${error.code.name}")
                
                // Track restore failed
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.PremiumRestoreFailed,
                    mapOf(
                        "error_code" to error.code.name,
                        "error_message" to (error.message ?: "unknown"),
                        "source" to "manual_restore",
                        "is_anonymous" to isAnonymous.toString()
                    )
                )
                
                // Provide more helpful error message based on error code name
                val errorMessage = when (error.code.name) {
                    "PURCHASE_NOT_ALLOWED", "PURCHASE_INVALID" -> 
                        "Purchase restore not allowed. Please check your Google Play account."
                    "NETWORK_ERROR", "NETWORK_ERROR_CODE" -> 
                        "Network error. Please check your internet connection and try again."
                    "STORE_PROBLEM" -> 
                        "Google Play Store issue. Please try again later."
                    else -> 
                        error.message ?: "Failed to restore purchases. Please ensure you're signed in with the same Google account used for purchase."
                }
                
                onError(errorMessage)
            }
        })
    }

    /**
     * Get the lifetime premium product price dynamically
     * 
     * @param onSuccess Callback with formatted price string (e.g., "$2.99")
     * @param onError Callback when fetching fails (will use fallback)
     */
    fun getLifetimeProductPrice(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (!isInitialized) {
            onError("RevenueCat not initialized")
            return
        }
        
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                // Use specific offering ID "device-gpt-offering"
                val currentOffering = offerings.getOffering(OFFERING_ID) ?: offerings.current
                if (currentOffering == null) {
                    onError("No offerings available")
                    return
                }
                
                // Find lifetime product
                val lifetimeProduct = currentOffering.availablePackages.firstOrNull { 
                    it.identifier.contains("lifetime", ignoreCase = true) || 
                    (it.product as? StoreProduct)?.id?.contains("lifetime", ignoreCase = true) == true
                } ?: currentOffering.availablePackages.firstOrNull { 
                    it.identifier.contains("premium", ignoreCase = true) ||
                    (it.product as? StoreProduct)?.id?.contains("premium", ignoreCase = true) == true
                } ?: currentOffering.availablePackages.firstOrNull()
                
                val storeProduct = lifetimeProduct?.product as? StoreProduct
                if (storeProduct != null) {
                    // Get formatted price from StoreProduct
                    // StoreProduct.price is a Price object
                    val priceString = try {
                        val price = storeProduct.price
                        val amount = price.amountMicros / 1_000_000.0
                        val currencyCode = price.currencyCode
                        
                        // Format as currency using NumberFormat
                        val currency = java.util.Currency.getInstance(currencyCode)
                        val formatter = java.text.NumberFormat.getCurrencyInstance(
                            java.util.Locale.getDefault()
                        )
                        formatter.currency = currency
                        formatter.format(amount)
                    } catch (e: Exception) {
                        // Fallback: simple format
                        val amount = storeProduct.price.amountMicros / 1_000_000.0
                        val currencyCode = storeProduct.price.currencyCode
                        "$currencyCode $amount"
                    }
                    Log.d(TAG, "Lifetime product price: $priceString")
                    onSuccess(priceString)
                } else {
                    onError("No lifetime product found")
                }
            }
            
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                Log.e(TAG, "Failed to get product price: ${error.message}")
                onError(error.message ?: "Failed to load price")
            }
        })
    }

    /**
     * Set user ID (for cross-device sync)
     * 
     * @param userId User identifier (e.g., Firebase Auth UID)
     */
    fun setUserId(userId: String) {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized - cannot set user ID")
            return
        }
        
        try {
            // Check if Purchases is actually configured before accessing sharedInstance
            // This handles the case where isInitialized is true but configure() wasn't called
            Purchases.sharedInstance.logIn(userId, object : LogInCallback {
                override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {
                    Log.d(TAG, "User ID set: $userId (created: $created)")
                    this@RevenueCatManager.customerInfo = customerInfo
                    updatePremiumStatus(customerInfo)
                    
                    // After linking user ID, check if premium exists
                    // If not, restore purchases from Google Play (for cross-device purchases)
                    val hasPremium = customerInfo.entitlements.active[PREMIUM_ENTITLEMENT_ID] != null
                    if (!hasPremium) {
                        Log.d(TAG, "No premium found after user ID link, attempting to restore purchases from Google Play...")
                        // Restore purchases to sync with Google Play account
                        // This is critical for cross-device purchase restoration
                        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                            override fun onReceived(restoredCustomerInfo: CustomerInfo) {
                                val restoredPremium = restoredCustomerInfo.entitlements.active[PREMIUM_ENTITLEMENT_ID]
                                if (restoredPremium != null) {
                                    Log.d(TAG, "✅ Premium restored from Google Play after user ID link!")
                                    this@RevenueCatManager.customerInfo = restoredCustomerInfo
                                    updatePremiumStatus(restoredCustomerInfo)
                                } else {
                                    Log.d(TAG, "No purchases found in Google Play for this account after user ID link")
                                }
                            }
                            
                            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                                // Silent fail - this is expected if user never purchased
                                Log.d(TAG, "No purchases to restore after user ID link (this is normal for new users): ${error.message}")
                            }
                        })
                    } else {
                        Log.d(TAG, "✅ Premium already active after user ID link")
                    }
                }
                
                override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                    Log.e(TAG, "Failed to set user ID: ${error.message}")
                }
            })
        } catch (e: kotlin.UninitializedPropertyAccessException) {
            // RevenueCat not configured - this can happen if API key was missing
            Log.w(TAG, "RevenueCat not configured - cannot set user ID. This is expected if API key is missing.")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting user ID: ${e.message}", e)
        }
    }
    
    /**
     * Log out current user
     */
    fun logOut() {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized - cannot log out")
            return
        }
        
        Purchases.sharedInstance.logOut(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                Log.d(TAG, "Logged out successfully")
                updatePremiumStatus(customerInfo)
            }
            
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                Log.e(TAG, "Failed to log out: ${error.message}")
            }
        })
    }
    
    /**
     * Purchase product directly - called from PremiumPurchaseDialog when user clicks "Get Premium"
     * This method fetches offerings and triggers the purchase flow
     * 
     * @param activity Activity to show purchase flow
     * @param onSuccess Callback when purchase succeeds
     * @param onError Callback when purchase fails or is cancelled
     * @param onDismiss Callback when user dismisses without purchasing
     */
    fun purchaseProduct(
        activity: Activity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized - cannot purchase. Check if REVENUECAT_API_KEY is set in local_config.properties")
            onError("RevenueCat not initialized. Please check your configuration.")
            return
        }
        
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                Log.d(TAG, "Received offerings. Available offering IDs: ${offerings.all.keys}")
                
                // Use specific offering ID "device-gpt-offering"
                val currentOffering = offerings.getOffering(OFFERING_ID) ?: offerings.current
                if (currentOffering == null) {
                    Log.e(TAG, "No offering available for ID: $OFFERING_ID. Available offerings: ${offerings.all.keys}")
                    onError("No subscription available. Please check your RevenueCat configuration.")
                    return
                }
                
                Log.d(TAG, "Using offering: ${currentOffering.identifier} with ${currentOffering.availablePackages.size} packages")
                
                // Prioritize lifetime product, fallback to any premium product
                val lifetimeProduct = currentOffering.availablePackages.firstOrNull { 
                    it.identifier.contains("lifetime", ignoreCase = true) || 
                    (it.product as? StoreProduct)?.id?.contains("lifetime", ignoreCase = true) == true
                }
                
                val product = lifetimeProduct ?: currentOffering.availablePackages.firstOrNull { 
                    it.identifier.contains("premium", ignoreCase = true) ||
                    (it.product as? StoreProduct)?.id?.contains("premium", ignoreCase = true) == true
                } ?: currentOffering.availablePackages.firstOrNull()
                
                if (product == null) {
                    Log.e(TAG, "No premium product found in offering")
                    onError("No premium product available. Please try again later.")
                    return
                }
                
                val storeProduct = product.product as? StoreProduct
                Log.d(TAG, "Purchasing product: ${product.identifier}, price: ${storeProduct?.price}")
                
                // Track purchase initiated
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.PremiumPurchaseInitiated,
                    mapOf(
                        "product_id" to (storeProduct?.id ?: product.identifier),
                        "product_price" to (storeProduct?.price?.formatted ?: "unknown"),
                        "offering_id" to OFFERING_ID
                    )
                )
                
                // Purchase the product directly
                Purchases.sharedInstance.purchase(
                    com.revenuecat.purchases.PurchaseParams.Builder(activity, product).build(),
                    object : PurchaseCallback {
                        override fun onCompleted(
                            transaction: StoreTransaction,
                            customerInfo: CustomerInfo
                        ) {
                            Log.d(TAG, "Purchase successful: ${transaction.productIds}")
                            updatePremiumStatus(customerInfo)
                            
                            // Track purchase completed
                            val purchasedProductId = (product.product as? StoreProduct)?.id ?: product.identifier
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.PremiumPurchaseCompleted,
                                mapOf(
                                    "product_id" to purchasedProductId,
                                    "product_ids" to transaction.productIds.joinToString(","),
                                    "source" to "direct_purchase"
                                )
                            )
                            
                            onSuccess()
                        }
                        
                        override fun onError(
                            error: com.revenuecat.purchases.PurchasesError,
                            userCancelled: Boolean
                        ) {
                            // Track purchase error
                            AnalyticsUtils.logEvent(
                                if (userCancelled) {
                                    AnalyticsEvent.PremiumPurchaseCancelled
                                } else {
                                    AnalyticsEvent.PremiumPurchaseFailed
                                },
                                mapOf(
                                    "product_id" to ((product.product as? StoreProduct)?.id ?: product.identifier),
                                    "error_code" to error.code.name,
                                    "error_message" to (error.message ?: "unknown"),
                                    "user_cancelled" to userCancelled.toString(),
                                    "source" to "direct_purchase"
                                )
                            )
                            if (userCancelled) {
                                Log.d(TAG, "User cancelled purchase")
                                // Track cancellation
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.DrawerItemClicked,
                                    mapOf("item" to "purchase_cancelled")
                                )
                                onDismiss()
                            } else {
                                Log.e(TAG, "Purchase failed: ${error.message}")
                                // Track purchase failure
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.DrawerItemClicked,
                                    mapOf("item" to "purchase_failed", "error" to (error.message ?: "unknown"))
                                )
                                onError(error.message ?: "Purchase failed. Please try again.")
                            }
                        }
                    }
                )
            }
            
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                Log.e(TAG, "Failed to get offerings: ${error.message}")
                // Track error
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.DrawerItemClicked,
                    mapOf("item" to "paywall_load_failed", "error" to (error.message ?: "unknown"))
                )
                onError(error.message ?: "Failed to load subscription options. Please check your internet connection.")
            }
        })
    }
    
    /**
     * Show RevenueCat paywall designed in RevenueCat console
     * Displays the paywall named "device-gpt" from RevenueCat console
     * Uses purchaseProduct as fallback since PaywallActivityLauncher API may not be available
     * 
     * @param activity Activity to show purchase flow
     * @param onSuccess Callback when purchase succeeds
     * @param onError Callback when purchase fails or is cancelled
     * @param onDismiss Callback when user dismisses without purchasing
     */
    fun showPaywall(
        activity: Activity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized - cannot show paywall. Check if REVENUECAT_API_KEY is set in local_config.properties")
            onError("RevenueCat not initialized. Please check your configuration.")
            return
        }
        
        // Track analytics
        AnalyticsUtils.logEvent(
            AnalyticsEvent.DrawerItemClicked,
            mapOf("item" to "show_paywall", "source" to "revenuecat_paywall", "paywall_name" to PAYWALL_IDENTIFIER)
        )
        
        // Use purchaseProduct which will use the offering configured in RevenueCat console
        // The paywall "device-gpt" will be used from the offering configuration
        purchaseProduct(activity, onSuccess, onError, onDismiss)
    }
}

