package com.moneta.app.data.plus

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** The one-time in-app product created in Play Console (Monetize > Products > In-app products). */
const val PLUS_PRODUCT_ID = "moneta_plus"

data class BillingUi(
    /** The price as Google Play shows it for this user's country, e.g. "Rs 1,200.00". Null until loaded. */
    val price: String? = null,
    val message: String? = null,
    val busy: Boolean = false
)

/**
 * Talks to Google Play Billing for the single one-time "Moneta Plus" purchase. The result is
 * remembered on the phone, so Plus keeps working offline. When the app starts and Play answers,
 * the remembered result is corrected (for example after a refund). If Play can't be reached,
 * nothing is changed, so being offline never takes Plus away.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext context: Context,
    private val plusRepository: PlusRepository
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _ui = MutableStateFlow(BillingUi())
    val ui: StateFlow<BillingUi> = _ui.asStateFlow()

    private var details: ProductDetails? = null
    private var announceResult = false

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /** Loads the price and re-checks what the user owns. [userInitiated] also reports the outcome on screen. */
    fun refresh(userInitiated: Boolean = false) {
        announceResult = userInitiated
        if (userInitiated) _ui.update { it.copy(busy = true, message = null) }
        withConnection(
            onReady = {
                loadDetails()
                queryOwned()
            },
            onFailed = {
                if (userInitiated) {
                    _ui.update { it.copy(busy = false, message = "Google Play isn't available right now. Check your connection and try again.") }
                }
            }
        )
    }

    fun purchase(activity: Activity) {
        _ui.update { it.copy(message = null) }
        withConnection(
            onReady = {
                val product = details
                if (product == null) {
                    _ui.update { it.copy(message = "The price isn't available yet. Check your connection and try again.") }
                    loadDetails()
                } else {
                    val flow = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(
                            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build())
                        )
                        .build()
                    client.launchBillingFlow(activity, flow)
                }
            },
            onFailed = {
                _ui.update { it.copy(message = "Google Play isn't available. Install Moneta from Google Play to buy Plus.") }
            }
        )
    }

    private fun withConnection(onReady: () -> Unit, onFailed: () -> Unit) {
        if (client.isReady) {
            onReady()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) onReady() else onFailed()
            }

            override fun onBillingServiceDisconnected() {
                // The next action reconnects.
            }
        })
    }

    private fun loadDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PLUS_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details = list.firstOrNull()
                val price = details?.oneTimePurchaseOfferDetails?.formattedPrice
                _ui.update { it.copy(price = price) }
            }
        }
    }

    private fun queryOwned() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases, authoritative = true)
            } else {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handlePurchases(purchases.orEmpty(), authoritative = false)
            BillingClient.BillingResponseCode.USER_CANCELED -> _ui.update { it.copy(message = null) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh(userInitiated = true)
            else -> _ui.update { it.copy(message = "The purchase didn't go through. You were not charged.") }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>, authoritative: Boolean) {
        val mine = purchases.filter { PLUS_PRODUCT_ID in it.products }
        val owned = mine.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        when {
            owned != null -> {
                scope.launch { plusRepository.setPurchased(true) }
                if (!owned.isAcknowledged) {
                    val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(owned.purchaseToken).build()
                    client.acknowledgePurchase(ack) { }
                }
                _ui.update { it.copy(busy = false, message = "Moneta Plus is unlocked. Thank you!") }
            }
            mine.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> {
                _ui.update { it.copy(busy = false, message = "Your payment is pending. Plus unlocks as soon as it completes.") }
            }
            authoritative -> {
                // Play answered and there is no purchase: refunded, or never bought on this account.
                scope.launch { plusRepository.setPurchased(false) }
                _ui.update {
                    it.copy(
                        busy = false,
                        message = if (announceResult) "No Plus purchase was found on this Google account." else it.message
                    )
                }
            }
            else -> _ui.update { it.copy(busy = false) }
        }
    }
}
