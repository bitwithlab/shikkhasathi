package com.shikkhasathi.features.checkout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsButtonVariant
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.features.checkout.presentation.CheckoutResultUiState
import com.shikkhasathi.features.checkout.presentation.CheckoutResultViewModel
import com.shikkhasathi.shared.navigation.CheckoutResultRoute
import com.shikkhasathi.shared.ui.ScreenHeader
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf

/** Landing screen of the gateway's return App Link: verifies the order, then offers the first lesson. */
@Composable
fun CheckoutResultScreen(
    route: CheckoutResultRoute,
    onBack: () -> Unit,
    onStartLearning: (path: String?) -> Unit,
    onMyCourses: () -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel(key = "checkoutResult:${route.orderId}") { koin.get<CheckoutResultViewModel> { parametersOf(route) } }
    val state by vm.state.collectAsStateWithLifecycle()
    val palette = SsTheme.palette
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = Str.CheckoutTitle.t(), onBack = onBack)
        Column(
            Modifier.fillMaxWidth().padding(SsSpace.s4).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(SsSpace.s3),
        ) {
            when (val s = state) {
                CheckoutResultUiState.Checking -> {
                    CircularProgressIndicator(Modifier.size(40.dp), color = palette.primary, strokeWidth = 3.dp)
                    Text(Str.CheckoutVerifying.t(), style = MaterialTheme.typography.titleMedium, color = palette.text)
                }
                CheckoutResultUiState.StillPending -> {
                    Tile(Lucide.Clock, palette.warningSoft, palette.warningFg)
                    Text(Str.CheckoutPendingTitle.t(), style = MaterialTheme.typography.titleMedium, color = palette.text)
                    Text(Str.CheckoutPendingBody.t(), style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                    SsButton(text = Str.CheckoutCheckAgain.t(), onClick = vm::check, variant = SsButtonVariant.Secondary)
                }
                is CheckoutResultUiState.Paid -> {
                    Tile(Lucide.CircleCheck, palette.successSoft, palette.successFg)
                    Text(Str.CheckoutSuccessTitle.t(), style = MaterialTheme.typography.titleLarge, color = palette.text)
                    SsButton(
                        text = Str.CourseStart.t(),
                        onClick = { onStartLearning(s.order.firstLessonPath) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    SsButton(text = Str.NavCourses.t(), onClick = onMyCourses, variant = SsButtonVariant.Ghost)
                }
                is CheckoutResultUiState.Failed -> {
                    Tile(Lucide.TriangleAlert, palette.dangerSoft, palette.dangerFg)
                    Text(Str.CheckoutFailedTitle.t(), style = MaterialTheme.typography.titleLarge, color = palette.text)
                    Text(s.message.t(), style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                    SsButton(text = Str.CommonBack.t(), onClick = onBack, variant = SsButtonVariant.Secondary)
                }
            }
        }
    }
}

@Composable
private fun Tile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
) {
    Box(Modifier.size(56.dp).background(bg, SsShape.card), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(28.dp))
    }
}
