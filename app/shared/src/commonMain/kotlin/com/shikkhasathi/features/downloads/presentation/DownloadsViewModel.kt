package com.shikkhasathi.features.downloads.presentation

import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.time.dhakaDate
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.features.downloads.data.DownloadManager
import com.shikkhasathi.features.downloads.data.DownloadStatus
import com.shikkhasathi.features.downloads.data.OfflineDownload
import com.shikkhasathi.features.downloads.data.OfflineKind
import com.shikkhasathi.features.downloads.data.OfflineValidity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.number

data class DownloadRowUi(
    val lessonId: String,
    val courseId: String,
    val title: String,
    val courseTitle: String,
    val kind: OfflineKind,
    val status: DownloadStatus,
    val validity: OfflineValidity,
    /** 0–100 while downloading, null when the size is unknown. */
    val percent: Int?,
    val sizeBytes: Long,
    /** "মেয়াদ: ১২ নভেম্বর" when access is time-limited. */
    val accessUntil: UiText?,
) {
    val canOpen: Boolean get() = status == DownloadStatus.Completed && validity == OfflineValidity.Valid
}

data class DownloadsUiState(
    val rows: List<DownloadRowUi> = emptyList(),
) {
    val totalBytes: Long get() = rows.filter { it.status == DownloadStatus.Completed }.sumOf { it.sizeBytes }
}

/** ডাউনলোড (drawer): what is stored offline, its validity, and delete. */
class DownloadsViewModel(
    private val manager: DownloadManager,
) : SsViewModel() {
    val state: StateFlow<DownloadsUiState> =
        manager.downloads
            .map { list -> DownloadsUiState(list.sortedByDescending { it.createdAt }.map { it.toRow(manager.validity(it)) }) }
            .stateIn(scope, SharingStarted.Eagerly, DownloadsUiState())

    fun remove(lessonId: String) {
        scope.launch { manager.remove(lessonId) }
    }
}

private fun OfflineDownload.toRow(validity: OfflineValidity): DownloadRowUi =
    DownloadRowUi(
        lessonId = lessonId,
        courseId = courseId,
        title = title,
        courseTitle = courseTitle,
        kind = kind,
        status = status,
        validity = validity,
        percent = totalBytes?.takeIf { it > 0 }?.let { ((bytes * PERCENT) / it).toInt().coerceIn(0, PERCENT) },
        sizeBytes = totalBytes ?: bytes,
        accessUntil = accessUntil?.dhakaDate()?.let { UiText.DayMonth(it.day, it.month.number) },
    )

private const val PERCENT = 100
