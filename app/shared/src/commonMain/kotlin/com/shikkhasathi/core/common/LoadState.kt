package com.shikkhasathi.core.common

import com.shikkhasathi.core.i18n.UiText

/** Screen-level load state: skeleton → content, or a sympathetic Bangla error with retry. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>

    data class Failed(val message: UiText) : LoadState<Nothing>

    data class Ready<T>(val data: T) : LoadState<T>
}

val <T> LoadState<T>.dataOrNull: T? get() = (this as? LoadState.Ready<T>)?.data
