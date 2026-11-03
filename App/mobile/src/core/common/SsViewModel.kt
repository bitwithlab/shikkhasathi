package com.shikkhasathi.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Framework-free base for presentation logic (UDF: intents in → immutable `UiState` out).
 *
 * It deliberately does not extend `androidx.lifecycle.ViewModel`, so ViewModels compile and are tested
 * on the plain JVM. The Compose layer keeps each instance inside a real androidx `ViewModel`
 * (`rememberSsViewModel` in `core/designsystem/ui`), which survives configuration changes and calls
 * [clear] from `onCleared()`.
 */
abstract class SsViewModel {
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    open fun clear() {
        scope.cancel()
    }
}
