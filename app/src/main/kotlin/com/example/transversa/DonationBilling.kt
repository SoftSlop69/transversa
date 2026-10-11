// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

data class DonationOffer(
    val productId: String,
    val productDetails: ProductDetails,
    val offerToken: String,
    val formattedPrice: String,
    val billingPeriodLabel: String? = null,
)

enum class DonationPurchaseStatus { NONE, PENDING, SUCCESS, FAILED }

data class DonationBillingState(
    val offers: Map<String, DonationOffer> = emptyMap(),
    val isLoading: Boolean = true,
    val purchaseStatus: DonationPurchaseStatus = DonationPurchaseStatus.NONE,
    val isRentSubscriptionActive: Boolean = false,
)

/** Play one-time donations, retaining management support for existing Rent subscribers. */
class DonationBilling(activity: Activity) : PurchasesUpdatedListener {
    private val appContext: Context = activity.applicationContext
    private val productIds = listOf("donation_small", "donation_medium", "donation_socks")
    private val rentProductId = "rent_monthly"
    private var productQueriesRemaining = 2
    val state: MutableState<DonationBillingState> = mutableStateOf(DonationBillingState())

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            com.android.billingclient.api.PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    init {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryDonationProducts()
                    queryRentSubscription()
                    queryUnprocessedPurchases(BillingClient.ProductType.INAPP, ::processDonation)
                    queryUnprocessedPurchases(BillingClient.ProductType.SUBS, ::processSubscription)
                } else {
                    state.value = state.value.copy(isLoading = false)
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun launchPurchase(activity: Activity, productId: String) {
        val offer = state.value.offers[productId] ?: return
        val itemParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(offer.productDetails)
        if (offer.offerToken.isNotBlank()) itemParamsBuilder.setOfferToken(offer.offerToken)
        val itemParams = itemParamsBuilder.build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(itemParams))
            .build()
        val result = billingClient.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            state.value = state.value.copy(purchaseStatus = DonationPurchaseStatus.FAILED)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach { purchase ->
                if (purchase.products.contains(rentProductId)) processSubscription(purchase)
                else processDonation(purchase)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                state.value = state.value.copy(purchaseStatus = DonationPurchaseStatus.NONE)
            }
            else -> state.value = state.value.copy(purchaseStatus = DonationPurchaseStatus.FAILED)
        }
    }

    fun close() {
        billingClient.endConnection()
    }

    private fun queryDonationProducts() {
        val products = productIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        queryProductDetails(products) { details ->
            details.mapNotNull { productDetails ->
                val offer = productDetails.oneTimePurchaseOfferDetailsList?.firstOrNull()
                    ?: productDetails.oneTimePurchaseOfferDetails
                    ?: return@mapNotNull null
                DonationOffer(
                    productId = productDetails.productId,
                    productDetails = productDetails,
                    offerToken = offer.offerToken.orEmpty(),
                    formattedPrice = offer.formattedPrice,
                )
            }
        }
    }

    private fun queryRentSubscription() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(rentProductId)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        queryProductDetails(listOf(product)) { details ->
            details.mapNotNull { productDetails ->
                val monthlyOffer = productDetails.subscriptionOfferDetails.orEmpty()
                    .mapNotNull { offer ->
                        val monthlyPhase = offer.pricingPhases.pricingPhaseList.lastOrNull { phase ->
                            phase.billingPeriod == "P1M" &&
                                phase.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING
                        } ?: return@mapNotNull null
                        offer to monthlyPhase
                    }
                    .firstOrNull()
                    ?: return@mapNotNull null
                DonationOffer(
                    productId = productDetails.productId,
                    productDetails = productDetails,
                    offerToken = monthlyOffer.first.offerToken,
                    formattedPrice = monthlyOffer.second.formattedPrice,
                    billingPeriodLabel = "month",
                )
            }
        }
    }

    private fun queryProductDetails(
        products: List<QueryProductDetailsParams.Product>,
        toOffers: (List<ProductDetails>) -> List<DonationOffer>,
    ) {
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            val found = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                toOffers(queryResult.productDetailsList).associateBy(DonationOffer::productId)
            } else {
                emptyMap()
            }
            val remaining = (productQueriesRemaining - 1).coerceAtLeast(0)
            productQueriesRemaining = remaining
            state.value = state.value.copy(
                offers = state.value.offers + found,
                isLoading = remaining > 0,
            )
        }
    }

    private fun queryUnprocessedPurchases(productType: String, processor: (Purchase) -> Unit) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(productType)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                if (productType == BillingClient.ProductType.SUBS) {
                    state.value = state.value.copy(
                        isRentSubscriptionActive = purchases.any {
                            rentProductId in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
                        },
                    )
                }
                purchases.forEach(processor)
            }
        }
    }

    private fun processDonation(purchase: Purchase) {
        if (purchase.products.none(productIds::contains)) return
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            state.value = state.value.copy(purchaseStatus = DonationPurchaseStatus.PENDING)
            return
        }
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val params = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billingClient.consumeAsync(params) { result, _ ->
            state.value = state.value.copy(
                purchaseStatus = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    DonationPurchaseStatus.SUCCESS
                } else {
                    DonationPurchaseStatus.FAILED
                },
            )
        }
    }

    private fun processSubscription(purchase: Purchase) {
        if (rentProductId !in purchase.products) return
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            state.value = state.value.copy(purchaseStatus = DonationPurchaseStatus.PENDING)
            return
        }
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        state.value = state.value.copy(isRentSubscriptionActive = true)

        if (purchase.isAcknowledged) {
            state.value = state.value.copy(purchaseStatus = DonationPurchaseStatus.SUCCESS)
            return
        }
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            state.value = state.value.copy(
                purchaseStatus = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    DonationPurchaseStatus.SUCCESS
                } else {
                    DonationPurchaseStatus.FAILED
                },
            )
        }
    }
}
