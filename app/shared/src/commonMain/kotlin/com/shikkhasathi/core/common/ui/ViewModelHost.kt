package com.shikkhasathi.core.common.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shikkhasathi.core.common.SsViewModel

/** Keeps a framework-free [SsViewModel] inside a real androidx ViewModel so it survives configuration changes. */
@PublishedApi
internal class SsViewModelHolder<T : SsViewModel>(val vm: T) : ViewModel() {
    override fun onCleared() = vm.clear()
}

/**
 * Returns the [SsViewModel] scoped to the current `ViewModelStoreOwner` (a NavBackStackEntry inside the
 * NavHost, the Activity / UIViewController outside it), creating it with [create] the first time.
 */
@Composable
inline fun <reified T : SsViewModel> rememberSsViewModel(
    key: String? = null,
    crossinline create: () -> T,
): T {
    val holder: SsViewModelHolder<T> = viewModel(key = key ?: T::class.qualifiedName) { SsViewModelHolder(create()) }
    return holder.vm
}
