package com.shikkhasathi.features.classroom.presentation

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.time.relativeTime
import com.shikkhasathi.core.common.time.toInstantOrNull
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.model.ClassCourse
import com.shikkhasathi.core.model.Material
import com.shikkhasathi.core.model.StreamItem
import com.shikkhasathi.core.model.StreamKind
import com.shikkhasathi.features.classroom.data.ClassroomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

enum class StreamTab(val label: Str) {
    Stream(Str.ClassroomTabStream),
    Assignments(Str.ClassroomTabAssignments),
    Materials(Str.ClassroomTabMaterials),
}

data class StreamRowUi(
    val id: String,
    val kind: StreamKind,
    val kindLabel: Str,
    val title: String,
    val body: String?,
    val author: String?,
    val postedAgo: UiText?,
    val materials: List<Material>,
    val link: String?,
    /** Set for assignment posts: tapping opens the assignment sheet. */
    val assignment: AssignmentRowUi?,
)

/** A material with where it came from ("CSE-2203 Lab 4 — Linked list.pdf"). [key] is stable for lazy lists. */
data class MaterialRowUi(
    val key: String,
    val material: Material,
    val fromTitle: String,
    val postedAgo: UiText?,
)

data class ClassStreamData(
    val course: ClassCourse,
    val stream: List<StreamRowUi>,
    val assignments: List<AssignmentRowUi>,
    val materials: List<MaterialRowUi>,
)

data class ClassStreamUiState(
    val tab: StreamTab = StreamTab.Stream,
    val content: LoadState<ClassStreamData> = LoadState.Loading,
)

/** Per-class screen: Stream / Assignments / Materials tabs over `GET /classroom/courses/:id/stream`. */
class ClassStreamViewModel(
    private val courseId: String,
    private val repository: ClassroomRepository,
    private val clock: Clock = Clock.System,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(ClassStreamUiState())
    val state: StateFlow<ClassStreamUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun selectTab(tab: StreamTab) = mutableState.update { it.copy(tab = tab) }

    fun retry() = load()

    private fun load() {
        mutableState.update { it.copy(content = LoadState.Loading) }
        scope.launch {
            val result = runCatching { repository.stream(courseId) }
            val now = clock.now()
            mutableState.update {
                it.copy(
                    content =
                        result.fold(
                            onSuccess = { r -> LoadState.Ready(buildData(r.course, r.items, now)) },
                            onFailure = { e -> LoadState.Failed(e.toUiTextOrGeneric()) },
                        ),
                )
            }
        }
    }
}

private fun StreamKind.label(): Str =
    when (this) {
        StreamKind.Assignment -> Str.GcKindAssignment
        StreamKind.Material -> Str.GcKindMaterial
        StreamKind.Announcement -> Str.GcKindAnnouncement
    }

internal fun buildData(
    course: ClassCourse,
    items: List<StreamItem>,
    now: Instant,
): ClassStreamData {
    val rows =
        items.map { item ->
            StreamRowUi(
                id = item.id,
                kind = item.kind,
                kindLabel = item.kind.label(),
                title = item.title,
                body = item.body,
                author = item.author,
                postedAgo = item.postedAt.toInstantOrNull()?.let { relativeTime(it, now) },
                materials = item.materials,
                link = item.alternateLink,
                assignment = item.assignment?.toRowUi(now),
            )
        }
    val materials =
        rows.flatMap { row ->
            row.materials.mapIndexed { i, m ->
                MaterialRowUi(key = "${row.id}#$i", material = m, fromTitle = row.title, postedAgo = row.postedAgo)
            }
        }
    return ClassStreamData(
        course = course,
        stream = rows,
        assignments = rows.mapNotNull { it.assignment },
        materials = materials,
    )
}
