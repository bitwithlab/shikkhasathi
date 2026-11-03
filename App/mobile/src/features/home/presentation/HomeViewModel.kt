package com.shikkhasathi.features.home.presentation

import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.model.Me
import com.shikkhasathi.features.auth.data.AuthRepository
import com.shikkhasathi.features.auth.data.SessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Phase 1 home: header + Classroom connect card. Dashboard widgets (§0A) plug in here as they are built. */
data class HomeUiState(
    /** First name for the greeting, or null to use the generic "শিক্ষার্থী". */
    val firstName: String? = null,
    /** "Metropolitan University · CSE · Semester 5" style academic chip; null until onboarding is done. */
    val academicLine: String? = null,
    val classroomConnected: Boolean = false,
    val unreadNotifications: Int = 0,
)

class HomeViewModel(repository: AuthRepository) : SsViewModel() {
    val state: StateFlow<HomeUiState> =
        repository.session
            .map { session -> (session as? SessionState.SignedIn)?.user?.toHomeState() ?: HomeUiState() }
            .stateIn(scope, SharingStarted.Eagerly, HomeUiState())
}

internal fun Me.toHomeState(): HomeUiState =
    HomeUiState(
        firstName =
            name
                .trim()
                .split(Regex("\\s+"))
                .firstOrNull()
                ?.takeIf { it.isNotEmpty() },
        academicLine =
            listOfNotNull(
                academic.institutionShort ?: academic.institutionName,
                academic.departmentShort ?: academic.departmentName,
                academic.semester?.let { "Semester $it" },
            ).takeIf { it.isNotEmpty() }?.joinToString(" · "),
    )
