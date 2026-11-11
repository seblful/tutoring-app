package com.seblful.klas.pro

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.seblful.klas.di.ApplicationContext
import com.seblful.klas.domain.pro.ProPlan
import com.seblful.klas.runCatchingCancellable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred

/**
 * [ProStore] over Google Play Billing. The products are set up in the Play Console (see `docs/releasing.md`):
 * [LIFETIME_ID], a one-time product, and [MONTHLY_ID], a subscription whose monthly base plan carries the free
 * trial as an offer Play gives only to an account that has not had it.
 *
 * No server checks the purchase: the app is offline by design and its source is open, so Play's own answer on the
 * phone is the whole check. A purchase is acknowledged here, or Play refunds it after three days.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class PlayProStore @Inject constructor(
    @param:ApplicationContext context: Context,
    private val resumed: ResumedActivity,
) : ProStore {

    /** The sheet's ending, which Play reports to the listener rather than to the call that opened it. */
    private var sheet: CompletableDeferred<PurchaseOutcome>? = null

    private val listener = PurchasesUpdatedListener { result, purchases ->
        sheet?.complete(
            when (result.responseCode) {
                BillingResponseCode.OK -> outcomeOf(purchases.orEmpty())
                BillingResponseCode.USER_CANCELED -> PurchaseOutcome.Cancelled
                else -> PurchaseOutcome.Failed
            },
        )
        sheet = null
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override val sells: Boolean = true

    override val manageUrl: String =
        "https://play.google.com/store/account/subscriptions?sku=$MONTHLY_ID&package=${context.packageName}"

    override suspend fun owned(): Ownership? {
        if (!connected()) return null
        val results = listOf(ProductType.INAPP, ProductType.SUBS).map { type ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build())
        }
        return results.takeIf { all -> all.all { it.billingResult.responseCode == BillingResponseCode.OK } }
            ?.flatMap { it.purchasesList }
            ?.also { purchases ->
                purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { acknowledge(it) }
            }
            ?.let(::ownershipOf)
    }

    override suspend fun offers(): ProOffers? {
        val details = details() ?: return null
        val monthly = details[MONTHLY_ID]?.subscriptionOfferDetails?.let { offers ->
            val base = offers.firstOrNull { it.offerId == null } ?: return@let null
            val trial = offers.firstOrNull { it.offerId != null && it.freeDays() != null }
            ProOffers.Price(
                formatted = base.pricingPhases.pricingPhaseList.last().formattedPrice,
                trialDays = trial?.freeDays(),
            )
        }
        val lifetime = details[LIFETIME_ID]?.oneTimePurchaseOfferDetails?.let { ProOffers.Price(it.formattedPrice) }
        return ProOffers(monthly = monthly, lifetime = lifetime)
    }

    /** Call on the main thread: Play opens its sheet from there. */
    override suspend fun buy(plan: ProPlan): PurchaseOutcome {
        val flow = details()?.get(plan.productId)?.let { flowFor(plan, it) }
        val activity = resumed.activity
        return when {
            flow == null -> PurchaseOutcome.Unavailable
            activity == null -> PurchaseOutcome.Failed
            else -> launch(activity, flow)
        }
    }

    /** The sheet's parameters; for the subscription, its trial offer while Play still gives it, else the base plan. */
    private fun flowFor(plan: ProPlan, product: ProductDetails): BillingFlowParams? {
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
        if (plan == ProPlan.MONTHLY) {
            val offer = product.subscriptionOfferDetails?.minByOrNull { if (it.freeDays() != null) 0 else 1 }
                ?: return null
            params.setOfferToken(offer.offerToken)
        }
        return BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params.build())).build()
    }

    private suspend fun launch(activity: Activity, flow: BillingFlowParams): PurchaseOutcome {
        val ending = CompletableDeferred<PurchaseOutcome>()
        sheet?.complete(PurchaseOutcome.Cancelled)
        sheet = ending
        val launched = client.launchBillingFlow(activity, flow)
        if (launched.responseCode == BillingResponseCode.OK) {
            // Asking again acknowledges what was just bought.
            return ending.await().also { if (it is PurchaseOutcome.Bought) owned() }
        }
        sheet = null
        val alreadyOwned = if (launched.responseCode == BillingResponseCode.ITEM_ALREADY_OWNED) owned()?.plan else null
        return alreadyOwned?.let { PurchaseOutcome.Bought(it) } ?: launched.unavailableOrFailed()
    }

    private suspend fun details(): Map<String, ProductDetails>? {
        if (!connected()) return null
        val products = listOf(LIFETIME_ID to ProductType.INAPP, MONTHLY_ID to ProductType.SUBS).map { (id, type) ->
            QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()
        }
        val result = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(products).build())
        return if (result.billingResult.responseCode == BillingResponseCode.OK) {
            result.productDetailsList.orEmpty().associateBy { it.productId }
        } else {
            null
        }
    }

    /** Connects once; afterwards the client reconnects by itself ([BillingClient.Builder.enableAutoServiceReconnection]). */
    private suspend fun connected(): Boolean {
        if (client.isReady) return true
        val ready = CompletableDeferred<Boolean>()
        val started = runCatchingCancellable {
            client.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        ready.complete(result.responseCode == BillingResponseCode.OK)
                    }

                    override fun onBillingServiceDisconnected() {
                        ready.complete(false)
                    }
                },
            )
        }.isSuccess
        return started && ready.await()
    }

    private suspend fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        runCatchingCancellable {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            )
        }
    }

    private fun outcomeOf(purchases: List<Purchase>): PurchaseOutcome {
        val owned = ownershipOf(purchases)
        val plan = owned.plan
        return when {
            plan != null -> PurchaseOutcome.Bought(plan)
            owned.pending -> PurchaseOutcome.Pending
            else -> PurchaseOutcome.Failed
        }
    }

    private companion object {
        const val LIFETIME_ID = "klas_pro_lifetime"
        const val MONTHLY_ID = "klas_pro"
        const val DAYS_PER_WEEK = 7
        const val DAYS_PER_MONTH = 30

        val ProPlan.productId: String
            get() = when (this) {
                ProPlan.MONTHLY -> MONTHLY_ID
                ProPlan.LIFETIME -> LIFETIME_ID
            }

        /** The plan the [purchases] pay for, the one-time product winning; acknowledging is [owned]'s. */
        fun ownershipOf(purchases: List<Purchase>): Ownership {
            fun has(id: String, state: Int) = purchases.any { id in it.products && it.purchaseState == state }
            val plan = when {
                has(LIFETIME_ID, Purchase.PurchaseState.PURCHASED) -> ProPlan.LIFETIME
                has(MONTHLY_ID, Purchase.PurchaseState.PURCHASED) -> ProPlan.MONTHLY
                else -> null
            }
            return Ownership(plan, pending = purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING })
        }

        /** Days free at the start of [this] offer, when its first phase costs nothing. */
        fun ProductDetails.SubscriptionOfferDetails.freeDays(): Int? = pricingPhases.pricingPhaseList
            .firstOrNull()
            ?.takeIf { it.priceAmountMicros == 0L }
            ?.let { days(it.billingPeriod) }

        /** An ISO 8601 period as Play writes a trial's — «P7D», «P1W», «P1M» — in days; a month counts 30. */
        fun days(period: String): Int? {
            val match = Regex("""P(\d+)([DWM])""").matchEntire(period) ?: return null
            val n = match.groupValues[1].toInt()
            return when (match.groupValues[2]) {
                "D" -> n
                "W" -> n * DAYS_PER_WEEK
                else -> n * DAYS_PER_MONTH
            }
        }

        fun BillingResult.unavailableOrFailed(): PurchaseOutcome = when (responseCode) {
            BillingResponseCode.BILLING_UNAVAILABLE,
            BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingResponseCode.ITEM_UNAVAILABLE,
            -> PurchaseOutcome.Unavailable
            else -> PurchaseOutcome.Failed
        }
    }
}
