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
 * Google Play subscription client. No private backend/server is used.
 * Product ID must match a subscription created in Play Console.
 */
class OrbitBillingManager(
    context: Context,
    private val onState: (active: Boolean, status: String, price: String?, productReady: Boolean) -> Unit
) {
    companion object {
        const val PREMIUM_PRODUCT_ID = "orbit_premium_monthly"
    }

    private val appContext = context.applicationContext
    private var productDetails: ProductDetails? = null
    private var offerToken: String? = null
    private var connected = false

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                purchases.forEach { processPurchase(it) }
            } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                onState(false, "Não foi possível concluir a compra: ${result.debugMessage}", currentPrice(), productDetails != null)
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
                else onState(false, "Google Play indisponível: $debugMessage", null, false)
            }
            override fun onServiceDisconnected() {
                connected = false
                onState(false, "Conexão com Google Play interrompida.", currentPrice(), productDetails != null)
            }
        })
    }

    private fun loadProductsAndPurchases() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PREMIUM_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = detailsResult.productDetailsList.firstOrNull()
                val offer = productDetails?.subscriptionOfferDetails?.firstOrNull()
                offerToken = offer?.offerToken
                val price = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                onState(false, if (productDetails != null && offerToken != null) {
                    "Plano Premium encontrado no Google Play."
                } else {
                    "O plano ainda não foi publicado ou configurado no Google Play Console."
                }, price, productDetails != null && offerToken != null)
            } else {
                onState(false, "Não foi possível consultar o plano: ${result.debugMessage}", null, false)
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
                val active = purchases.any {
                    it.products.contains(PREMIUM_PRODUCT_ID) &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach { processPurchase(it) }
                if (!active) onState(false, "Você está no plano gratuito.", currentPrice(), productDetails != null && offerToken != null)
            }
        }
    }

    fun launchPurchase(activity: Activity) {
        val details = productDetails
        val token = offerToken
        if (!billingClient.isReady || details == null || token == null) {
            onState(false, "Assinatura indisponível. Confira a configuração do produto no Google Play.", currentPrice(), false)
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
            onState(false, "Não foi possível abrir a compra: ${result.debugMessage}", currentPrice(), true)
        }
    }

    private fun processPurchase(purchase: Purchase) {
        if (!purchase.products.contains(PREMIUM_PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            onState(false, "Aguardando confirmação do pagamento no Google Play.", currentPrice(), productDetails != null && offerToken != null)
            return
        }
        if (purchase.isAcknowledged) {
            onState(true, "SMART ORBIT Premium ativo nesta conta Google Play.", currentPrice(), productDetails != null && offerToken != null)
        } else {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    onState(true, "SMART ORBIT Premium ativo nesta conta Google Play.", currentPrice(), productDetails != null && offerToken != null)
                } else {
                    onState(false, "Compra recebida; aguardando confirmação do Google Play.", currentPrice(), productDetails != null && offerToken != null)
                }
            }
        }
    }

    private fun currentPrice(): String? =
        productDetails?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice

    fun close() {
        if (billingClient.isReady) billingClient.endConnection()
        connected = false
    }
}

/** Small adapter keeps BillingClient callbacks explicit and easy to test. */
private abstract class BillingClientStateListenerCompat : com.android.billingclient.api.BillingClientStateListener {
    abstract fun onSetupFinished(responseCode: Int, debugMessage: String)
    abstract fun onServiceDisconnected()

    override fun onBillingSetupFinished(result: com.android.billingclient.api.BillingResult) {
        onSetupFinished(result.responseCode, result.debugMessage)
    }

    override fun onBillingServiceDisconnected() = onServiceDisconnected()
}
