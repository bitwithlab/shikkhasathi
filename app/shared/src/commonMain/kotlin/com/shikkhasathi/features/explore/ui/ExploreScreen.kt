package com.shikkhasathi.features.explore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowUpDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.SearchX
import com.composables.icons.lucide.X
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.EmptyState
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.SkeletonBox
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.model.PriceFilter
import com.shikkhasathi.core.model.SearchSort
import com.shikkhasathi.core.model.Suggestion
import com.shikkhasathi.core.model.SuggestionType
import com.shikkhasathi.features.explore.presentation.ExploreUiState
import com.shikkhasathi.features.explore.presentation.ExploreViewModel
import com.shikkhasathi.shared.ui.ScreenHeader
import org.koin.compose.getKoin

private const val PREFETCH = 4
private val EXAM_FILTERS = listOf("midterm", "final", "quiz", "lab", "admission")

/** কোর্স খুঁজুন: search box with autocomplete, filter chips, result list. Tap a course → its page. */
@Composable
fun ExploreScreen(
    onBack: () -> Unit,
    onOpenCourse: (slug: String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val koin = getKoin()
    val vm = rememberSsViewModel { koin.get<ExploreViewModel>() }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.openLink) {
        state.openLink?.let {
            vm.linkOpened()
            onOpenLink(it)
        }
    }
    ExploreContent(
        state = state,
        onBack = onBack,
        onInput = vm::onInputChange,
        onSubmit = vm::submit,
        onSuggestion = vm::selectSuggestion,
        onExamType = vm::setExamType,
        onPrice = vm::setPrice,
        onSort = vm::setSort,
        onClearFilters = vm::clearFilters,
        onOpenCourse = onOpenCourse,
        onLoadMore = vm::loadMore,
        onRetry = vm::retry,
        onMessageShown = vm::messageShown,
    )
}

@Composable
fun ExploreContent(
    state: ExploreUiState,
    onBack: () -> Unit,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onSuggestion: (Suggestion) -> Unit,
    onExamType: (String?) -> Unit,
    onPrice: (PriceFilter?) -> Unit,
    onSort: (SearchSort) -> Unit,
    onClearFilters: () -> Unit,
    onOpenCourse: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val palette = SsTheme.palette
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.t()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }
    val list = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            (
                list.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            ) >= list.layoutInfo.totalItemsCount - PREFETCH
        }
    }
    LaunchedEffect(nearEnd, state.results.size) { if (nearEnd) onLoadMore() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = Str.NavExplore.t(), onBack = onBack)
            SearchBox(state.input, onInput, onSubmit, Modifier.padding(horizontal = SsSpace.s4))
            if (state.suggestions.isNotEmpty()) {
                SuggestionList(state.suggestions, onSuggestion)
            }
            FilterRow(state, onExamType, onPrice, onSort, onClearFilters)
            state.interpreted.firstOrNull()?.let {
                Text(
                    Str.ExploreInterpreted.t("term" to it.term, "meaning" to it.meaning),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                    modifier = Modifier.padding(horizontal = SsSpace.s4, vertical = SsSpace.s1),
                )
            }
            val error = state.error
            when {
                state.loading ->
                    Column(Modifier.padding(SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
                        repeat(4) { SkeletonBox(Modifier.fillMaxWidth().height(132.dp), shape = SsShape.card) }
                    }
                error != null -> ErrorState(message = error.t(), onRetry = onRetry, modifier = Modifier.padding(SsSpace.s4))
                state.isEmpty ->
                    EmptyState(
                        icon = Lucide.SearchX,
                        title = Str.ExploreNoResultsTitle.t(),
                        body = Str.ExploreNoResultsBody.t(),
                        actionLabel = if (state.filters.activeCount > 0) Str.AssignmentsClearFilters.t() else null,
                        onAction = if (state.filters.activeCount > 0) onClearFilters else null,
                        modifier = Modifier.padding(horizontal = SsSpace.s4),
                    )
                else ->
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = SsSpace.s4, end = SsSpace.s4, top = SsSpace.s2, bottom = SsSpace.s8),
                        verticalArrangement = Arrangement.spacedBy(SsSpace.s3),
                    ) {
                        item(key = "count") {
                            Text(
                                Str.ExploreResultCount.t("n" to state.total),
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.muted
                            )
                        }
                        items(state.results, key = { it.id }) { course -> CourseListCard(course, onClick = { onOpenCourse(course.slug) }) }
                        if (state.loadingMore) {
                            item(key = "more") {
                                Box(Modifier.fillMaxWidth().padding(SsSpace.s4), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp), color = palette.primary, strokeWidth = 2.dp)
                                }
                            }
                        }
                        item(key = "disclaimer") {
                            Text(Str.BrandDisclaimerUniversity.t(), style = MaterialTheme.typography.bodySmall, color = palette.muted)
                        }
                    }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(SsSpace.s4))
    }
}

@Composable
private fun SearchBox(
    value: String,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = SsTheme.palette
    OutlinedTextField(
        value = value,
        onValueChange = onInput,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(Str.ExploreSearchPlaceholder.t(), color = palette.muted) },
        leadingIcon = { Icon(Lucide.Search, contentDescription = null, tint = palette.muted) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = {
                    onInput("")
                    onSubmit()
                }) { Icon(Lucide.X, contentDescription = Str.ExploreClear.t(), tint = palette.muted) }
            }
        },
        singleLine = true,
        shape = SsShape.control,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.primary,
                unfocusedBorderColor = palette.borderStrong,
                focusedContainerColor = palette.surface,
                unfocusedContainerColor = palette.surface,
            ),
    )
}

@Composable
private fun SuggestionList(
    items: List<Suggestion>,
    onSelect: (Suggestion) -> Unit,
) {
    val palette = SsTheme.palette
    Column(Modifier.padding(horizontal = SsSpace.s4, vertical = SsSpace.s1)) {
        items.forEach { s ->
            val kind =
                when (s.type) {
                    SuggestionType.Institution -> Str.SuggestInstitution
                    SuggestionType.Subject -> Str.SuggestSubject
                    SuggestionType.Code -> Str.SuggestCode
                    SuggestionType.Teacher -> Str.SuggestTeacher
                    SuggestionType.Course -> Str.SuggestCourse
                }.t()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onSelect(s) }
                    .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(s.label, s.sub, kind).joinToString(", ") }
                    .padding(vertical = SsSpace.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SsSpace.s3),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.label, style = MaterialTheme.typography.bodyMedium, color = palette.text, maxLines = 1)
                    s.sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 1) }
                }
                Text(kind, style = MaterialTheme.typography.labelSmall, color = palette.muted)
            }
        }
        HorizontalDivider(color = palette.border)
    }
}

@Composable
private fun FilterRow(
    state: ExploreUiState,
    onExamType: (String?) -> Unit,
    onPrice: (PriceFilter?) -> Unit,
    onSort: (SearchSort) -> Unit,
    onClear: () -> Unit,
) {
    val palette = SsTheme.palette
    var sortOpen by remember { mutableStateOf(false) }
    val chipColors =
        FilterChipDefaults.filterChipColors(
            selectedContainerColor = palette.primarySoft,
            selectedLabelColor = palette.primaryFg,
            labelColor = palette.text,
        )
    LazyRow(
        contentPadding = PaddingValues(horizontal = SsSpace.s4, vertical = SsSpace.s2),
        horizontalArrangement = Arrangement.spacedBy(SsSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "sort") {
            Box {
                FilterChip(
                    selected = state.filters.sort != SearchSort.Best,
                    onClick = { sortOpen = true },
                    label = { Text(sortLabel(state.filters.sort).t()) },
                    leadingIcon = { Icon(Lucide.ArrowUpDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = SsShape.chip,
                    colors = chipColors,
                )
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    SearchSort.entries.forEach { sort ->
                        DropdownMenuItem(text = { Text(sortLabel(sort).t()) }, onClick = {
                            sortOpen = false
                            onSort(sort)
                        })
                    }
                }
            }
        }
        item(key = "free") {
            FilterChip(
                selected = state.filters.price == PriceFilter.Free,
                onClick = { onPrice(if (state.filters.price == PriceFilter.Free) null else PriceFilter.Free) },
                label = { Text(Str.CourseFree.t()) },
                shape = SsShape.chip,
                colors = chipColors,
            )
        }
        items(EXAM_FILTERS, key = { it }) { type ->
            FilterChip(
                selected = state.filters.examType == type,
                onClick = { onExamType(if (state.filters.examType == type) null else type) },
                label = { Text(Str.fromKey("examType.$type")?.t() ?: type) },
                shape = SsShape.chip,
                colors = chipColors,
            )
        }
        if (state.filters.activeCount > 0) {
            item(key = "clear") {
                FilterChip(
                    selected = false,
                    onClick = onClear,
                    label = { Text(Str.AssignmentsClearFilters.t()) },
                    leadingIcon = { Icon(Lucide.X, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = SsShape.chip,
                    colors = chipColors,
                )
            }
        }
    }
}

private fun sortLabel(sort: SearchSort): Str =
    when (sort) {
        SearchSort.Best -> Str.SortBest
        SearchSort.Newest -> Str.SortNewest
        SearchSort.Rating -> Str.SortRating
        SearchSort.PriceAsc -> Str.SortPriceAsc
        SearchSort.PriceDesc -> Str.SortPriceDesc
        SearchSort.Popular -> Str.SortPopular
    }
