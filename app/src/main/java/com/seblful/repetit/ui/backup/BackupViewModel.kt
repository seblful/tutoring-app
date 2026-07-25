package com.seblful.repetit.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.backup.BackupManager
import com.seblful.repetit.data.backup.BackupRestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class BackupResult { EXPORT_OK, IMPORT_OK, ERROR }

@HiltViewModel
class BackupViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backup: BackupManager,
    private val restore: BackupRestore,
) : ViewModel() {

    private val _events = Channel<BackupResult>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun export(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val text = backup.exportJson()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                        ?: error("Cannot open output stream")
                }
            }.isSuccess
            _events.send(if (ok) BackupResult.EXPORT_OK else BackupResult.ERROR)
        }
    }

    /** Reads the picked file here (the `Uri` is UI-side) and hands the rest to [BackupRestore]. */
    fun import(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: error("Cannot open input stream")
                    restore.restore(text) // parsing a whole snapshot stays off the main thread
                }
            }.isSuccess
            _events.send(if (ok) BackupResult.IMPORT_OK else BackupResult.ERROR)
        }
    }
}
