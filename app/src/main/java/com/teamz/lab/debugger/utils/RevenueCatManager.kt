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
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.AnalyticsEvent
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
    private const val PREMIUM_ENTITLEMENT_ID = "premium"
    
    // Product IDs - these should match your RevenueCat products
    // Lifetime premium product ID for $2.99 one-time purchase
    private const val LIFETIME_PRODUCT_ID = "lifetime_premium"
    
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
            val revenueCatApiKey = apiKey ?: getApiKeyFromConfig(context)
            
            if (revenueCatApiKey.isNullOrEmpty()) {
                Log.w(TAG, "RevenueCat API key not found - premium features will be disabled")
                _premiumStatusFlow.value = PremiumStatus.NotPremium
                isInitialized = true
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
            isInitialized = true
        }
    }
    
    /**
     * Get API key from BuildConfig (set from local_config.properties at build time)
     */
    private fun getApiKeyFromConfig(context: Context): String? {
        val apiKey = BuildConfig.REVENUECAT_API_KEY
        return if (apiKey.isNotEmpty()) apiKey else null
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
    private fun updatePremiumStatus(customerInfo: CustomerInfo) {
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
     * Get current premium status
     */
    fun getPremiumStatus(): PremiumStatus {
        return _premiumStatusFlow.value
    }
    
    /**
     * Show purchase flow for premium subscription
     * 
     * @param activity Activity to show purchase flow
     * @param onSuccess Callback when purchase succeeds
     * @param onError Callback when purchase fails or is cancelled
     */
    fun purchasePremium(
        activity: Activity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized - cannot purchase")
            onError("RevenueCat not initialized")
            return
        }
        
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                val currentOffering = offerings.current
                if (currentOffering == null) {
                    Log.e(TAG, "No current offering available")
                    onError("No subscription available")
                    return
                }
                
                // Get the lifetime premium product (prioritize lifetime over subscriptions)
                val product = currentOffering.availablePackages.firstOrNull { 
                    it.identifier.contains("lifetime", ignoreCase = true) || 
                    it.product.identifier.contains("lifetime", ignoreCase = true)
                } ?: currentOffering.availablePackages.firstOrNull { 
                    it.identifier.contains("premium", ignoreCase = true) ||
                    it.product.identifier.contains("premium", ignoreCase = true)
                } ?: currentOffering.availablePackages.firstOrNull()
                
                if (product == null) {
                    Log.e(TAG, "No premium product found in offering")
                    onError("No premium product available")
                    return
                }
                
                // Purchase the product
                Purchases.sharedInstance.purchase(
                    com.revenuecat.purchases.PurchaseParams.Builder(activity, product).build(),
                    object : PurchaseCallback {
                        override fun onCompleted(
                            transaction: StoreTransaction,
                            customerInfo: CustomerInfo
                        ) {
                            Log.d(TAG, "Purchase successful")
                            updatePremiumStatus(customerInfo)
                            onSuccess()
                        }
                        
                        override fun onError(
                            error: com.revenuecat.purchases.PurchasesError,
                            userCancelled: Boolean
                        ) {
                            if (userCancelled) {
                                Log.d(TAG, "User cancelled purchase")
                                onError("Purchase cancelled")
                            } else {
                                Log.e(TAG, "Purchase failed: ${error.message}")
                                onError(error.message ?: "Purchase failed")
                            }
                        }
                    }
                )
            }
            
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                Log.e(TAG, "Failed to get offerings: ${error.message}")
                onError(error.message ?: "Failed to load subscriptions")
            }
        })
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
                onError(error.message ?: "Failed to restore purchases")
            }
        })
    }
    
    /**
     * Get available products for display
     * 
     * @param onSuccess Callback with list of products
     * @param onError Callback when fetching fails
     */
    fun getAvailableProducts(
        onSuccess: (List<StoreProduct>) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (!isInitialized) {
            onError("RevenueCat not initialized")
            return
        }
        
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                val currentOffering = offerings.current
                if (currentOffering == null) {
                    onError("No offerings available")
                    return
                }
                
                val products = currentOffering.availablePackages.mapNotNull { packageInfo -> 
                    packageInfo.product as? StoreProduct
                }
                onSuccess(products)
            }
            
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                onError(error.message ?: "Failed to load products")
            }
        })
    }
    
    /**
     * Sync customer info (useful after purchase or restore)
     */
    fun syncCustomerInfo() {
        if (isInitialized) {
            fetchCustomerInfo()
        }
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
        
        Purchases.sharedInstance.logIn(userId, object : LogInCallback {
            override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {
                Log.d(TAG, "User ID set: $userId (created: $created)")
                updatePremiumStatus(customerInfo)
            }
            
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                Log.e(TAG, "Failed to set user ID: ${error.message}")
            }
        })
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
     * Show RevenueCat paywall directly - fetches offerings and triggers purchase flow
     * This method directly presents the purchase flow without intermediate dialogs
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
            Log.w(TAG, "Not initialized - cannot show paywall")
            onError("RevenueCat not initialized")
            return
        }
        
        // Track analytics
        AnalyticsUtils.logEvent(
            AnalyticsEvent.DrawerItemClicked,
            mapOf("item" to "show_paywall", "source" to "direct_paywall")
        )
        
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                val currentOffering = offerings.current
                if (currentOffering == null) {
                    Log.e(TAG, "No current offering available")
                    onError("No subscription available. Please try again later.")
                    return
                }
                
                // Prioritize lifetime product, fallback to any premium product
                val lifetimeProduct = currentOffering.availablePackages.firstOrNull { 
                    it.identifier.contains("lifetime", ignoreCase = true) || 
                    it.product.identifier.contains("lifetime", ignoreCase = true)
                }
                
                val product = lifetimeProduct ?: currentOffering.availablePackages.firstOrNull { 
                    it.identifier.contains("premium", ignoreCase = true) ||
                    it.product.identifier.contains("premium", ignoreCase = true)
                } ?: currentOffering.availablePackages.firstOrNull()
                
                if (product == null) {
                    Log.e(TAG, "No premium product found in offering")
                    onError("No premium product available. Please try again later.")
                    return
                }
                
                Log.d(TAG, "Showing paywall for product: ${product.identifier}, price: ${product.product.price}")
                
                // Track purchase attempt
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.DrawerItemClicked,
                    mapOf("item" to "purchase_attempt", "product_id" to product.product.identifier)
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
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.DrawerItemClicked,
                                mapOf("item" to "purchase_success", "product_id" to product.product.identifier)
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
}

