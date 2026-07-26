package com.seblful.klas.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.di.DebugBuild
import com.seblful.klas.diagnostics.CrashLogger
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentStore
import dagger.hilt.android.lifecycle.HiltViewModel
import com.seblful.klas.ui.common.UiEvents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DiagnosticsEvent { EXPORT_OK, ERROR }

data class DiagnosticsUiState(
    val hasLog: Boolean = false,
    val log: String = "",
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val crashLogger: CrashLogger,
    private val documents: DocumentStore,
    /**
     * True only for a debuggable (non-release) build. Gates the "force test crash" action so it can
     * never reach a published build.
     */
    @param:DebugBuild val isDebugBuild: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state = _state.asStateFlow()

    val events = UiEvents<DiagnosticsEvent>()

    init {
        refresh()
    }

    private fun refresh() {
        _state.value = DiagnosticsUiState(hasLog = crashLogger.hasLog(), log = crashLogger.read())
    }

    fun export(ref: DocumentRef) {
        viewModelScope.launch {
            val ok = runCatching { documents.write(ref, crashLogger.read()) }.isSuccess
            events.send(if (ok) DiagnosticsEvent.EXPORT_OK else DiagnosticsEvent.ERROR)
        }
    }

    fun clear() {
        crashLogger.clear()
        refresh()
    }
}
