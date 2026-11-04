package com.shikkhasathi.features.checkout.data

import com.shikkhasathi.core.model.CouponValidateBody
import com.shikkhasathi.core.model.CouponValidateResponse
import com.shikkhasathi.core.model.CreateOrderBody
import com.shikkhasathi.core.model.Order
import com.shikkhasathi.core.model.PayBody
import com.shikkhasathi.core.model.PayResponse
import com.shikkhasathi.core.model.PaymentMethod
import com.shikkhasathi.core.network.ApiClient
import com.shikkhasathi.core.network.segment

/** Checkout (contracts `commerce`). The server prices every order; the app sends only ids, coupon and an idempotency key. */
interface OrdersRepository {
    suspend fun create(
        courseId: String,
        couponCode: String?,
        idempotencyKey: String,
    ): Order

    suspend fun pay(
        orderId: String,
        method: PaymentMethod,
        phone: String? = null,
    ): PayResponse

    suspend fun order(orderId: String): Order

    suspend fun validateCoupon(
        code: String,
        courseId: String,
    ): CouponValidateResponse
}

class ApiOrdersRepository(private val api: ApiClient) : OrdersRepository {
    override suspend fun create(
        courseId: String,
        couponCode: String?,
        idempotencyKey: String,
    ): Order = api.post("orders", CreateOrderBody(courseId = courseId, couponCode = couponCode, idempotencyKey = idempotencyKey))

    override suspend fun pay(
        orderId: String,
        method: PaymentMethod,
        phone: String?,
    ): PayResponse = api.post("orders/${segment(orderId)}/pay", PayBody(method = method, phone = phone))

    override suspend fun order(orderId: String): Order = api.get("orders/${segment(orderId)}")

    override suspend fun validateCoupon(
        code: String,
        courseId: String,
    ): CouponValidateResponse = api.post("coupons/validate", CouponValidateBody(code = code.trim().uppercase(), courseId = courseId))
}
