package com.omniread.app.util

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
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
import com.omniread.app.data.repo.PaymentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed class PurchaseResult {
    data class Success(val sku: String, val coinsBalance: Int?) : PurchaseResult()
    data class SubscriptionSuccess(val sku: String) : PurchaseResult()
    data class Failed(val sku: String, val message: String) : PurchaseResult()
    data object Cancelled : PurchaseResult()
}

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val payments: PaymentRepository,
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _purchaseResult = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 4)
    val purchaseResult: SharedFlow<PurchaseResult> = _purchaseResult.asSharedFlow()

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var pendingPurchaseSku: String? = null
    @Volatile private var connecting = false

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        connect()
    }

    private fun connect() {
        if (client.isReady) {
            _connected.value = true
            scope.launch { queryProducts() }
            return
        }
        if (connecting) return
        connecting = true
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        _connected.value = true
                        scope.launch { queryProducts() }
                    } else {
                        _connected.value = false
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connecting = false
                    _connected.value = false
                    scope.launch {
                        delay(5_000)
                        connect()
                    }
                }
            })
        } catch (_: Exception) {
            connecting = false
            _connected.value = false
        }
    }

    private suspend fun queryProducts() {
        val inAppProducts = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("coins_50")
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("coins_150")
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("coins_350")
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("coins_800")
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("coins_2000")
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        )
        val subscriptionProducts = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("vip_monthly")
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
        )

        val products = runCatching {
            queryProductGroup(inAppProducts) + queryProductGroup(subscriptionProducts)
        }.getOrElse {
            emptyList()
        }
        _productDetails.value = products.associateBy { it.productId }
    }

    private suspend fun queryProductGroup(products: List<QueryProductDetailsParams.Product>): List<ProductDetails> {
        if (products.isEmpty()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        return suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { result, details ->
                val found = if (result.responseCode == BillingClient.BillingResponseCode.OK) details else emptyList()
                cont.resume(found)
            }
        }
    }

    fun launchCoinPurchase(activity: Activity, sku: String) {
        if (!requireReady(sku)) return
        val details = _productDetails.value[sku]
        if (details == null) {
            scope.launch { _purchaseResult.emit(PurchaseResult.Failed(sku, "Product not found. Check Play Console setup or try again.")) }
            return
        }
        pendingPurchaseSku = sku
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        launchBillingFlow(activity, sku, flowParams)
    }

    fun launchSubscription(activity: Activity, sku: String) {
        if (!requireReady(sku)) return
        val details = _productDetails.value[sku]
        if (details == null) {
            scope.launch { _purchaseResult.emit(PurchaseResult.Failed(sku, "Product not found. Check Play Console setup or try again.")) }
            return
        }
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: run {
            scope.launch { _purchaseResult.emit(PurchaseResult.Failed(sku, "No offer available.")) }
            return
        }
        pendingPurchaseSku = sku
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        launchBillingFlow(activity, sku, flowParams)
    }

    private fun requireReady(sku: String): Boolean {
        if (client.isReady) return true
        refreshConnection()
        scope.launch {
            _purchaseResult.emit(PurchaseResult.Failed(sku, "Google Play Billing is still connecting. Try again in a moment."))
        }
        return false
    }

    private fun launchBillingFlow(activity: Activity, sku: String, flowParams: BillingFlowParams) {
        val result = try {
            client.launchBillingFlow(activity, flowParams)
        } catch (e: Exception) {
            scope.launch {
                _purchaseResult.emit(
                    PurchaseResult.Failed(sku, e.message ?: "Could not open Google Play Billing.")
                )
            }
            return
        }

        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> Unit
            BillingClient.BillingResponseCode.USER_CANCELED ->
                scope.launch { _purchaseResult.emit(PurchaseResult.Cancelled) }
            else ->
                scope.launch { _purchaseResult.emit(PurchaseResult.Failed(sku, billingMessage(result))) }
        }
    }

    private fun billingMessage(result: BillingResult): String {
        return result.debugMessage.takeIf { it.isNotBlank() }
            ?: when (result.responseCode) {
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Google Play Billing is unavailable on this device."
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "This product is not available for this app build."
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Google Play Billing service is unavailable."
                BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "Billing setup rejected this app build."
                else -> "Billing could not be started."
            }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val sku = pendingPurchaseSku ?: ""
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        scope.launch { handlePurchase(purchase) }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                scope.launch { _purchaseResult.emit(PurchaseResult.Cancelled) }
            else ->
                scope.launch { _purchaseResult.emit(PurchaseResult.Failed(sku, result.debugMessage)) }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        val sku = purchase.products.firstOrNull() ?: return
        try {
            if (sku == "vip_monthly") {
                payments.verifySubscription(sku, purchase.purchaseToken)
                acknowledgeIfNeeded(purchase)
                _purchaseResult.emit(PurchaseResult.SubscriptionSuccess(sku))
            } else {
                val idempotency = UUID.randomUUID().toString()
                val result = payments.verifyCoinPurchase(sku, purchase.purchaseToken, idempotency)
                consumePurchase(purchase)
                val balance = try {
                    (result as? kotlinx.serialization.json.JsonObject)
                        ?.get("balance")
                        ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }
                } catch (_: Exception) { null }
                _purchaseResult.emit(PurchaseResult.Success(sku, balance))
            }
        } catch (e: Exception) {
            _purchaseResult.emit(PurchaseResult.Failed(sku, e.message ?: "Verification failed"))
        }
    }

    private suspend fun acknowledgeIfNeeded(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            suspendCancellableCoroutine { cont ->
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                ) { cont.resume(Unit) }
            }
        }
    }

    private suspend fun consumePurchase(purchase: Purchase) {
        suspendCancellableCoroutine { cont ->
            client.consumeAsync(
                ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            ) { _, _ -> cont.resume(Unit) }
        }
    }

    fun refreshConnection() {
        if (!client.isReady) connect()
        else scope.launch { queryProducts() }
    }
}
