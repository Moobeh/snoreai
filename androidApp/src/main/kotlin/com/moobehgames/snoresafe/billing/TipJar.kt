package com.moobehgames.snoresafe.billing

import android.app.Activity
import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Buy me a coffee" through Google Play. Tips are consumable one-time products that unlock nothing.
 * The only thing the app learns is that a tip happened, which hides every support prompt.
 *
 * Product IDs to create in Play Console (one-time, consumable): tip_coffee, tip_lunch, tip_thanks.
 */
class TipJar(context: Context, private val onTipped: () -> Unit) : PurchasesUpdatedListener {

    enum class Tip(val productId: String) { COFFEE("tip_coffee"), LUNCH("tip_lunch"), THANKS("tip_thanks") }

    sealed interface State {
        data object Loading : State
        data object Unavailable : State
        data class Ready(val prices: Map<Tip, String>) : State
        data object Thanks : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val details = mutableMapOf<Tip, ProductDetails>()

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    _state.value = State.Unavailable
                    return
                }
                loadProducts()
                consumeLeftovers()
            }

            override fun onBillingServiceDisconnected() {
                if (_state.value is State.Loading) _state.value = State.Unavailable
            }
        })
    }

    private fun loadProducts() {
        val products = Tip.entries.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it.productId).setProductType(BillingClient.ProductType.INAPP).build()
        }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { result, response ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.value = State.Unavailable
                return@queryProductDetailsAsync
            }
            details.clear()
            for (d in response.productDetailsList) {
                Tip.entries.firstOrNull { it.productId == d.productId }?.let { details[it] = d }
            }
            val prices = details.mapNotNull { (tip, d) -> d.oneTimePurchaseOfferDetails?.formattedPrice?.let { tip to it } }.toMap()
            _state.value = if (prices.isEmpty()) State.Unavailable else State.Ready(prices)
        }
    }

    fun tip(activity: Activity, tip: Tip) {
        val d = details[tip] ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases?.forEach(::handle)
    }

    /** Tips that went through while the app was closed. */
    private fun consumeLeftovers() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases.forEach(::handle)
        }
    }

    private fun handle(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        // Consuming acknowledges the tip and lets the same size be tipped again later.
        client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { r, _ ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                _state.value = State.Thanks
                onTipped()
            } else {
                Log.w("TipJar", "Consume failed: ${r.debugMessage}")
            }
        }
    }

    fun close() = client.endConnection()
}
