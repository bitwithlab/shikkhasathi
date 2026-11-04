package com.shikkhasathi.features.checkout

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.model.CouponValidateResponse
import com.shikkhasathi.core.model.OrderStatus
import com.shikkhasathi.core.model.PaymentMethod
import com.shikkhasathi.features.checkout.presentation.CheckoutResultUiState
import com.shikkhasathi.features.checkout.presentation.CheckoutResultViewModel
import com.shikkhasathi.features.checkout.presentation.CheckoutStep
import com.shikkhasathi.features.checkout.presentation.CheckoutViewModel
import com.shikkhasathi.features.checkout.presentation.CouponState
import com.shikkhasathi.features.testing.FakeBrowser
import com.shikkhasathi.features.testing.FakeCatalogRepository
import com.shikkhasathi.features.testing.FakeOrdersRepository
import com.shikkhasathi.features.testing.networkError
import com.shikkhasathi.features.testing.order
import com.shikkhasathi.features.testing.uid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    private fun vm(
        orders: FakeOrdersRepository,
        browser: FakeBrowser = FakeBrowser(),
        slug: String? = "ds-midterm-cse-2203",
    ) = CheckoutViewModel(uid(100), slug, orders, FakeCatalogRepository(), browser, pollIntervalMs = 3_000, pollTimeoutMs = 30_000)

    @Test
    fun paysThroughTheGatewayAndPollsUntilPaid() =
        test {
            val orders =
                FakeOrdersRepository().apply {
                    statuses =
                        mutableListOf(
                            OrderStatus.Pending,
                            OrderStatus.Pending,
                            OrderStatus.Paid
                        )
                }
            val browser = FakeBrowser()
            val vm = vm(orders, browser)
            runCurrent()
            assertEquals(349, (vm.state.value.summary as LoadState.Ready).data.total)

            vm.selectMethod(PaymentMethod.Nagad)
            vm.pay()
            runCurrent()
            assertEquals(listOf(uid(500) to PaymentMethod.Nagad), orders.pays)
            assertEquals(listOf("https://sandbox.gateway.test/pay/${uid(500)}"), browser.opened)
            assertIs<CheckoutStep.AwaitingPayment>(vm.state.value.step)

            advanceTimeBy(6_001)
            val step = vm.state.value.step
            assertIs<CheckoutStep.Success>(step)
            assertEquals("/learn/${uid(100)}/${uid(201)}", step.order.firstLessonPath)
            assertEquals(3, orders.reads)
            vm.clear()
        }

    @Test
    fun retriesReuseTheIdempotencyKeyAndANewCouponGetsANewOne() =
        test {
            val orders = FakeOrdersRepository().apply { createError = networkError }
            val vm = vm(orders)
            runCurrent()
            vm.pay()
            runCurrent()
            assertNotNull(vm.state.value.message, "network error shown, still on review")
            orders.createError = null
            vm.pay()
            runCurrent()
            assertEquals(orders.creates[0].third, orders.creates[1].third, "same key → the server returns the same order")
            assertTrue(orders.creates[0].third.length >= 8)

            vm.startOver()
            vm.onCouponInput("eid50")
            vm.applyCoupon()
            runCurrent()
            val coupon = vm.state.value.coupon
            assertIs<CouponState.Applied>(coupon)
            assertEquals("EID50", coupon.code)
            assertEquals(299, vm.state.value.payable)
            vm.pay()
            runCurrent()
            assertEquals("EID50", orders.creates.last().second)
            assertNotEquals(orders.creates[0].third, orders.creates.last().third)
            vm.clear()
        }

    @Test
    fun freeCourseEnrollsWithoutTheGateway() =
        test {
            val orders = FakeOrdersRepository().apply { created = order(status = OrderStatus.Paid, total = 0) }
            val browser = FakeBrowser()
            val vm = vm(orders, browser)
            runCurrent()
            vm.pay()
            runCurrent()
            assertIs<CheckoutStep.Success>(vm.state.value.step)
            assertTrue(orders.pays.isEmpty())
            assertTrue(browser.opened.isEmpty())
            vm.clear()
        }

    @Test
    fun failedPaymentAndMissingBrowser() =
        test {
            val orders = FakeOrdersRepository().apply { statuses = mutableListOf(OrderStatus.Failed) }
            val vm = vm(orders, FakeBrowser(opens = false))
            runCurrent()
            vm.pay()
            runCurrent()
            assertIs<CheckoutStep.Review>(vm.state.value.step)
            assertNotNull(vm.state.value.message, "no browser → explain")

            val vm2 = vm(orders)
            runCurrent()
            vm2.pay()
            runCurrent()
            assertIs<CheckoutStep.Failed>(vm2.state.value.step)
            vm.clear()
            vm2.clear()
        }

    @Test
    fun invalidCouponShowsTheServerMessage() =
        test {
            val orders = FakeOrdersRepository().apply { coupon = CouponValidateResponse(valid = false, message = "কুপনের মেয়াদ শেষ") }
            val vm = vm(orders)
            runCurrent()
            vm.onCouponInput("AB")
            vm.applyCoupon()
            assertIs<CouponState.Invalid>(vm.state.value.coupon, "too short is checked locally")
            vm.onCouponInput("OLD2025")
            vm.applyCoupon()
            runCurrent()
            val c = vm.state.value.coupon
            assertIs<CouponState.Invalid>(c)
            assertEquals("কুপনের মেয়াদ শেষ", (c.message as com.shikkhasathi.core.i18n.UiText.Raw).text)
            vm.clear()
        }

    @Test
    fun openedFromALinkWithoutSlugPricesFromTheOrder() =
        test {
            val orders = FakeOrdersRepository()
            val vm = vm(orders, slug = null)
            runCurrent()
            assertEquals(1, orders.creates.size)
            assertEquals("Data Structures Midterm Crash", (vm.state.value.summary as LoadState.Ready).data.title)
            vm.pay()
            runCurrent()
            assertEquals(orders.creates[0].third, orders.creates[1].third)
            vm.clear()
        }

    @Test
    fun resultScreenVerifiesWithTheServer() =
        test {
            val orders = FakeOrdersRepository().apply { statuses = mutableListOf(OrderStatus.Pending, OrderStatus.Paid) }
            val vm = CheckoutResultViewModel(uid(500), orders, pollIntervalMs = 2_000, pollTimeoutMs = 10_000)
            runCurrent()
            assertIs<CheckoutResultUiState.Checking>(vm.state.value)
            advanceTimeBy(2_001)
            assertIs<CheckoutResultUiState.Paid>(vm.state.value)

            val stuck = FakeOrdersRepository()
            val vm2 = CheckoutResultViewModel(uid(500), stuck, pollIntervalMs = 2_000, pollTimeoutMs = 4_000)
            advanceTimeBy(10_000)
            assertIs<CheckoutResultUiState.StillPending>(vm2.state.value)

            assertIs<CheckoutResultUiState.Failed>(CheckoutResultViewModel(null, stuck).state.value)
            vm.clear()
            vm2.clear()
        }
}
