package com.ddw.nukeyauto.billing

import android.app.Activity
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.ddw.nukeyauto.data.PrefKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Google Play Billing for optional "support the developer" donations.
 * Products are consumable so users can donate multiple times.
 */
class BillingHelper(
    private val activity: Activity,
    private val prefs: SharedPreferences,
    private val onPurchaseComplete: (Boolean) -> Unit
) {
    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User cancelled purchase")
        } else {
            Log.e(TAG, "Purchase failed: ${billingResult.debugMessage}")
            onPurchaseComplete(false)
        }
    }

    private val billingClient = BillingClient.newBuilder(activity)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    /**
     * Connect to Google Play and query available support products.
     */
    fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing connected")
                    queryProducts()
                    // Handle any purchases that were not yet consumed
                    processPendingPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing disconnected")
            }
        })
    }

    /**
     * Query the available in-app products defined in Play Console.
     */
    private fun queryProducts() {
        val productList = PRODUCT_IDS.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // Sort by price (lowest first)
                val sorted = queryResult.productDetailsList.sortedBy { product ->
                    product.oneTimePurchaseOfferDetails?.priceAmountMicros ?: 0L
                }
                _products.value = sorted
                Log.d(TAG, "Found ${sorted.size} products")
            } else {
                Log.e(TAG, "Query products failed: ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Launch the Google Play purchase flow for the given product.
     */
    fun launchPurchase(productDetails: ProductDetails) {
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )
            )
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    /**
     * Consume the purchase so the user can buy it again (donation = consumable).
     */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.consumeAsync(consumeParams) { billingResult, _ ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Purchase consumed successfully")
                    val count = prefs.getInt(PrefKeys.DONATION_COUNT, 0) + 1
                    prefs.edit {
                        putBoolean(PrefKeys.IS_SUPPORTER, true)
                        putInt(PrefKeys.DONATION_COUNT, count)
                    }
                    onPurchaseComplete(true)
                } else {
                    Log.e(TAG, "Consume failed: ${billingResult.debugMessage}")
                    onPurchaseComplete(false)
                }
            }
        }
    }

    /**
     * Check for any purchases made but not yet consumed (e.g. app crashed before consuming).
     */
    private fun processPendingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchaseList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchaseList) {
                    handlePurchase(purchase)
                }
            }
        }
    }

    /**
     * Disconnect from Google Play Billing. Call in onDestroy().
     */
    fun disconnect() {
        billingClient.endConnection()
    }

    companion object {
        private const val TAG = "BillingHelper"

        /** Product IDs matching the ones created in Google Play Console. */
        const val PRODUCT_SMALL = "support_small"
        const val PRODUCT_MEDIUM = "support_medium"
        const val PRODUCT_LARGE = "support_large"
        val PRODUCT_IDS = listOf(PRODUCT_SMALL, PRODUCT_MEDIUM, PRODUCT_LARGE)

        /**
         * Check if the user has donated at least once.
         */
        fun isSupporter(prefs: SharedPreferences): Boolean {
            return prefs.getBoolean(PrefKeys.IS_SUPPORTER, false)
        }

        /**
         * Get the total number of donations.
         */
        fun getDonationCount(prefs: SharedPreferences): Int {
            return prefs.getInt(PrefKeys.DONATION_COUNT, 0)
        }
    }
}
