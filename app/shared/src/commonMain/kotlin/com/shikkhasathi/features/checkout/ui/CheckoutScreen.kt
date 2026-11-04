package com.shikkhasathi.features.checkout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.TriangleAlert
import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.SkeletonCard
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsButtonVariant
import com.shikkhasathi.core.designsystem.components.SsCard
import com.shikkhasathi.core.designsystem.hexColor
import com.shikkhasathi.core.designsystem.numeric
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.takaText
import com.shikkhasathi.core.model.Order
import com.shikkhasathi.core.model.PaymentMethod
import com.shikkhasathi.features.checkout.presentation.CheckoutStep
import com.shikkhasathi.features.checkout.presentation.CheckoutSummary
import com.shikkhasathi.features.checkout.presentation.CheckoutUiState
import com.shikkhasathi.features.checkout.presentation.CheckoutViewModel
import com.shikkhasathi.features.checkout.presentation.CouponState
import com.shikkhasathi.shared.navigation.CheckoutRoute
import com.shikkhasathi.shared.ui.ScreenHeader
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf

/** Checkout: order summary, coupon, payment method, one "পেমেন্ট করুন" CTA; waits for the gateway, then success. */
@Composable
fun CheckoutScreen(
    route: CheckoutRoute,
    onBack: () -> Unit,
    onStartLearning: (path: String?) -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel(key = "checkout:${route.courseId}") { koin.get<CheckoutViewModel> { parametersOf(route) } }
    val state by vm.state.collectAsStateWithLifecycle()
    // Back from the Custom Tab: check the order immediately instead of waiting for the next poll.
    LifecycleResumeEffect(vm) {
        vm.onResume()
        onPauseOrDispose { }
    }
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.t()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.messageShown()
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = Str.CheckoutTitle.t(), onBack = onBack)
            when (val step = state.step) {
                CheckoutStep.Review -> ReviewStep(state, vm)
                is CheckoutStep.AwaitingPayment -> WaitingStep(onCheckAgain = vm::onResume, onCancel = vm::startOver)
                is CheckoutStep.Success -> SuccessStep(step.order, onStart = { onStartLearning(step.order.firstLessonPath) })
                is CheckoutStep.Failed -> FailedStep(step.message.t(), onRetry = vm::startOver)
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(SsSpace.s4))
    }
}

@Composable
private fun ReviewStep(
    state: CheckoutUiState,
    vm: CheckoutViewModel,
) {
    val palette = SsTheme.palette
    when (val summary = state.summary) {
        LoadState.Loading ->
            Column(Modifier.padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
                repeat(3) { SkeletonCard() }
            }
        is LoadState.Failed -> ErrorState(message = summary.message.t(), onRetry = vm::retry, modifier = Modifier.padding(SsSpace.s4))
        is LoadState.Ready -> {
            val free = (state.payable ?: summary.data.total) == 0
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SsSpace.s4),
                verticalArrangement = Arrangement.spacedBy(SsSpace.s4),
            ) {
                SummaryCard(summary.data, state)
                if (!summary.data.free) CouponCard(state, vm::onCouponInput, vm::applyCoupon, vm::removeCoupon)
                if (!free) MethodPicker(state.method, vm::selectMethod)
                SsButton(
                    text = if (free) Str.CourseEnrollFree.t() else Str.CheckoutPay.t("amount" to takaText(state.payable ?: 0)),
                    onClick = vm::pay,
                    loading = state.submitting,
                    leadingIcon = Lucide.Lock,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(Str.CheckoutSecureNote.t(), style = MaterialTheme.typography.bodySmall, color = palette.muted)
                Text(
                    Str.CheckoutRefundNote.t(),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                    modifier = Modifier.padding(bottom = SsSpace.s6)
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    summary: CheckoutSummary,
    state: CheckoutUiState,
) {
    val palette = SsTheme.palette
    val applied = state.coupon as? CouponState.Applied
    SsCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s3), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 6.dp, height = 44.dp).background(hexColor(summary.coverColor, palette.primary), SsShape.chip))
            Column(Modifier.weight(1f)) {
                Text(summary.title, style = MaterialTheme.typography.titleSmall, color = palette.text, maxLines = 2)
                listOfNotNull(summary.courseCode, summary.sellerName).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = palette.muted)
                }
            }
        }
        Column(Modifier.padding(top = SsSpace.s3), verticalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
            AmountRow(Str.CheckoutSubtotal.t(), takaText(summary.subtotal).t())
            val discount = applied?.discount ?: summary.discount
            if (discount > 0) AmountRow(Str.CheckoutDiscount.t(), "−" + takaText(discount).t(), color = palette.successFg)
            AmountRow(Str.CheckoutTotal.t(), takaText(state.payable ?: summary.total).t(), strong = true)
        }
    }
}

@Composable
private fun AmountRow(
    label: String,
    value: String,
    strong: Boolean = false,
    color: androidx.compose.ui.graphics.Color = SsTheme.palette.text,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = SsTheme.palette.text
        )
        Text(
            value,
            style = (if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium).numeric,
            color = color
        )
    }
}

@Composable
private fun CouponCard(
    state: CheckoutUiState,
    onInput: (String) -> Unit,
    onApply: () -> Unit,
    onRemove: () -> Unit,
) {
    val palette = SsTheme.palette
    val coupon = state.coupon
    SsCard(Modifier.fillMaxWidth()) {
        if (coupon is CouponState.Applied) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                Icon(Lucide.CircleCheck, contentDescription = null, tint = palette.success, modifier = Modifier.size(20.dp))
                Text(
                    Str.CheckoutCouponApplied.t("code" to coupon.code, "amount" to takaText(coupon.discount)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.text,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRemove) { Text(Str.CheckoutCouponRemove.t(), color = palette.primaryFg) }
            }
        } else {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
                OutlinedTextField(
                    value = state.couponInput,
                    onValueChange = onInput,
                    modifier = Modifier.weight(1f),
                    label = { Text(Str.CheckoutCouponLabel.t()) },
                    singleLine = true,
                    isError = coupon is CouponState.Invalid,
                    supportingText = (coupon as? CouponState.Invalid)?.let { { Text(it.message.t()) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    shape = SsShape.control,
                )
                SsButton(
                    text = Str.CheckoutCouponApply.t(),
                    onClick = onApply,
                    variant = SsButtonVariant.Secondary,
                    loading = coupon is CouponState.Checking,
                    enabled = state.couponInput.isNotBlank(),
                    modifier = Modifier.padding(top = SsSpace.s2),
                )
            }
        }
    }
}

@Composable
private fun MethodPicker(
    selected: PaymentMethod,
    onSelect: (PaymentMethod) -> Unit,
) {
    val palette = SsTheme.palette
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
        Text(Str.CheckoutMethod.t(), style = MaterialTheme.typography.titleSmall, color = palette.text)
        PaymentMethod.entries.forEach { method ->
            val isSelected = method == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) palette.primary else palette.border, SsShape.control)
                    .background(if (isSelected) palette.primarySoft else palette.surface, SsShape.control)
                    .selectable(selected = isSelected, onClick = { onSelect(method) }, role = Role.RadioButton)
                    .padding(horizontal = SsSpace.s3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SsSpace.s3),
            ) {
                RadioButton(selected = isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = palette.primary))
                Icon(
                    if (method ==
                        PaymentMethod.Card
                    ) {
                        Lucide.CreditCard
                    } else {
                        Lucide.Smartphone
                    },
                    contentDescription = null,
                    tint = palette.muted
                )
                Text(methodLabel(method).t(), style = MaterialTheme.typography.bodyLarge, color = palette.text)
            }
        }
    }
}

private fun methodLabel(method: PaymentMethod): Str =
    when (method) {
        PaymentMethod.Bkash -> Str.PayBkash
        PaymentMethod.Nagad -> Str.PayNagad
        PaymentMethod.Rocket -> Str.PayRocket
        PaymentMethod.Card -> Str.PayCard
    }

@Composable
private fun WaitingStep(
    onCheckAgain: () -> Unit,
    onCancel: () -> Unit,
) {
    val palette = SsTheme.palette
    Column(
        Modifier.fillMaxWidth().padding(SsSpace.s4).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(SsSpace.s3),
    ) {
        CircularProgressIndicator(Modifier.size(40.dp), color = palette.primary, strokeWidth = 3.dp)
        Text(Str.CheckoutWaitingTitle.t(), style = MaterialTheme.typography.titleMedium, color = palette.text)
        Text(Str.CheckoutWaitingBody.t(), style = MaterialTheme.typography.bodyMedium, color = palette.muted)
        SsButton(text = Str.CheckoutCheckAgain.t(), onClick = onCheckAgain, variant = SsButtonVariant.Secondary)
        TextButton(onClick = onCancel) { Text(Str.CheckoutChangeMethod.t(), color = palette.primaryFg) }
    }
}

@Composable
private fun SuccessStep(
    order: Order,
    onStart: () -> Unit,
) {
    val palette = SsTheme.palette
    Column(
        Modifier.fillMaxWidth().padding(SsSpace.s4).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(SsSpace.s3),
    ) {
        Box(Modifier.size(56.dp).background(palette.successSoft, SsShape.card), contentAlignment = Alignment.Center) {
            Icon(Lucide.CircleCheck, contentDescription = null, tint = palette.successFg, modifier = Modifier.size(28.dp))
        }
        Text(Str.CheckoutSuccessTitle.t(), style = MaterialTheme.typography.titleLarge, color = palette.text)
        Text(
            Str.CheckoutSuccessBody.t("title" to order.items.joinToString(" + ") { it.title }),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
        )
        order.invoiceNumber?.let {
            Text(
                Str.CheckoutInvoice.t("number" to it),
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
        }
        SsButton(text = Str.CourseStart.t(), onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp))
    }
}

@Composable
private fun FailedStep(
    message: String,
    onRetry: () -> Unit,
) {
    val palette = SsTheme.palette
    Column(Modifier.fillMaxWidth().padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
        Box(Modifier.size(56.dp).background(palette.dangerSoft, SsShape.card), contentAlignment = Alignment.Center) {
            Icon(Lucide.TriangleAlert, contentDescription = null, tint = palette.dangerFg, modifier = Modifier.size(28.dp))
        }
        Text(Str.CheckoutFailedTitle.t(), style = MaterialTheme.typography.titleLarge, color = palette.text)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
        SsButton(text = Str.CommonRetry.t(), onClick = onRetry)
    }
}
