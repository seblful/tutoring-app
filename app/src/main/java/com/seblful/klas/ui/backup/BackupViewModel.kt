package com.seblful.klas.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.backup.BackupManager
import com.seblful.klas.data.backup.BackupRestore
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentStore
import dagger.hilt.android.lifecycle.HiltViewModel
import com.seblful.klas.ui.common.UiEvents
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BackupResult { EXPORT_OK, IMPORT_OK, ERROR }

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupManager,
    private val restore: BackupRestore,
    private val documents: DocumentStore,
) : ViewModel() {

    val events = UiEvents<BackupResult>()

    fun export(ref: DocumentRef) {
        viewModelScope.launch {
            val ok = runCatching { documents.write(ref, backup.exportJson()) }.isSuccess
            events.send(if (ok) BackupResult.EXPORT_OK else BackupResult.ERROR)
        }
    }

    /** Reads the picked file through [DocumentStore] and hands the rest to [BackupRestore]. */
    fun import(ref: DocumentRef) {
        viewModelScope.launch {
            val ok = runCatching { restore.restore(documents.read(ref)) }.isSuccess
            events.send(if (ok) BackupResult.IMPORT_OK else BackupResult.ERROR)
        }
    }
}
