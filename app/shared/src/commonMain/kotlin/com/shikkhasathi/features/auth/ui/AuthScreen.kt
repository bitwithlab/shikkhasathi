package com.shikkhasathi.features.auth.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.Smartphone
import com.shikkhasathi.core.common.ui.rememberSsViewModel
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.ErrorState
import com.shikkhasathi.core.designsystem.components.OtpField
import com.shikkhasathi.core.designsystem.components.SsButton
import com.shikkhasathi.core.designsystem.components.SsButtonVariant
import com.shikkhasathi.core.designsystem.components.SsChip
import com.shikkhasathi.core.designsystem.numeric
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.model.BdPhone
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.features.auth.presentation.AuthIntent
import com.shikkhasathi.features.auth.presentation.AuthStep
import com.shikkhasathi.features.auth.presentation.AuthUiState
import com.shikkhasathi.features.auth.presentation.AuthViewModel
import org.koin.compose.getKoin

/**
 * Sign-in: phone OTP first (most students), Google and email as secondary routes.
 * On success the session flips to SignedIn and `App()` swaps this screen for the dashboard.
 */
@Composable
fun AuthScreen() {
    val koin = getKoin()
    val vm = rememberSsViewModel { koin.get<AuthViewModel>() }
    val state by vm.state.collectAsStateWithLifecycle()
    AuthContent(state = state, onIntent = vm::onIntent)
}

@Composable
fun AuthContent(
    state: AuthUiState,
    onIntent: (AuthIntent) -> Unit,
) {
    val palette = SsTheme.palette
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SsSpace.s6, vertical = SsSpace.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SsSpace.s4)) {
            Text(Str.AppName.t(), style = MaterialTheme.typography.headlineMedium, color = palette.primary)
            AnimatedContent(
                targetState = state.step,
                transitionSpec = { fadeIn(tween(MOTION_MS)) togetherWith fadeOut(tween(MOTION_MS)) },
                label = "authStep",
            ) { step ->
                Column(verticalArrangement = Arrangement.spacedBy(SsSpace.s4)) {
                    when (step) {
                        AuthStep.Phone -> PhoneStep(state, onIntent)
                        AuthStep.Otp -> OtpStep(state, onIntent)
                        AuthStep.Email -> EmailStep(state, onIntent)
                    }
                }
            }
            state.error?.let { ErrorState(message = it.t()) }
            Spacer(Modifier.height(SsSpace.s2))
            Text(Str.AuthTerms.t(), style = MaterialTheme.typography.bodySmall, color = palette.muted)
        }
    }
}

@Composable
private fun PhoneStep(
    state: AuthUiState,
    onIntent: (AuthIntent) -> Unit,
) {
    Heading(Str.AuthTitle.t(), Str.AuthSubtitle.t())
    SsInput(
        value = state.phoneInput,
        onValueChange = { onIntent(AuthIntent.PhoneChanged(it)) },
        label = Str.AuthPhoneLabel.t(),
        placeholder = Str.AuthPhonePlaceholder.t(),
        icon = Lucide.Smartphone,
        keyboardType = KeyboardType.Phone,
        error = state.fieldError?.t(),
        imeAction = ImeAction.Send,
        onIme = { onIntent(AuthIntent.RequestOtp) },
    )
    SsButton(
        text = Str.AuthOtpSend.t(),
        onClick = { onIntent(AuthIntent.RequestOtp) },
        loading = state.isSubmitting,
        modifier = Modifier.fillMaxWidth(),
    )
    OrDivider()
    SsButton(
        text = Str.AuthGoogle.t(),
        onClick = { onIntent(AuthIntent.GoogleLogin) },
        variant = SsButtonVariant.Secondary,
        enabled = !state.isSubmitting,
        modifier = Modifier.fillMaxWidth(),
    )
    SsButton(
        text = Str.AuthEmailCta.t(),
        onClick = { onIntent(AuthIntent.ShowEmailLogin) },
        variant = SsButtonVariant.Ghost,
        leadingIcon = Lucide.Mail,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OtpStep(
    state: AuthUiState,
    onIntent: (AuthIntent) -> Unit,
) {
    val palette = SsTheme.palette
    Heading(Str.AuthOtpTitle.t(), Str.AuthOtpSentTo.t("phone" to BdPhone.pretty(state.phoneE164.orEmpty())))
    OtpField(
        value = state.otp,
        onValueChange = { onIntent(AuthIntent.OtpChanged(it)) },
        contentDescription = Str.AuthOtpTitle.t(),
        isError = state.error != null || state.fieldError != null,
        enabled = !state.isSubmitting,
        onDone = { onIntent(AuthIntent.VerifyOtp) },
    )
    state.fieldError?.let { Text(it.t(), style = MaterialTheme.typography.bodySmall, color = palette.dangerFg) }
    state.devCode?.let { SsChip(text = Str.AuthOtpDevCode.t("code" to it), tone = StatusTone.Warning) }
    SsButton(
        text = Str.AuthOtpVerify.t(),
        onClick = { onIntent(AuthIntent.VerifyOtp) },
        loading = state.isSubmitting,
        enabled = state.otp.length == AuthViewModel.OTP_LENGTH,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.canResend) {
            SsButton(text = Str.AuthOtpResend.t(), onClick = { onIntent(AuthIntent.ResendOtp) }, variant = SsButtonVariant.Ghost)
        } else {
            Text(
                Str.AuthOtpResendIn.t("seconds" to state.resendInSec),
                style = MaterialTheme.typography.bodySmall.numeric,
                color = palette.muted,
                modifier = Modifier.weight(1f),
            )
        }
        SsButton(text = Str.AuthOtpChangeNumber.t(), onClick = { onIntent(AuthIntent.ChangeNumber) }, variant = SsButtonVariant.Ghost)
    }
}

@Composable
private fun EmailStep(
    state: AuthUiState,
    onIntent: (AuthIntent) -> Unit,
) {
    Heading(Str.AuthEmailTitle.t(), null)
    SsInput(
        value = state.email,
        onValueChange = { onIntent(AuthIntent.EmailChanged(it)) },
        label = Str.AuthEmailLabel.t(),
        icon = Lucide.Mail,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
    )
    SsInput(
        value = state.password,
        onValueChange = { onIntent(AuthIntent.PasswordChanged(it)) },
        label = Str.AuthPasswordLabel.t(),
        icon = Lucide.KeyRound,
        keyboardType = KeyboardType.Password,
        isPassword = true,
        error = state.fieldError?.t(),
        imeAction = ImeAction.Done,
        onIme = { onIntent(AuthIntent.EmailLogin) },
    )
    SsButton(
        text = Str.AuthEmailSubmit.t(),
        onClick = { onIntent(AuthIntent.EmailLogin) },
        loading = state.isSubmitting,
        modifier = Modifier.fillMaxWidth(),
    )
    SsButton(
        text = Str.AuthPhoneCta.t(),
        onClick = { onIntent(AuthIntent.ShowPhoneLogin) },
        variant = SsButtonVariant.Ghost,
        leadingIcon = Lucide.Smartphone,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Heading(
    title: String,
    subtitle: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = SsTheme.palette.text,
            modifier = Modifier.semantics { heading() }
        )
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SsTheme.palette.muted)
    }
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s3)) {
        HorizontalDivider(Modifier.weight(1f), color = SsTheme.palette.border)
        Text(Str.CommonOr.t(), style = MaterialTheme.typography.labelMedium, color = SsTheme.palette.muted)
        HorizontalDivider(Modifier.weight(1f), color = SsTheme.palette.border)
    }
}

/** 48dp+ outlined input with token colours and a 2px primary focus ring. */
@Composable
private fun SsInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    placeholder: String? = null,
    error: String? = null,
    isPassword: Boolean = false,
    onIme: () -> Unit = {},
) {
    val palette = SsTheme.palette
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyLarge.numeric) } },
        leadingIcon = { Icon(icon, contentDescription = null) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        shape = SsShape.control,
        textStyle = MaterialTheme.typography.bodyLarge.let { if (keyboardType == KeyboardType.Phone) it.numeric else it },
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.primary,
                unfocusedBorderColor = palette.borderStrong,
                focusedLabelColor = palette.primaryFg,
                unfocusedLabelColor = palette.muted,
                focusedLeadingIconColor = palette.primaryFg,
                unfocusedLeadingIconColor = palette.muted,
                cursorColor = palette.primary,
                errorBorderColor = palette.danger,
                errorLabelColor = palette.dangerFg,
                errorSupportingTextColor = palette.dangerFg,
                errorLeadingIconColor = palette.dangerFg,
                focusedContainerColor = palette.surface,
                unfocusedContainerColor = palette.surface,
                errorContainerColor = palette.surface,
            ),
    )
}

private const val MOTION_MS = 200
