package com.shikkhasathi.features.checkout.presentation

import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.asUiText
import com.shikkhasathi.core.model.Order
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.presentation.toUiText
import com.shikkhasathi.features.checkout.data.OrdersRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CheckoutResultUiState {
    /** Verifying with the server (the gateway's own redirect status is only a hint). */
    data object Checking : CheckoutResultUiState

    /** Still pending after the wait — the payment may settle later; offer to check again. */
    data object StillPending : CheckoutResultUiState

    data class Paid(val order: Order) : CheckoutResultUiState

    data class Failed(val message: UiText) : CheckoutResultUiState
}

/**
 * `https://shikkhasathi.com/checkout/result?order=…&status=…` — where the gateway sends the student back
 * (App Link → this screen). The gateway's `status` is only a hint: the order is always re-read from the API.
 * No order id means the gateway never created one.
 */
class CheckoutResultViewModel(
    private val orderId: String?,
    private val orders: OrdersRepository,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
    private val pollTimeoutMs: Long = POLL_TIMEOUT_MS,
) : SsViewModel() {
    private val mutableState =
        MutableStateFlow<CheckoutResultUiState>(
            if (orderId == null) CheckoutResultUiState.Failed(Str.CheckoutFailedBody.asUiText()) else CheckoutResultUiState.Checking,
        )
    val state: StateFlow<CheckoutResultUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    init {
        if (orderId != null) check()
    }

    fun check() {
        val id = orderId ?: return
        job?.cancel()
        mutableState.value = CheckoutResultUiState.Checking
        job =
            scope.launch {
                mutableState.value =
                    try {
                        val order = awaitFinalOrder(orders, id, pollIntervalMs, pollTimeoutMs)
                        when {
                            order == null -> CheckoutResultUiState.StillPending
                            order.status.isPaid -> CheckoutResultUiState.Paid(order)
                            else -> CheckoutResultUiState.Failed(Str.CheckoutFailedBody.asUiText())
                        }
                    } catch (e: ApiException) {
                        CheckoutResultUiState.Failed(e.error.toUiText())
                    }
            }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val POLL_TIMEOUT_MS = 60_000L
    }
}
