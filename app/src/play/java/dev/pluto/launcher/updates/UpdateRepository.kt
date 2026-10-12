package dev.pluto.launcher.updates

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Play handles updates; retain the shared ViewModel API without network or installer code. */
@Suppress("UNUSED_PARAMETER")
class UpdateRepository(context: Context) {
    val state = MutableStateFlow(UpdateState()).asStateFlow()
    suspend fun restore() = Unit
    suspend fun check(force: Boolean = false) = Unit
    suspend fun download() = Unit
    suspend fun install() = Unit
    suspend fun resumeInstallationIfAllowed() = Unit
}
