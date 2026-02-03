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
    val premiumStatusFlow: StateFlow<PremiumStatus> = _premiumStatusFlow.asStateFlow()
    
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
            
            val configuration = PurchasesConfiguration.Builder(context, revenueCatApiKey)
                .appUserID(null) // Let RevenueCat generate anonymous ID
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
            
            // Fetch initial customer info
            fetchCustomerInfo()
            
            isInitialized = true
            Log.d(TAG, "✅ RevenueCat initialized successfully")
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
     */
    private fun fetchCustomerInfo() {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized - cannot fetch customer info")
            return
        }
        
        _premiumStatusFlow.value = PremiumStatus.Loading
        
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                this@RevenueCatManager.customerInfo = customerInfo
                updatePremiumStatus(customerInfo)
            }
            
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                Log.e(TAG, "Failed to fetch customer info: ${error.message}")
                // On error, assume not premium (fail-safe)
                _premiumStatusFlow.value = PremiumStatus.NotPremium
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
     * Check if user has premium (ads-free) status
     * 
     * @return true if user has active premium subscription, false otherwise
     */
    fun isPremium(): Boolean {
        return when (val status = _premiumStatusFlow.value) {
            is PremiumStatus.Premium -> status.isActive
            else -> false
        }
    }

    /**
     * Restore purchases (for users who already purchased on another device)
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
        
        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                Log.d(TAG, "Purchases restored successfully")
                updatePremiumStatus(customerInfo)
                onSuccess()
            }
            
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                Log.e(TAG, "Failed to restore purchases: ${error.message}")
                onError(error.message)
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
                    updatePremiumStatus(customerInfo)
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
                
                // Track purchase attempt
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.DrawerItemClicked,
                    mapOf("item" to "purchase_attempt", "product_id" to (storeProduct?.id ?: product.identifier))
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
                            
                            // Track purchase success
                            val purchasedProductId = (product.product as? StoreProduct)?.id ?: product.identifier
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.DrawerItemClicked,
                                mapOf("item" to "purchase_success", "product_id" to purchasedProductId)
                            )
                            
                            onSuccess()
                        }
                        
                        override fun onError(
                            error: com.revenuecat.purchases.PurchasesError,
                            userCancelled: Boolean
                        ) {
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

