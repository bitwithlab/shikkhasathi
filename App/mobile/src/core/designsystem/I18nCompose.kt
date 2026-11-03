package com.shikkhasathi.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.shikkhasathi.core.i18n.AppLocale
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText

/** App language (bn default). Switched from Settings later; provided at the root in `App()`. */
val LocalAppLocale = staticCompositionLocalOf { AppLocale.Bn }

@Composable
@ReadOnlyComposable
fun Str.t(vararg args: Pair<String, Any>): String = text(LocalAppLocale.current, *args)

@Composable
@ReadOnlyComposable
fun UiText.t(): String = resolve(LocalAppLocale.current)
