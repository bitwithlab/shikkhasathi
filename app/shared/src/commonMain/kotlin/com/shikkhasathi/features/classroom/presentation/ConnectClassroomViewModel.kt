package com.shikkhasathi.features.classroom.presentation

import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.time.toInstantOrNull
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.asUiText
import com.shikkhasathi.core.model.ClassroomConnectionStatus
import com.shikkhasathi.core.model.ClassroomStatus
import com.shikkhasathi.core.model.DeviceInfo
import com.shikkhasathi.core.network.ApiError
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.presentation.toUiText
import com.shikkhasathi.features.classroom.data.ClassroomRepository
import com.shikkhasathi.features.classroom.data.GoogleAuthorization
import com.shikkhasathi.features.classroom.data.GoogleAuthorizationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/** Why Classroom is not working, from the connection status or an API error code. */
enum class ConnectProblem { None, NeedsReauth, WorkspaceBlocked, Quota }

/** Copy for the connect card / screen. All Bangla-first, friendly (CLAUDE.md §0A "Failure copy"). */
data class ConnectCopy(
    val problem: ConnectProblem,
    val title: Str,
    val body: UiText,
    /** Null for quota: nothing to do but wait, the server backs off and syncs on its own. */
    val cta: Str?,
    /** Workspace blocked: "কী করতে পারি?" → ask IT or use a personal account. */
    val help: UiText? = null,
)

enum class ConnectPhase { Idle, Authorizing, Connecting, Connected }

data class ConnectClassroomUiState(
    val status: ClassroomStatus? = null,
    val copy: ConnectCopy = connectCopy(null, null, null, Instant.DISTANT_PAST),
    val phase: ConnectPhase = ConnectPhase.Idle,
    /** Transient failure of the last attempt (shown under the button). */
    val error: UiText? = null,
    /** "Google Classroom সংযুক্ত হয়েছে — ক্লাসগুলো সিঙ্ক হচ্ছে" once, after success. */
    val message: UiText? = null,
) {
    val busy: Boolean get() = phase == ConnectPhase.Authorizing || phase == ConnectPhase.Connecting
}

/** Pure mapping, unit-tested: connection status + last error code → what the student reads. */
fun connectCopy(
    status: ClassroomStatus?,
    errorCode: String?,
    lastSyncAt: Instant?,
    now: Instant,
): ConnectCopy {
    val problem =
        when {
            errorCode == ApiError.CLASSROOM_REAUTH || status?.status == ClassroomConnectionStatus.NeedsReauth -> ConnectProblem.NeedsReauth
            errorCode == ApiError.CLASSROOM_WORKSPACE_BLOCKED || status?.status == ClassroomConnectionStatus.WorkspaceBlocked ->
                ConnectProblem.WorkspaceBlocked
            errorCode == ApiError.CLASSROOM_QUOTA || status?.errorCode == "quota" -> ConnectProblem.Quota
            else -> ConnectProblem.None
        }
    return when (problem) {
        ConnectProblem.NeedsReauth -> ConnectCopy(problem, Str.GcReauthTitle, Str.GcReauthBody.asUiText(), Str.GcReconnect)
        ConnectProblem.WorkspaceBlocked ->
            ConnectCopy(
                problem,
                Str.GcBlockedTitle,
                Str.GcBlockedBody.asUiText(),
                Str.HomeClassroomConnectCta,
                help = Str.GcBlockedHelp.asUiText(),
            )
        ConnectProblem.Quota -> ConnectCopy(problem, Str.GcQuotaTitle, quotaText(lastSyncAt, now), cta = null)
        ConnectProblem.None ->
            ConnectCopy(problem, Str.HomeClassroomConnectTitle, Str.HomeClassroomConnectBody.asUiText(), Str.HomeClassroomConnectCta)
    }
}

/**
 * Connect / reconnect Google Classroom: platform consent ([GoogleAuthorization]) → server auth code →
 * `POST /classroom/connect`. Read-only scopes; the student can cancel at any point without an error.
 */
class ConnectClassroomViewModel(
    private val repository: ClassroomRepository,
    private val google: GoogleAuthorization,
    private val device: DeviceInfo,
    private val clock: Clock = Clock.System,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(ConnectClassroomUiState())
    val state: StateFlow<ConnectClassroomUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            runCatching { repository.status() }.onSuccess { s -> setStatus(s, errorCode = null) }
        }
    }

    fun connect() {
        if (state.value.busy) return
        mutableState.update { it.copy(phase = ConnectPhase.Authorizing, error = null) }
        scope.launch {
            when (val result = google.authorize()) {
                is GoogleAuthorizationResult.Success -> exchange(result.serverAuthCode)
                GoogleAuthorizationResult.Cancelled -> idle(Str.GcDeniedToast.asUiText())
                GoogleAuthorizationResult.Unavailable -> idle(Str.GcConnectUnavailable.asUiText())
                is GoogleAuthorizationResult.Failed -> idle(Str.GcConnectError.asUiText())
            }
        }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private suspend fun exchange(code: String) {
        mutableState.update { it.copy(phase = ConnectPhase.Connecting) }
        try {
            val status = repository.connect(code, device.platform)
            setStatus(status, errorCode = null)
            mutableState.update { it.copy(phase = ConnectPhase.Connected, message = Str.GcConnectedToast.asUiText()) }
        } catch (e: ApiException) {
            onError(e.error)
        }
    }

    /** Visible for the error-code → copy mapping tests: applies an API failure from connect/sync. */
    internal fun onError(error: ApiError) {
        val current = state.value
        val lastSync = current.status?.lastSyncAt.toInstantOrNull()
        val copy = connectCopy(current.status, error.code, lastSync, clock.now())
        val inline =
            when (copy.problem) {
                ConnectProblem.None -> if (error.code == ApiError.RATE_LIMITED) Str.GcSyncRateLimited.asUiText() else error.toUiText()
                else -> null // the card itself now explains the problem
            }
        mutableState.update { it.copy(copy = copy, phase = ConnectPhase.Idle, error = inline) }
    }

    private fun idle(error: UiText) = mutableState.update { it.copy(phase = ConnectPhase.Idle, error = error) }

    private fun setStatus(
        status: ClassroomStatus,
        errorCode: String?,
    ) {
        val copy = connectCopy(status, errorCode, status.lastSyncAt.toInstantOrNull(), clock.now())
        mutableState.update {
            it.copy(
                status = status,
                copy = copy,
                phase = if (status.connected && copy.problem == ConnectProblem.None) ConnectPhase.Connected else it.phase,
            )
        }
    }
}
