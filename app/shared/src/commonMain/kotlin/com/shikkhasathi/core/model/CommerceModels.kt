package com.shikkhasathi.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors of website/src/api/contracts/commerce.ts (student side: orders, pay, coupons).

/** `ORDER_STATUSES`. */
@Serializable
enum class OrderStatus {
    @SerialName("pending")
    Pending,

    @SerialName("paid")
    Paid,

    @SerialName("failed")
    Failed,

    @SerialName("cancelled")
    Cancelled,

    @SerialName("refunded")
    Refunded,

    @SerialName("partially_refunded")
    PartiallyRefunded,
    ;

    /** Paid (or paid and later partly refunded): the student owns the course. */
    val isPaid: Boolean get() = this == Paid || this == PartiallyRefunded

    val isFinal: Boolean get() = this != Pending
}

/** `PAYMENT_METHODS`, in the order the checkout shows them (bKash first: most Bangladeshi students use it). */
@Serializable
enum class PaymentMethod {
    @SerialName("bkash")
    Bkash,

    @SerialName("nagad")
    Nagad,

    @SerialName("rocket")
    Rocket,

    @SerialName("card")
    Card,
}

@Serializable
data class OrderItem(
    val id: String,
    val courseId: String? = null,
    val bundleId: String? = null,
    val title: String,
    val price: Int = 0,
    val discount: Int = 0,
    val coverColor: String? = null,
    val slug: String? = null,
)

@Serializable
data class OrderRefund(
    val status: String,
    val amount: Int = 0,
    val reason: String = "",
)

@Serializable
data class Order(
    val id: String,
    val status: OrderStatus = OrderStatus.Pending,
    val items: List<OrderItem> = emptyList(),
    val subtotal: Int = 0,
    val discount: Int = 0,
    val total: Int = 0,
    val couponCode: String? = null,
    val paymentMethod: String? = null,
    val createdAt: String = "",
    val paidAt: String? = null,
    val invoiceNumber: String? = null,
    val refund: OrderRefund? = null,
    val refundEligible: Boolean = false,
    /** `/learn/{courseId}/{lessonId}` once paid. */
    val firstLessonPath: String? = null,
)

@Serializable
data class CreateOrderBody(
    val courseId: String? = null,
    val bundleId: String? = null,
    val couponCode: String? = null,
    /** Client-generated so retries / double taps return the same order. */
    val idempotencyKey: String,
)

@Serializable
data class PayBody(
    val method: PaymentMethod,
    val phone: String? = null,
)

@Serializable
data class PayResponse(
    val redirectUrl: String,
    val gateway: String = "",
)

@Serializable
data class CouponValidateBody(
    val code: String,
    val courseId: String? = null,
    val bundleId: String? = null,
)

@Serializable
data class CouponValidateResponse(
    val valid: Boolean,
    val discount: Int = 0,
    val total: Int = 0,
    /** Bangla, from the server ("কুপনের মেয়াদ শেষ"). */
    val message: String = "",
)
