package com.shikkhasathi.features.checkout.presentation

import com.shikkhasathi.core.common.ExternalBrowser
import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.dataOrNull
import com.shikkhasathi.core.datastore.randomId
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.asUiText
import com.shikkhasathi.core.model.CourseDetail
import com.shikkhasathi.core.model.Order
import com.shikkhasathi.core.model.OrderStatus
import com.shikkhasathi.core.model.PaymentMethod
import com.shikkhasathi.core.network.ApiError
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.presentation.toUiText
import com.shikkhasathi.features.checkout.data.OrdersRepository
import com.shikkhasathi.features.explore.data.CatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the order summary shows. Prices are whole taka, always from the server. */
data class CheckoutSummary(
    val title: String,
    val courseCode: String?,
    val sellerName: String?,
    val coverColor: String?,
    val subtotal: Int,
    val discount: Int,
    val total: Int,
) {
    val free: Boolean get() = total == 0
}

sealed interface CouponState {
    data object None : CouponState

    data object Checking : CouponState

    data class Applied(val code: String, val discount: Int, val total: Int, val message: String) : CouponState

    data class Invalid(val message: UiText) : CouponState
}

sealed interface CheckoutStep {
    data object Review : CheckoutStep

    /** The gateway page is open; we poll `GET /orders/:id` until it is paid / failed. */
    data class AwaitingPayment(val orderId: String) : CheckoutStep

    data class Success(val order: Order) : CheckoutStep

    data class Failed(val orderId: String?, val message: UiText) : CheckoutStep
}

data class CheckoutUiState(
    val summary: LoadState<CheckoutSummary> = LoadState.Loading,
    val method: PaymentMethod = PaymentMethod.Bkash,
    val couponInput: String = "",
    val coupon: CouponState = CouponState.None,
    val step: CheckoutStep = CheckoutStep.Review,
    val submitting: Boolean = false,
    val message: UiText? = null,
) {
    /** Total after an applied coupon. */
    val payable: Int?
        get() = (coupon as? CouponState.Applied)?.total ?: summary.dataOrNull?.total
}

/**
 * Checkout for one course (spec §8):
 * 1. summary from `GET /courses/:slug` (or from the order itself when opened by a link with only the id);
 * 2. optional coupon preview (`POST /coupons/validate`);
 * 3. "পেমেন্ট করুন" → `POST /orders` with an idempotency key (one key per coupon, so a double tap or a retry
 *    after a timeout returns the same order) → `POST /orders/:id/pay` → the gateway page opens in a Custom Tab;
 * 4. while the student pays, `GET /orders/:id` is polled (and checked again when the app comes back) until paid / failed.
 * Free courses skip 3–4: the order comes back paid and the student is enrolled.
 */
@Suppress("TooManyFunctions", "LongParameterList") // one intent per checkout control; poll timings injectable for tests
class CheckoutViewModel(
    private val courseId: String,
    private val slug: String?,
    private val orders: OrdersRepository,
    private val catalog: CatalogRepository,
    private val browser: ExternalBrowser,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
    private val pollTimeoutMs: Long = POLL_TIMEOUT_MS,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(CheckoutUiState())
    val state: StateFlow<CheckoutUiState> = mutableState.asStateFlow()

    /** coupon code ("" = none) → idempotency key of the order created for it. */
    private val keys = mutableMapOf<String, String>()
    private var pollJob: Job? = null

    init {
        loadSummary()
    }

    fun retry() = loadSummary()

    fun selectMethod(method: PaymentMethod) = mutableState.update { it.copy(method = method) }

    fun onCouponInput(text: String) =
        mutableState.update {
            it.copy(
                couponInput = text.uppercase().take(COUPON_MAX),
                coupon = if (it.coupon is CouponState.Applied) it.coupon else CouponState.None
            )
        }

    fun applyCoupon() {
        val code = state.value.couponInput.trim()
        if (code.length < COUPON_MIN) {
            mutableState.update { it.copy(coupon = CouponState.Invalid(Str.CheckoutCouponTooShort.asUiText())) }
            return
        }
        mutableState.update { it.copy(coupon = CouponState.Checking) }
        scope.launch {
            try {
                val r = orders.validateCoupon(code, courseId)
                mutableState.update {
                    it.copy(
                        coupon =
                            if (r.valid) {
                                CouponState.Applied(code, r.discount, r.total, r.message)
                            } else {
                                CouponState.Invalid(
                                    r.message.takeIf { m -> m.isNotBlank() }?.let(UiText::Raw) ?: Str.CheckoutCouponInvalid.asUiText()
                                )
                            },
                    )
                }
            } catch (e: ApiException) {
                mutableState.update { it.copy(coupon = CouponState.Invalid(e.error.toUiText())) }
            }
        }
    }

    fun removeCoupon() = mutableState.update { it.copy(coupon = CouponState.None, couponInput = "") }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    /** Primary CTA: "পেমেন্ট করুন" / "ফ্রি-তে এনরোল করুন". */
    fun pay() {
        if (state.value.submitting) return
        val couponCode = (state.value.coupon as? CouponState.Applied)?.code
        val method = state.value.method
        mutableState.update { it.copy(submitting = true, message = null) }
        scope.launch {
            try {
                val order = orders.create(courseId, couponCode, keyFor(couponCode))
                if (order.status.isPaid) {
                    finish(order)
                    return@launch
                }
                if (order.status != OrderStatus.Pending) {
                    // A failed/cancelled order behind this key: the next tap must create a fresh one.
                    keys.remove(couponCode.orEmpty())
                    mutableState.update {
                        it.copy(
                            submitting = false,
                            step = CheckoutStep.Failed(order.id, Str.CheckoutFailedBody.asUiText())
                        )
                    }
                    return@launch
                }
                val redirect = orders.pay(order.id, method).redirectUrl
                if (!browser.open(redirect)) {
                    mutableState.update { it.copy(submitting = false, message = Str.CheckoutBrowserMissing.asUiText()) }
                    return@launch
                }
                mutableState.update { it.copy(submitting = false, step = CheckoutStep.AwaitingPayment(order.id)) }
                watch(order.id)
            } catch (e: ApiException) {
                mutableState.update { it.copy(submitting = false, message = e.error.toUiText()) }
            }
        }
    }

    /** The app came back to the foreground (Custom Tab closed): check the order right away. */
    fun onResume() {
        val waiting = state.value.step as? CheckoutStep.AwaitingPayment ?: return
        watch(waiting.orderId)
    }

    /** "আবার চেষ্টা করুন" after a failed payment: back to the review step with a new order next time. */
    fun startOver() {
        pollJob?.cancel()
        keys.clear()
        mutableState.update { it.copy(step = CheckoutStep.Review, submitting = false) }
    }

    private fun keyFor(couponCode: String?): String = keys.getOrPut(couponCode.orEmpty()) { randomId() }

    private fun watch(orderId: String) {
        pollJob?.cancel()
        pollJob =
            scope.launch {
                val order =
                    try {
                        awaitFinalOrder(orders, orderId, pollIntervalMs, pollTimeoutMs)
                    } catch (e: ApiException) {
                        mutableState.update { it.copy(step = CheckoutStep.Failed(orderId, e.error.toUiText())) }
                        return@launch
                    }
                when {
                    order == null -> Unit // still pending: keep the waiting screen, it re-checks on resume
                    order.status.isPaid -> finish(order)
                    else -> {
                        keys.clear()
                        mutableState.update { it.copy(step = CheckoutStep.Failed(order.id, Str.CheckoutFailedBody.asUiText())) }
                    }
                }
            }
    }

    private fun finish(order: Order) {
        pollJob?.cancel()
        mutableState.update { it.copy(submitting = false, step = CheckoutStep.Success(order)) }
    }

    private fun loadSummary() {
        mutableState.update { it.copy(summary = LoadState.Loading) }
        scope.launch {
            try {
                val summary =
                    if (slug != null) {
                        catalog.course(slug).toSummary()
                    } else {
                        // Opened from a link with only the course id: the (idempotent) pending order carries the price.
                        val order = orders.create(courseId, null, keyFor(null))
                        if (order.status.isPaid) finish(order)
                        order.toSummary()
                    }
                mutableState.update { it.copy(summary = LoadState.Ready(summary)) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(summary = LoadState.Failed(e.error.toUiText())) }
            }
        }
    }

    companion object {
        const val POLL_INTERVAL_MS = 3_000L
        const val POLL_TIMEOUT_MS = 10 * 60_000L
        private const val COUPON_MIN = 3
        private const val COUPON_MAX = 40
    }
}

/**
 * Polls `GET /orders/:id` until the order is no longer pending, or [timeoutMs] passes (null).
 * Network blips while waiting are ignored — the student may be switching apps to bKash.
 */
suspend fun awaitFinalOrder(
    orders: OrdersRepository,
    orderId: String,
    intervalMs: Long,
    timeoutMs: Long,
): Order? {
    var waited = 0L
    while (true) {
        try {
            val order = orders.order(orderId)
            if (order.status.isFinal) return order
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.error.code == ApiError.NOT_FOUND || e.error.code == ApiError.FORBIDDEN) throw e
        }
        if (waited >= timeoutMs) return null
        delay(intervalMs)
        waited += intervalMs
    }
}

private fun CourseDetail.toSummary() =
    CheckoutSummary(
        title = title,
        courseCode = courseCode,
        sellerName = sellerName,
        coverColor = coverColor,
        subtotal = price,
        discount = 0,
        total = price,
    )

private fun Order.toSummary() =
    CheckoutSummary(
        title = items.joinToString(" + ") { it.title },
        courseCode = null,
        sellerName = null,
        coverColor = items.firstOrNull()?.coverColor,
        subtotal = subtotal,
        discount = discount,
        total = total,
    )
