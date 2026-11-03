package com.shikkhasathi.features.learning.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MonitorSmartphone
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Star
import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.SkeletonBox
import com.shikkhasathi.core.designsystem.components.SkeletonCard
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsButtonVariant
import com.shikkhasathi.core.designsystem.components.SsCard
import com.shikkhasathi.core.designsystem.numeric
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.clockText
import com.shikkhasathi.core.model.LessonType
import com.shikkhasathi.core.player.SsMediaPlayer
import com.shikkhasathi.core.player.ui.VideoSurface
import com.shikkhasathi.features.learning.presentation.LessonUi
import com.shikkhasathi.features.learning.presentation.PlayerTab
import com.shikkhasathi.features.learning.presentation.PlayerUiState
import com.shikkhasathi.features.learning.presentation.PlayerViewModel
import com.shikkhasathi.features.learning.presentation.VideoState
import com.shikkhasathi.shared.navigation.PlayerRoute
import kotlinx.coroutines.delay
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf
import kotlin.random.Random

private const val WATERMARK_MOVE_MS = 20_000L
private const val WATERMARK_ALPHA = 0.35f

/** Lesson player: video (or PDF / text) on top, lesson title + complete, tabs লেসন · নোট · প্রশ্নোত্তর below. */
@Composable
fun PlayerScreen(
    route: PlayerRoute,
    onBack: () -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel(key = "player:${route.courseId}") { koin.get<PlayerViewModel> { parametersOf(route) } }
    val state by vm.state.collectAsStateWithLifecycle()
    // Leaving the app pauses and reports the watched time right away (no playback in the background).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onBackground() }
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.t()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.messageShown()
        }
    }
    val resumedText = state.resumedFromSec?.let { Str.PlayerResumedAt.t("time" to clockText(it)) }
    LaunchedEffect(resumedText) {
        if (resumedText != null) {
            snackbar.showSnackbar(resumedText)
            vm.resumedShown()
        }
    }
    val palette = SsTheme.palette
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = SsSpace.s2), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = Str.CommonBack.t(), tint = palette.text) }
                val title = (state.outline as? LoadState.Ready)?.data?.courseTitle.orEmpty()
                Text(title, style = MaterialTheme.typography.titleSmall, color = palette.text, maxLines = 1, modifier = Modifier.weight(1f))
            }
            when (val outline = state.outline) {
                LoadState.Loading ->
                    Column(verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
                        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), shape = SsShape.small)
                        SkeletonCard(Modifier.padding(horizontal = SsSpace.s4))
                    }
                is LoadState.Failed ->
                    ErrorState(
                        message = outline.message.t(),
                        onRetry = vm::retry,
                        modifier = Modifier.padding(SsSpace.s4)
                    )
                is LoadState.Ready -> {
                    LessonArea(state, vm)
                    PlayerTabs(state, vm, Modifier.weight(1f))
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(SsSpace.s4))
    }
}

@Composable
private fun LessonArea(
    state: PlayerUiState,
    vm: PlayerViewModel,
) {
    val uri = LocalUriHandler.current
    when (val lesson = state.lesson) {
        null, LoadState.Loading -> SkeletonBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), shape = SsShape.small)
        is LoadState.Failed -> ErrorState(message = lesson.message.t(), onRetry = vm::retryLesson, modifier = Modifier.padding(SsSpace.s4))
        is LoadState.Ready -> {
            val l = lesson.data
            when (l.type) {
                LessonType.Video ->
                    VideoArea(
                        state,
                        vm.player,
                        vm::togglePlay,
                        vm::seekBy,
                        vm::seekTo,
                        vm::setSpeed,
                        vm::playHere,
                        vm::retryLesson
                    )
                LessonType.Pdf ->
                    DocumentCard(
                        l,
                        downloaded = l.id in state.downloadedLessonIds,
                        onOpen = { l.documentUrl?.let { runCatching { uri.openUri(it) } } },
                        onDownload = vm::downloadDocument,
                    )
                else -> l.body?.let { TextLesson(it) }
            }
            LessonTitleRow(l, state.completing, vm::toggleComplete, vm::previous, vm::next)
        }
    }
}

@Composable
private fun VideoArea(
    state: PlayerUiState,
    player: SsMediaPlayer,
    onTogglePlay: () -> Unit,
    onSeekBy: (Int) -> Unit,
    onSeekTo: (Double) -> Unit,
    onSpeed: (Float) -> Unit,
    onPlayHere: () -> Unit,
    onRetry: () -> Unit,
) {
    val uri = LocalUriHandler.current
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
        when (val video = state.video) {
            VideoState.None, VideoState.Preparing ->
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp), color = Color.White, strokeWidth = 3.dp)
            is VideoState.Ready -> {
                VideoSurface(player, Modifier.fillMaxSize())
                Watermark(video.watermark)
                Controls(state, onTogglePlay, onSeekBy, onSeekTo, onSpeed)
            }
            is VideoState.External ->
                Overlay(
                    Str.PlayerYoutubeBody.t(),
                    Str.PlayerOpenYoutube.t(),
                    Lucide.ExternalLink
                ) { runCatching { uri.openUri(video.url) } }
            VideoState.TakenOver -> {
                VideoSurface(player, Modifier.fillMaxSize())
                Overlay(Str.PlayerTakenOver.t(), Str.PlayerPlayHere.t(), Lucide.MonitorSmartphone, onPlayHere)
            }
            is VideoState.Error -> Overlay(video.message.t(), Str.CommonRetry.t(), Lucide.RotateCw, onRetry)
        }
    }
}

/** Moving watermark (name + phone from the API): jumps every 20 s so it cannot be cropped out of a recording. */
@Composable
private fun Watermark(text: String) {
    if (text.isBlank()) return
    var target by remember { mutableStateOf(0.1f to 0.15f) }
    LaunchedEffect(text) {
        while (true) {
            delay(WATERMARK_MOVE_MS)
            target = (Random.nextFloat() * 0.6f + 0.05f) to (Random.nextFloat() * 0.7f + 0.06f)
        }
    }
    val x by animateFloatAsState(target.first, tween(1_500), label = "wmX")
    val y by animateFloatAsState(target.second, tween(1_500), label = "wmY")
    BoxWithConstraints(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        Text(
            text,
            color = Color.White.copy(alpha = WATERMARK_ALPHA),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.offset(x = maxWidth * x, y = maxHeight * y),
        )
    }
}

@Composable
private fun Controls(
    state: PlayerUiState,
    onTogglePlay: () -> Unit,
    onSeekBy: (Int) -> Unit,
    onSeekTo: (Double) -> Unit,
    onSpeed: (Float) -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var speedOpen by remember { mutableStateOf(false) }
    LaunchedEffect(visible, state.isPlaying) {
        if (visible && state.isPlaying) {
            delay(3_000)
            visible = false
        }
    }
    val alpha by animateFloatAsState(if (visible || !state.isPlaying) 1f else 0f, tween(200), label = "controls")
    Box(
        Modifier
            .fillMaxSize()
            .clickable(onClickLabel = Str.PlayerShowControls.t()) { visible = !visible },
    ) {
        if (alpha > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f * alpha)))
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(SsSpace.s6),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onSeekBy(-10) }
                ) { Icon(Lucide.RotateCcw, contentDescription = Str.PlayerBack10.t(), tint = Color.White) }
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(64.dp).background(SsTheme.palette.primary.copy(alpha = 0.9f), SsShape.chip)
                ) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (state.isPlaying) Lucide.Pause else Lucide.Play,
                            contentDescription = if (state.isPlaying) Str.PlayerPause.t() else Str.PlayerPlay.t(),
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                IconButton(
                    onClick = { onSeekBy(10) }
                ) { Icon(Lucide.RotateCw, contentDescription = Str.PlayerForward10.t(), tint = Color.White) }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = SsSpace.s3)) {
                val duration = state.durationSec.coerceAtLeast(1.0).toFloat()
                val position = dragging ?: state.positionSec.toFloat()
                val timeText = clockText(position.toInt()).t() + " / " + clockText(state.durationSec.toInt()).t()
                Slider(
                    value = position.coerceIn(0f, duration),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { onSeekTo(it.toDouble()) }
                        dragging = null
                    },
                    valueRange = 0f..duration,
                    modifier = Modifier.semantics { stateDescription = timeText },
                    colors =
                        SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = SsTheme.palette.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        timeText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium.numeric,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        TextButton(onClick = { speedOpen = true }) {
                            Text(
                                UiText.Digits(speedLabel(state.speed)).t() + "×",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge.numeric
                            )
                        }
                        DropdownMenu(expanded = speedOpen, onDismissRequest = { speedOpen = false }) {
                            SsMediaPlayer.SPEEDS.forEach { s ->
                                DropdownMenuItem(text = { Text(UiText.Digits(speedLabel(s)).t() + "×") }, onClick = {
                                    speedOpen = false
                                    onSpeed(s)
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun speedLabel(speed: Float): String = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()

@Composable
private fun Overlay(
    message: String,
    action: String,
    icon: ImageVector,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).padding(SsSpace.s5),
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        SsButton(text = action, onClick = onAction, leadingIcon = icon, modifier = Modifier.padding(top = SsSpace.s3))
    }
}

@Composable
private fun DocumentCard(
    lesson: LessonUi,
    downloaded: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
) {
    val palette = SsTheme.palette
    SsCard(Modifier.fillMaxWidth().padding(SsSpace.s4)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
            Box(Modifier.size(44.dp).background(palette.primarySoft, SsShape.control), contentAlignment = Alignment.Center) {
                Icon(Lucide.FileText, contentDescription = null, tint = palette.primaryFg)
            }
            Text(
                lesson.documentTitle ?: lesson.title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.text,
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.padding(top = SsSpace.s3), horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
            SsButton(text = Str.PlayerOpenPdf.t(), onClick = onOpen, enabled = lesson.documentUrl != null)
            SsButton(
                text = if (downloaded) Str.DownloadDone.t() else Str.DownloadKeepOffline.t(),
                onClick = onDownload,
                enabled = !downloaded && lesson.documentUrl != null,
                variant = SsButtonVariant.Secondary,
                leadingIcon = Lucide.Download,
            )
        }
    }
}

@Composable
private fun TextLesson(body: String) {
    SsCard(Modifier.fillMaxWidth().padding(SsSpace.s4)) {
        Text(body, style = MaterialTheme.typography.bodyLarge, color = SsTheme.palette.text)
    }
}

@Composable
private fun LessonTitleRow(
    lesson: LessonUi,
    completing: Boolean,
    onToggleComplete: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val palette = SsTheme.palette
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SsSpace.s4, vertical = SsSpace.s3),
        verticalArrangement = Arrangement.spacedBy(SsSpace.s2)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
            if (lesson.important) {
                Icon(
                    Lucide.Star,
                    contentDescription = Str.LessonImportant.t(),
                    tint = palette.warning,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(lesson.title, style = MaterialTheme.typography.titleMedium, color = palette.text, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
            val doneLabel = if (lesson.completed) Str.PlayerCompleted.t() else Str.PlayerMarkComplete.t()
            SsButton(
                text = doneLabel,
                onClick = onToggleComplete,
                loading = completing,
                leadingIcon = if (lesson.completed) Lucide.CircleCheck else Lucide.Circle,
                variant = if (lesson.completed) SsButtonVariant.Secondary else SsButtonVariant.Primary,
                modifier = Modifier.semantics { contentDescription = doneLabel },
            )
            Box(Modifier.weight(1f))
            IconButton(onClick = onPrevious, enabled = lesson.prevLessonId != null) {
                Icon(
                    Lucide.ChevronLeft,
                    contentDescription = Str.PlayerPrevious.t(),
                    tint =
                        if (lesson.prevLessonId !=
                            null
                        ) {
                            palette.text
                        } else {
                            palette.border
                        }
                )
            }
            IconButton(onClick = onNext, enabled = lesson.nextLessonId != null) {
                Icon(
                    Lucide.ChevronRight,
                    contentDescription = Str.PlayerNext.t(),
                    tint =
                        if (lesson.nextLessonId !=
                            null
                        ) {
                            palette.text
                        } else {
                            palette.border
                        }
                )
            }
        }
    }
}

@Composable
private fun PlayerTabs(
    state: PlayerUiState,
    vm: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val palette = SsTheme.palette
    val lessonId = state.currentLessonId
    // Read at tap time ("add note at 12:34"), without recomposing the panels on every position tick.
    val position by rememberUpdatedState(state.positionSec)
    val isVideo by rememberUpdatedState(state.video is VideoState.Ready)
    val currentTime: () -> Int? = { position.toInt().takeIf { isVideo } }
    Column(modifier.fillMaxWidth()) {
        PrimaryTabRow(selectedTabIndex = state.tab.ordinal, containerColor = palette.bg, contentColor = palette.primaryFg) {
            PlayerTab.entries.forEach { tab ->
                Tab(
                    selected = state.tab == tab,
                    onClick = { vm.selectTab(tab) },
                    text = { Text(tabLabel(tab).t(), style = MaterialTheme.typography.labelLarge) },
                    selectedContentColor = palette.primaryFg,
                    unselectedContentColor = palette.muted,
                )
            }
        }
        val outline = (state.outline as? LoadState.Ready)?.data
        when (state.tab) {
            PlayerTab.Lessons -> if (outline != null) LessonsPanel(outline, lessonId, vm::selectLesson)
            PlayerTab.Notes ->
                if (lessonId != null) {
                    NotesPanel(lessonId, currentPositionSec = currentTime)
                }
            PlayerTab.Qa ->
                if (lessonId != null) {
                    QaPanel(lessonId, currentPositionSec = currentTime)
                }
        }
    }
}

private fun tabLabel(tab: PlayerTab): Str =
    when (tab) {
        PlayerTab.Lessons -> Str.PlayerTabLessons
        PlayerTab.Notes -> Str.PlayerTabNotes
        PlayerTab.Qa -> Str.PlayerTabQa
    }
