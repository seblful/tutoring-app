package com.seblful.klas.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.backup.BackupFailure
import com.seblful.klas.data.backup.BackupManager
import com.seblful.klas.data.backup.BackupRestore
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentStore
import dagger.hilt.android.lifecycle.HiltViewModel
import com.seblful.klas.ui.common.UiEvents
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * How an export or an import ended. A failure carries [BackupFailure] rather than being one flat
 * `ERROR`, so the screen can say which thing went wrong — see that type for why the distinction is
 * worth keeping.
 */
sealed interface BackupResult {
    data object ExportOk : BackupResult
    data object ImportOk : BackupResult
    data class Failed(val failure: BackupFailure) : BackupResult
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupManager,
    private val restore: BackupRestore,
    private val documents: DocumentStore,
) : ViewModel() {

    val events = UiEvents<BackupResult>()

    fun export(ref: DocumentRef) {
        viewModelScope.launch {
            val result = runCatchingCancellable { documents.write(ref, backup.exportJson()) }
            events.send(result.fold({ BackupResult.ExportOk }, ::failed))
        }
    }

    /** Reads the picked file through [DocumentStore] and hands the rest to [BackupRestore]. */
    fun import(ref: DocumentRef) {
        viewModelScope.launch {
            val result = runCatchingCancellable { restore.restore(documents.read(ref)) }
            events.send(result.fold({ BackupResult.ImportOk }, ::failed))
        }
    }

    private fun failed(error: Throwable): BackupResult = BackupResult.Failed(BackupFailure.of(error))
}
