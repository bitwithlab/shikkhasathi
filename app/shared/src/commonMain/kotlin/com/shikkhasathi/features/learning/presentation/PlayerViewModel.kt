package com.shikkhasathi.features.learning.presentation

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.dataOrNull
import com.shikkhasathi.core.common.time.toInstantOrNull
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.asUiText
import com.shikkhasathi.core.model.LessonDetail
import com.shikkhasathi.core.model.LessonType
import com.shikkhasathi.core.model.PlayerLesson
import com.shikkhasathi.core.model.PlayerOutline
import com.shikkhasathi.core.model.ProgressBody
import com.shikkhasathi.core.model.ProgressResponse
import com.shikkhasathi.core.model.VideoKind
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.core.player.MediaPlayerFactory
import com.shikkhasathi.core.player.PlayerSnapshot
import com.shikkhasathi.core.player.SsMediaPlayer
import com.shikkhasathi.core.player.WatchTimeTracker
import com.shikkhasathi.features.auth.presentation.toUiText
import com.shikkhasathi.features.downloads.data.DownloadManager
import com.shikkhasathi.features.downloads.data.DownloadRequest
import com.shikkhasathi.features.downloads.data.OfflineKind
import com.shikkhasathi.features.learning.data.LearningRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PlayerTab { Lessons, Notes, Qa }

/** What the video area shows. */
sealed interface VideoState {
    /** Not a video lesson (PDF / text / quiz). */
    data object None : VideoState

    data object Preparing : VideoState

    data class Ready(val watermark: String) : VideoState

    /** YouTube lessons open in the YouTube app / browser. */
    data class External(val url: String) : VideoState

    /** Another device took the stream (`streamActive: false`): paused, offer "এখানে চালান". */
    data object TakenOver : VideoState

    data class Error(val message: UiText) : VideoState
}

data class LessonRowUi(
    val id: String,
    val title: String,
    val type: LessonType,
    val durationSec: Int,
    val completed: Boolean,
    val locked: Boolean,
    val important: Boolean,
    val preview: Boolean,
)

data class ChapterUi(
    val id: String,
    val title: String,
    val lessons: List<LessonRowUi>,
)

data class SectionUi(
    val id: String,
    val title: String,
    val chapters: List<ChapterUi>,
)

data class OutlineUi(
    val courseId: String,
    val courseSlug: String,
    val courseTitle: String,
    val courseCode: String?,
    val sellerName: String,
    val enrolled: Boolean,
    val progressPercent: Int,
    val completedLessons: Int,
    val totalLessons: Int,
    val sections: List<SectionUi>,
)

data class LessonUi(
    val id: String,
    val title: String,
    val type: LessonType,
    val important: Boolean,
    val body: String?,
    val documentId: String?,
    val documentUrl: String?,
    val documentTitle: String?,
    val completed: Boolean,
    val prevLessonId: String?,
    val nextLessonId: String?,
)

data class PlayerUiState(
    val outline: LoadState<OutlineUi> = LoadState.Loading,
    /** Null until a lesson is chosen (outline still loading). */
    val lesson: LoadState<LessonUi>? = null,
    val video: VideoState = VideoState.None,
    val positionSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val ended: Boolean = false,
    val speed: Float = 1f,
    /** One-shot "আগের জায়গা থেকে চালু হলো" toast. */
    val resumedFromSec: Int? = null,
    val tab: PlayerTab = PlayerTab.Lessons,
    val completing: Boolean = false,
    /** Lessons stored offline (download button shows "ডাউনলোড হয়েছে"). */
    val downloadedLessonIds: Set<String> = emptySet(),
    val message: UiText? = null,
) {
    val currentLessonId: String? get() = lesson?.dataOrNull?.id
}

/**
 * The lesson player (spec §7, ADR-022):
 * - outline `GET /learn/:courseId` → lesson `GET /lessons/:id` → for videos `POST /lessons/:id/playback`
 *   with the install's stable [deviceId] (3-device limit);
 * - heartbeat `POST /lessons/:id/progress` every 15 s while playing and on pause / end / leaving, sending only
 *   **real playback** ([WatchTimeTracker]); the server auto-completes at 90 %;
 * - `streamActive: false` → pause and show "এখানে চালান" (another device took over); tapping it asks for a new stream;
 * - resume from `startAtSec`, speed menu, ±10 s seek, mark complete / not complete.
 */
@Suppress("TooManyFunctions", "LongParameterList") // one intent per player control; collaborators injected for JVM tests
class PlayerViewModel(
    private val courseId: String,
    initialLessonId: String?,
    private val repository: LearningRepository,
    playerFactory: MediaPlayerFactory,
    private val deviceId: String,
    private val deviceName: String?,
    /** Outlives the ViewModel: the final heartbeat is sent from here after the screen closes. */
    private val backgroundScope: CoroutineScope,
    initialTab: PlayerTab = PlayerTab.Lessons,
    private val downloads: DownloadManager? = null,
    private val heartbeatMs: Long = HEARTBEAT_MS,
) : SsViewModel() {
    val player: SsMediaPlayer = playerFactory.create()

    private val mutableState = MutableStateFlow(PlayerUiState(tab = initialTab))
    val state: StateFlow<PlayerUiState> = mutableState.asStateFlow()

    private val tracker = WatchTimeTracker()
    private var outline: PlayerOutline? = null
    private var streamToken: String? = null
    private var activeLessonId: String? = null
    private var lastReportedPosition = -1
    private var heartbeatJob: Job? = null
    private var lessonJob: Job? = null
    private var wasPlaying = false

    init {
        scope.launch { player.snapshot.collect(::onSnapshot) }
        downloads?.let { d ->
            scope.launch {
                d.downloads.collect { list ->
                    mutableState.update { it.copy(downloadedLessonIds = list.mapTo(HashSet()) { x -> x.lessonId }) }
                }
            }
        }
        loadOutline(initialLessonId)
    }

    // ------------------------------------------------------------------ intents

    fun retry() = loadOutline(state.value.currentLessonId)

    fun retryLesson() {
        activeLessonId?.let { openLesson(it) }
    }

    fun selectTab(tab: PlayerTab) = mutableState.update { it.copy(tab = tab) }

    fun selectLesson(lessonId: String) {
        if (lessonId == activeLessonId) return
        val row = outline?.lessons?.firstOrNull { it.id == lessonId }
        if (row?.locked == true) {
            mutableState.update { it.copy(message = Str.PlayerLocked.asUiText()) }
            return
        }
        openLesson(lessonId)
    }

    fun next() =
        state.value.lesson
            ?.dataOrNull
            ?.nextLessonId
            ?.let(::selectLesson)

    fun previous() =
        state.value.lesson
            ?.dataOrNull
            ?.prevLessonId
            ?.let(::selectLesson)

    fun togglePlay() {
        if (state.value.video !is VideoState.Ready) return
        if (player.snapshot.value.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaSec: Int) = seekTo(player.snapshot.value.positionSec + deltaSec)

    fun seekTo(positionSec: Double) {
        val target =
            positionSec.coerceIn(
                0.0,
                player.snapshot.value.durationSec
                    .takeIf { it > 0 } ?: Double.MAX_VALUE
            )
        tracker.onSeek(target)
        player.seekTo(target)
    }

    fun setSpeed(speed: Float) {
        player.setSpeed(speed)
        mutableState.update { it.copy(speed = speed) }
    }

    /** "এখানে চালান" after a takeover: a new playback grant moves the stream back to this device. */
    fun playHere() {
        val lesson = state.value.lesson?.dataOrNull ?: return
        startPlayback(
            lesson.id,
            resumeAt =
                player.snapshot.value.positionSec
                    .toInt(),
            autoplay = true
        )
    }

    fun toggleComplete() {
        val lesson = state.value.lesson?.dataOrNull ?: return
        if (state.value.completing) return
        val target = !lesson.completed
        mutableState.update { it.copy(completing = true) }
        scope.launch {
            try {
                applyProgress(lesson.id, repository.complete(lesson.id, target))
            } catch (e: ApiException) {
                mutableState.update { it.copy(message = e.error.toUiText()) }
            } finally {
                mutableState.update { it.copy(completing = false) }
            }
        }
    }

    /** "অফলাইনে রাখুন" on a PDF lesson: encrypted download tied to this account, device and the access period. */
    fun downloadDocument() {
        val lesson = state.value.lesson?.dataOrNull ?: return
        val url = lesson.documentUrl ?: return
        val o = outline ?: return
        val request =
            DownloadRequest(
                lessonId = lesson.id,
                courseId = o.course.id,
                title = lesson.documentTitle ?: lesson.title,
                courseTitle = o.course.title,
                kind = OfflineKind.Document,
                url = url,
                accessUntil = o.accessUntil.toInstantOrNull(),
            )
        val started = downloads?.enqueue(request) == true
        mutableState.update { it.copy(message = (if (started) Str.DownloadStarted else Str.DownloadUnavailable).asUiText()) }
    }

    fun resumedShown() = mutableState.update { it.copy(resumedFromSec = null) }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    /** App went to the background: pause and report what was watched. */
    fun onBackground() {
        player.pause()
        flush(background = false)
    }

    override fun clear() {
        player.pause()
        flush(background = true)
        heartbeatJob?.cancel()
        super.clear()
        player.release()
    }

    // ------------------------------------------------------------------ loading

    private fun loadOutline(preferredLessonId: String?) {
        mutableState.update { it.copy(outline = LoadState.Loading) }
        scope.launch {
            try {
                val o = repository.outline(courseId)
                outline = o
                downloads?.verified(o.course.id, enrolled = o.enrolled || o.isOwner, accessUntil = o.accessUntil.toInstantOrNull())
                mutableState.update { it.copy(outline = LoadState.Ready(o.toUi())) }
                val start = pickStartLesson(o, preferredLessonId)
                if (start == null) {
                    mutableState.update { it.copy(lesson = LoadState.Failed(Str.PlayerNoLessons.asUiText())) }
                } else {
                    openLesson(start)
                }
            } catch (e: ApiException) {
                mutableState.update { it.copy(outline = LoadState.Failed(e.error.toUiText())) }
            }
        }
    }

    private fun openLesson(lessonId: String) {
        flush(background = false)
        player.pause()
        lessonJob?.cancel()
        activeLessonId = lessonId
        streamToken = null
        lastReportedPosition = -1
        mutableState.update {
            it.copy(
                lesson = LoadState.Loading,
                video = VideoState.None,
                ended = false,
                positionSec = 0.0,
                durationSec = 0.0
            )
        }
        lessonJob =
            scope.launch {
                try {
                    val detail = repository.lesson(lessonId)
                    mutableState.update { it.copy(lesson = LoadState.Ready(detail.toUi())) }
                    if (detail.type == LessonType.Video) startPlayback(lessonId, resumeAt = null, autoplay = false)
                } catch (e: ApiException) {
                    mutableState.update { it.copy(lesson = LoadState.Failed(e.error.toUiText())) }
                }
            }
    }

    private fun startPlayback(
        lessonId: String,
        resumeAt: Int?,
        autoplay: Boolean,
    ) {
        mutableState.update { it.copy(video = VideoState.Preparing) }
        scope.launch {
            try {
                val grant = repository.playback(lessonId, deviceId, deviceName)
                if (activeLessonId != lessonId) return@launch
                streamToken = grant.streamToken
                if (grant.kind == VideoKind.Youtube) {
                    mutableState.update { it.copy(video = VideoState.External(grant.url)) }
                    return@launch
                }
                val startAt = resumeAt ?: grant.startAtSec
                player.load(grant.url, startAt)
                player.setSpeed(state.value.speed)
                tracker.onSeek(startAt.toDouble())
                mutableState.update {
                    it.copy(
                        video = VideoState.Ready(grant.watermark),
                        resumedFromSec = if (resumeAt == null && startAt > RESUME_TOAST_MIN_SEC) startAt else null,
                    )
                }
                if (autoplay) player.play()
            } catch (e: ApiException) {
                mutableState.update { it.copy(video = VideoState.Error(e.error.toUiText())) }
            }
        }
    }

    // ------------------------------------------------------------------ playback + heartbeat

    private fun onSnapshot(s: PlayerSnapshot) {
        tracker.onSample(s)
        mutableState.update {
            it.copy(
                positionSec = s.positionSec,
                durationSec = s.durationSec,
                isPlaying = s.isPlaying,
                isBuffering = s.isBuffering,
                ended = s.ended,
                video =
                    if (s.error != null &&
                        it.video is VideoState.Ready
                    ) {
                        VideoState.Error(Str.PlayerVideoError.asUiText())
                    } else {
                        it.video
                    },
            )
        }
        if (s.isPlaying && !wasPlaying) startHeartbeat()
        if (!s.isPlaying && wasPlaying) {
            heartbeatJob?.cancel()
            flush(background = false)
        }
        wasPlaying = s.isPlaying
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob =
            scope.launch {
                while (isActive) {
                    delay(heartbeatMs)
                    flush(background = false)
                }
            }
    }

    /** Sends the watched delta + position. Offline blips are ignored (playback never stops for them). */
    private fun flush(background: Boolean) {
        val lessonId = activeLessonId ?: return
        val token = streamToken ?: return
        val position =
            player.snapshot.value.positionSec
                .toInt()
        val delta = tracker.drain()
        if (delta == 0 && position == lastReportedPosition) return
        lastReportedPosition = position
        val body = ProgressBody(positionSec = position.coerceIn(0, MAX_POSITION_SEC), deltaSec = delta, streamToken = token)
        (if (background) backgroundScope else scope).launch {
            val response = runCatching { repository.progress(lessonId, body) }.getOrNull() ?: return@launch
            if (background) return@launch
            if (!response.streamActive && activeLessonId == lessonId) {
                player.pause()
                heartbeatJob?.cancel()
                mutableState.update { it.copy(video = VideoState.TakenOver) }
            }
            applyProgress(lessonId, response)
        }
    }

    private fun applyProgress(
        lessonId: String,
        response: ProgressResponse,
    ) {
        val o = outline ?: return
        val updated = o.withLessonCompleted(lessonId, response.completed, response.progressPercent)
        outline = updated
        mutableState.update { s ->
            val lesson = s.lesson?.dataOrNull
            s.copy(
                outline = LoadState.Ready(updated.toUi()),
                lesson = if (lesson?.id == lessonId) LoadState.Ready(lesson.copy(completed = response.completed)) else s.lesson,
            )
        }
    }

    companion object {
        const val HEARTBEAT_MS = 15_000L
        private const val RESUME_TOAST_MIN_SEC = 5
        private const val MAX_POSITION_SEC = 86_400
    }
}

/** The requested lesson if it can be opened, else resume → first unlocked lesson. */
internal fun pickStartLesson(
    outline: PlayerOutline,
    preferred: String?,
): String? {
    val lessons = outline.lessons
    val open = lessons.filter { !it.locked }
    return preferred?.takeIf { id -> open.any { it.id == id } }
        ?: outline.resumeLessonId?.takeIf { id -> open.any { it.id == id } }
        ?: open.firstOrNull { !it.completed }?.id
        ?: open.firstOrNull()?.id
}

internal fun PlayerOutline.withLessonCompleted(
    lessonId: String,
    completed: Boolean,
    progressPercent: Int,
): PlayerOutline {
    fun PlayerLesson.mark() = if (id == lessonId) copy(completed = completed) else this
    val sections = sections.map { s -> s.copy(chapters = s.chapters.map { c -> c.copy(lessons = c.lessons.map { it.mark() }) }) }
    val done = sections.sumOf { s -> s.chapters.sumOf { c -> c.lessons.count { it.completed } } }
    return copy(sections = sections, progressPercent = progressPercent, completedLessons = done)
}

internal fun PlayerOutline.toUi(): OutlineUi =
    OutlineUi(
        courseId = course.id,
        courseSlug = course.slug,
        courseTitle = course.title,
        courseCode = course.courseCode,
        sellerName = course.sellerName,
        enrolled = enrolled || isOwner,
        progressPercent = progressPercent.coerceIn(0, MAX_PERCENT),
        completedLessons = completedLessons,
        totalLessons = totalLessons,
        sections =
            sections.map { s ->
                SectionUi(
                    id = s.id,
                    title = s.title,
                    chapters =
                        s.chapters.map { c ->
                            ChapterUi(
                                id = c.id,
                                title = c.title,
                                lessons =
                                    c.lessons.map { l ->
                                        LessonRowUi(l.id, l.title, l.type, l.durationSec, l.completed, l.locked, l.isImportant, l.isPreview)
                                    },
                            )
                        },
                )
            },
    )

internal fun LessonDetail.toUi(): LessonUi =
    LessonUi(
        id = id,
        title = title,
        type = type,
        important = isImportant,
        body = body,
        documentId = document?.id,
        documentUrl = document?.url,
        documentTitle = document?.title,
        completed = completed,
        prevLessonId = prevLessonId,
        nextLessonId = nextLessonId,
    )

private const val MAX_PERCENT = 100
