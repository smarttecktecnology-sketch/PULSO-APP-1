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
 * Google Play subscription client. Product IDs must exist in Play Console.
 * Purchases are queried on-device; production release should add server-side verification.
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
        const val PLUS_PRODUCT_ID = "orbit_plus_monthly"
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
        val productList = listOf(PLUS_PRODUCT_ID, MAX_PRODUCT_ID).map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                detailsResult.productDetailsList.forEach { details ->
                    products[details.productId] = details
                    details.subscriptionOfferDetails?.firstOrNull()?.offerToken?.let { offers[details.productId] = it }
                }
                val missing = listOf(PLUS_PRODUCT_ID, MAX_PRODUCT_ID).filter { !isReady(it) }
                emit(if (missing.isEmpty()) {
                    "Planos ORBIT PLUS e ORBIT MAX encontrados no Google Play."
                } else {
                    "Configure no Google Play Console os produtos: ${missing.joinToString()}."
                })
            } else {
                emit("Não foi possível consultar os planos: ${result.debugMessage}")
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
                activeTier = purchases
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .let { owned ->
                        when {
                            owned.any { it.products.contains(MAX_PRODUCT_ID) } -> OrbitPlanTier.MAX
                            owned.any { it.products.contains(PLUS_PRODUCT_ID) } -> OrbitPlanTier.PLUS
                            else -> OrbitPlanTier.FREE
                        }
                    }
                purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
                    .forEach { processPurchase(it) }
                emit(if (activeTier == OrbitPlanTier.FREE) "Você está no ORBIT FREE." else "Assinatura ORBIT ativa nesta conta Google Play.")
            } else {
                emit("Não foi possível atualizar as assinaturas: ${result.debugMessage}")
            }
        }
    }

    fun launchPurchase(activity: Activity, tier: OrbitPlanTier) {
        val productId = when (tier) {
            OrbitPlanTier.PLUS -> PLUS_PRODUCT_ID
            OrbitPlanTier.MAX -> MAX_PRODUCT_ID
            OrbitPlanTier.FREE -> return
        }
        val details = products[productId]
        val token = offers[productId]
        if (!billingClient.isReady || details == null || token == null) {
            emit("Este plano ainda não está disponível. Confira se o produto mensal foi publicado no Google Play Console.")
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
        if (!purchase.products.contains(PLUS_PRODUCT_ID) && !purchase.products.contains(MAX_PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            emit("Aguardando confirmação do pagamento no Google Play.")
            return
        }
        if (purchase.products.contains(MAX_PRODUCT_ID)) activeTier = OrbitPlanTier.MAX
        else if (activeTier != OrbitPlanTier.MAX) activeTier = OrbitPlanTier.PLUS
        if (purchase.isAcknowledged) {
            emit("Assinatura ORBIT confirmada pelo Google Play.")
            return
        }
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                emit("Assinatura ORBIT confirmada pelo Google Play.")
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
            formattedPrice(PLUS_PRODUCT_ID), formattedPrice(MAX_PRODUCT_ID),
            isReady(PLUS_PRODUCT_ID), isReady(MAX_PRODUCT_ID)
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
