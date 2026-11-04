package com.shikkhasathi.features.classroom.presentation

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.time.dueLabel
import com.shikkhasathi.core.common.time.syncLabel
import com.shikkhasathi.core.common.time.toInstantOrNull
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.asUiText
import com.shikkhasathi.core.model.ClassCourse
import com.shikkhasathi.core.model.ClassroomConnectionStatus
import com.shikkhasathi.core.model.ClassroomStatus
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.presentation.toUiText
import com.shikkhasathi.features.classroom.data.ClassroomRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

data class ClassRowUi(
    val id: String,
    val name: String,
    /** "Section A · Room 402". */
    val subtitle: String?,
    val teachers: String?,
    val color: String,
    val pendingCount: Int,
    val unreadCount: Int,
    /** "পরের ডেডলাইন: আগামীকাল রাত ১১:৫৯". */
    val nextDue: UiText?,
    val alternateLink: String?,
)

fun ClassCourse.toRowUi(now: Instant): ClassRowUi =
    ClassRowUi(
        id = id,
        name = name,
        subtitle = listOfNotNull(section, room).takeIf { it.isNotEmpty() }?.joinToString(" · "),
        teachers = teacherNames.takeIf { it.isNotEmpty() }?.joinToString(", "),
        color = color,
        pendingCount = pendingCount,
        unreadCount = unreadCount,
        nextDue = nextDueAt.toInstantOrNull()?.let { dueLabel(it, now) },
        alternateLink = alternateLink,
    )

data class ClassroomUiState(
    val classes: LoadState<List<ClassRowUi>> = LoadState.Loading,
    /** Non-null when Classroom is not connected / needs attention: the screen shows the connect card instead. */
    val connect: ConnectCopy? = null,
    val syncLabel: UiText? = null,
    val refreshing: Boolean = false,
    val message: UiText? = null,
)

/** আমার ক্লাসরুম: the class list (bottom-bar ক্লাসরুম tab). */
class ClassroomViewModel(
    private val repository: ClassroomRepository,
    private val clock: Clock = Clock.System,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(ClassroomUiState())
    val state: StateFlow<ClassroomUiState> = mutableState.asStateFlow()

    private var status: ClassroomStatus? = null

    init {
        load()
    }

    fun retry() = load()

    fun messageShown() = mutableState.update { it.copy(message = null) }

    fun refresh() {
        if (state.value.refreshing) return
        mutableState.update { it.copy(refreshing = true) }
        scope.launch {
            val note =
                try {
                    status = repository.sync()
                    null
                } catch (e: ApiException) {
                    e.error.toClassroomUiText(status?.lastSyncAt.toInstantOrNull(), clock.now())
                }
            reload()
            mutableState.update { it.copy(refreshing = false, message = note) }
        }
    }

    private fun load() {
        mutableState.update { it.copy(classes = LoadState.Loading) }
        scope.launch { reload() }
    }

    private suspend fun reload() {
        try {
            val (s, courses) =
                coroutineScope {
                    val s = async { repository.status() }
                    val c = async { runCatching { repository.courses() } }
                    s.await() to c.await()
                }
            status = s
            val now = clock.now()
            val copy = connectCopy(s, null, s.lastSyncAt.toInstantOrNull(), now)
            val needsCard = !s.connected || copy.problem == ConnectProblem.NeedsReauth || copy.problem == ConnectProblem.WorkspaceBlocked
            mutableState.update {
                it.copy(
                    connect = if (needsCard) copy else null,
                    syncLabel =
                        s.takeIf { s.connected }?.let {
                            syncLabel(
                                it.lastSyncAt.toInstantOrNull(),
                                now,
                                it.status == ClassroomConnectionStatus.Syncing
                            )
                        },
                    classes =
                        courses.fold(
                            onSuccess = { list -> LoadState.Ready(list.map { c -> c.toRowUi(now) }) },
                            onFailure = { e -> if (needsCard) LoadState.Ready(emptyList()) else LoadState.Failed(e.toUiTextOrGeneric()) },
                        ),
                )
            }
        } catch (e: ApiException) {
            mutableState.update { it.copy(classes = LoadState.Failed(e.error.toUiText())) }
        }
    }
}

internal fun Throwable.toUiTextOrGeneric(): UiText = (this as? ApiException)?.error?.toUiText() ?: Str.ErrorGeneric.asUiText()
