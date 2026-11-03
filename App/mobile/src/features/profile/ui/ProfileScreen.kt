package com.shikkhasathi.features.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsButtonVariant
import com.shikkhasathi.core.designsystem.components.SsCard
import com.shikkhasathi.core.designsystem.numeric
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.model.BdPhone
import com.shikkhasathi.core.model.Me
import com.shikkhasathi.features.auth.data.SignOut
import com.shikkhasathi.shared.ui.ScreenHeader
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Profile: identity card (initials avatar, never a stock photo) + sign out (unregisters push, wipes downloads). */
@Composable
fun ProfileScreen(
    user: Me,
    signOut: SignOut = koinInject(),
) {
    val palette = SsTheme.palette
    val scope = rememberCoroutineScope()
    var signingOut by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(title = Str.NavProfile.t())
        Column(Modifier.padding(horizontal = SsSpace.s4), verticalArrangement = Arrangement.spacedBy(SsSpace.s4)) {
            SsCard(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s3), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).background(palette.primarySoft, SsShape.chip), contentAlignment = Alignment.Center) {
                        Text(initials(user.name), style = MaterialTheme.typography.titleMedium, color = palette.primaryFg)
                    }
                    Column {
                        Text(user.name, style = MaterialTheme.typography.titleMedium, color = palette.text)
                        val contact = user.phone?.let(BdPhone::pretty) ?: user.email
                        if (contact != null) Text(contact, style = MaterialTheme.typography.bodyMedium.numeric, color = palette.muted)
                    }
                }
            }
            Text(Str.ProfileEmptyBody.t(), style = MaterialTheme.typography.bodyMedium, color = palette.muted)
            SsButton(
                text = Str.ProfileLogout.t(),
                variant = SsButtonVariant.Secondary,
                leadingIcon = Lucide.LogOut,
                loading = signingOut,
                onClick = {
                    signingOut = true
                    scope.launch { signOut() }
                },
            )
        }
    }
}

private fun initials(name: String): String =
    name
        .trim()
        .split(Regex("\\s+"))
        .take(2)
        .mapNotNull { it.firstOrNull()?.toString() }
        .joinToString("")
        .ifEmpty { "?" }
