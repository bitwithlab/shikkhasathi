package com.shikkhasathi.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shikkhasathi.core.designsystem.LocalAppLocale
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.SkeletonBox
import com.shikkhasathi.core.designsystem.components.SkeletonCard
import com.shikkhasathi.core.i18n.AppLocale
import com.shikkhasathi.features.auth.data.AuthRepository
import com.shikkhasathi.features.auth.data.SessionState
import com.shikkhasathi.features.auth.ui.AuthScreen
import com.shikkhasathi.shared.navigation.ui.MainScaffold
import org.koin.compose.koinInject

/**
 * Root composable hosted by Android `MainActivity` and iOS `MainViewController`.
 * Koin must already be started by the host (`initKoin`).
 */
@Composable
fun App(darkTheme: Boolean? = null) {
    val repository = koinInject<AuthRepository>()
    val session by repository.session.collectAsStateWithLifecycle()
    LaunchedEffect(repository) { repository.restore() }

    val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalAppLocale provides AppLocale.Bn) {
            Box(Modifier.fillMaxSize().background(SsTheme.palette.bg)) {
                when (val s = session) {
                    SessionState.Restoring -> LaunchSkeleton()
                    SessionState.SignedOut -> AuthScreen()
                    is SessionState.SignedIn -> MainScaffold(user = s.user)
                }
            }
        }
    }
    if (darkTheme == null) SsTheme(content = content) else SsTheme(darkTheme = darkTheme, content = content)
}

/** Shown only while secure storage is read at launch — mirrors the home layout so nothing jumps. */
@Composable
private fun LaunchSkeleton() {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(SsSpace.s4),
        verticalArrangement = Arrangement.spacedBy(SsSpace.s4),
    ) {
        SkeletonBox(Modifier.fillMaxWidth(0.6f).height(28.dp))
        SkeletonCard()
        SkeletonCard()
    }
}
