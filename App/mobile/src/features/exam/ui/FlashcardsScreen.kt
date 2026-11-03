package com.shikkhasathi.features.exam.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.EmptyState
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.SkeletonBox
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsButtonVariant
import com.shikkhasathi.core.designsystem.components.SsCard
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.features.exam.presentation.FlashcardsViewModel
import com.shikkhasathi.shared.ui.ScreenHeader
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf

/** Flashcards: tap to flip (respects reduced motion via a short fade-like flip), "জানি" / "এখনো না". */
@Composable
fun FlashcardsScreen(
    planId: String,
    onBack: () -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel(key = "cards:$planId") { koin.get<FlashcardsViewModel> { parametersOf(planId) } }
    val state by vm.state.collectAsStateWithLifecycle()
    val palette = SsTheme.palette
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = Str.ExamFlashcards.t(), onBack = onBack)
        when (val deck = state.deck) {
            LoadState.Loading -> SkeletonBox(Modifier.padding(SsSpace.s4).fillMaxWidth().aspectRatio(1.4f), shape = SsShape.card)
            is LoadState.Failed -> ErrorState(message = deck.message.t(), onRetry = vm::retry, modifier = Modifier.padding(SsSpace.s4))
            is LoadState.Ready -> {
                val card = state.current
                when {
                    deck.data.isEmpty() ->
                        EmptyState(
                            Lucide.Layers,
                            Str.FlashcardsEmptyTitle.t(),
                            Str.FlashcardsEmptyBody.t(),
                            Modifier.padding(horizontal = SsSpace.s4)
                        )
                    state.finished || card == null ->
                        Column(Modifier.padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
                            Text(Str.FlashcardsDone.t(), style = MaterialTheme.typography.titleLarge, color = palette.text)
                            Text(
                                Str.FlashcardsSummary.t("known" to state.knownCount, "notYet" to state.notYetCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.muted,
                            )
                            SsButton(text = Str.FlashcardsAgain.t(), onClick = vm::restart)
                            SsButton(text = Str.CommonBack.t(), onClick = onBack, variant = SsButtonVariant.Ghost)
                        }
                    else -> {
                        val total = deck.data.size
                        val position = Str.FlashcardsPosition.t("n" to state.index + 1, "total" to total)
                        Column(Modifier.padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s4)) {
                            Text(position, style = MaterialTheme.typography.labelMedium, color = palette.muted)
                            LinearProgressIndicator(
                                progress = { state.index.toFloat() / total },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(SsShape.chip),
                                color = palette.primary,
                                trackColor = palette.surface2,
                                drawStopIndicator = {},
                            )
                            val rotation by animateFloatAsState(if (state.flipped) 180f else 0f, tween(220), label = "flip")
                            val sideLabel = if (state.flipped) Str.FlashcardsBack.t() else Str.FlashcardsFront.t()
                            SsCard(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.4f)
                                    .graphicsLayer {
                                        rotationY = rotation
                                        cameraDistance = 12f * density
                                    }.clickable(role = Role.Button, onClickLabel = Str.FlashcardsFlip.t(), onClick = vm::flip)
                                    .semantics {
                                        stateDescription = sideLabel
                                        contentDescription = if (state.flipped) card.back else card.front
                                    },
                            ) {
                                Box(
                                    Modifier.fillMaxSize().graphicsLayer {
                                        rotationY =
                                            if (rotation >
                                                90f
                                            ) {
                                                180f
                                            } else {
                                                0f
                                            }
                                    },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (rotation > 90f) card.back else card.front,
                                        style =
                                            if (rotation >
                                                90f
                                            ) {
                                                MaterialTheme.typography.bodyLarge
                                            } else {
                                                MaterialTheme.typography.titleLarge
                                            },
                                        color = palette.text,
                                    )
                                }
                            }
                            Text(Str.FlashcardsTapHint.t(), style = MaterialTheme.typography.bodySmall, color = palette.muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
                                SsButton(
                                    text = Str.FlashcardsNotYet.t(),
                                    onClick = { vm.answer(false) },
                                    variant = SsButtonVariant.Secondary,
                                    modifier = Modifier.weight(1f),
                                )
                                SsButton(text = Str.FlashcardsKnown.t(), onClick = { vm.answer(true) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
