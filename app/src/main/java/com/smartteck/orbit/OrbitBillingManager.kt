package com.smartteck.orbit

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play billing client for the single paid subscription: ORBIT MAX.
 * Product ID must match the subscription configured in Play Console.
 * Production release should add server-side purchase verification.
 */
class OrbitBillingManager(
    context: Context,
    private val onState: (
        activeTier: OrbitPlanTier,
        status: String,
        plusPrice: String?,
        maxPrice: String?,
        plusReady: Boolean,
        maxReady: Boolean
    ) -> Unit
) {
    companion object {
        const val MAX_PRODUCT_ID = "orbit_max_monthly"
    }

    private val appContext = context.applicationContext
    private val products = mutableMapOf<String, ProductDetails>()
    private val offers = mutableMapOf<String, String>()
    private var connected = false
    private var activeTier = OrbitPlanTier.FREE

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                purchases.forEach { processPurchase(it) }
                refreshPurchases()
            } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                emit("Não foi possível concluir a compra: ${result.debugMessage}")
            }
        }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        if (billingClient.isReady) {
            connected = true
            loadProductsAndPurchases()
            return
        }
        billingClient.startConnection(object : BillingClientStateListenerCompat() {
            override fun onSetupFinished(responseCode: Int, debugMessage: String) {
                connected = responseCode == BillingClient.BillingResponseCode.OK
                if (connected) loadProductsAndPurchases()
                else emit("Google Play indisponível: $debugMessage")
            }
            override fun onServiceDisconnected() {
                connected = false
                emit("Conexão com Google Play interrompida.")
            }
        })
    }

    private fun loadProductsAndPurchases() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(MAX_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        products.clear()
        offers.clear()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                detailsResult.productDetailsList.forEach { details ->
                    products[details.productId] = details
                    details.subscriptionOfferDetails?.firstOrNull()?.offerToken?.let { offers[details.productId] = it }
                }
                emit(if (isReady(MAX_PRODUCT_ID)) {
                    "ORBIT MAX encontrado no Google Play."
                } else {
                    "Configure o produto $MAX_PRODUCT_ID no Google Play Console."
                })
            } else {
                emit("Não foi possível consultar o ORBIT MAX: ${result.debugMessage}")
            }
        }
        refreshPurchases()
    }

    fun refreshPurchases() {
        if (!billingClient.isReady) {
            connect()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                activeTier = if (purchases.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.contains(MAX_PRODUCT_ID)
                }) OrbitPlanTier.MAX else OrbitPlanTier.FREE
                purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
                    .forEach { processPurchase(it) }
                emit(if (activeTier == OrbitPlanTier.FREE) "Você está usando o acesso sem assinatura." else "Assinatura ORBIT MAX ativa nesta conta Google Play.")
            } else {
                emit("Não foi possível atualizar a assinatura: ${result.debugMessage}")
            }
        }
    }

    fun launchPurchase(activity: Activity, tier: OrbitPlanTier) {
        if (tier != OrbitPlanTier.MAX) return
        val details = products[MAX_PRODUCT_ID]
        val token = offers[MAX_PRODUCT_ID]
        if (!billingClient.isReady || details == null || token == null) {
            emit("ORBIT MAX ainda não está disponível. Confira se o produto mensal foi publicado no Google Play Console.")
            connect()
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(token)
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val result = billingClient.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            emit("Não foi possível abrir a compra: ${result.debugMessage}")
        }
    }

    private fun processPurchase(purchase: Purchase) {
        if (!purchase.products.contains(MAX_PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            emit("Aguardando confirmação do pagamento no Google Play.")
            return
        }
        activeTier = OrbitPlanTier.MAX
        if (purchase.isAcknowledged) {
            emit("Assinatura ORBIT MAX confirmada pelo Google Play.")
            return
        }
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                emit("Assinatura ORBIT MAX confirmada pelo Google Play.")
                refreshPurchases()
            } else {
                emit("Compra recebida; aguardando confirmação do Google Play.")
            }
        }
    }

    private fun isReady(productId: String): Boolean = products.containsKey(productId) && offers.containsKey(productId)

    private fun formattedPrice(productId: String): String? =
        products[productId]?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice

    private fun emit(status: String) {
        onState(
            activeTier, status,
            null, formattedPrice(MAX_PRODUCT_ID),
            false, isReady(MAX_PRODUCT_ID)
        )
    }

    fun close() {
        if (billingClient.isReady) billingClient.endConnection()
        connected = false
    }
}

private abstract class BillingClientStateListenerCompat : com.android.billingclient.api.BillingClientStateListener {
    abstract fun onSetupFinished(responseCode: Int, debugMessage: String)
    abstract fun onServiceDisconnected()

    override fun onBillingSetupFinished(result: com.android.billingclient.api.BillingResult) {
        onSetupFinished(result.responseCode, result.debugMessage)
    }

    override fun onBillingServiceDisconnected() = onServiceDisconnected()
}
