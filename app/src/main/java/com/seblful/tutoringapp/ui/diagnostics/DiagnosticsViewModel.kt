package com.seblful.tutoringapp.ui.diagnostics

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.tutoringapp.diagnostics.CrashLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class DiagnosticsEvent { EXPORT_OK, ERROR }

data class DiagnosticsUiState(
    val hasLog: Boolean = false,
    val log: String = "",
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crashLogger: CrashLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state = _state.asStateFlow()

    private val _events = Channel<DiagnosticsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        refresh()
    }

    private fun refresh() {
        _state.value = DiagnosticsUiState(hasLog = crashLogger.hasLog(), log = crashLogger.read())
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val text = crashLogger.read()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                        ?: error("Cannot open output stream")
                }
            }.isSuccess
            _events.send(if (ok) DiagnosticsEvent.EXPORT_OK else DiagnosticsEvent.ERROR)
        }
    }

    fun clear() {
        crashLogger.clear()
        refresh()
    }
}
