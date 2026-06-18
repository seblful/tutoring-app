package com.seblful.tutoringapp.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.tutoringapp.data.backup.BackupManager
import com.seblful.tutoringapp.data.repository.ClassRepository
import com.seblful.tutoringapp.work.WorkScheduler
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
    @ApplicationContext private val context: Context,
    private val backup: BackupManager,
    private val workScheduler: WorkScheduler,
    private val classes: ClassRepository,
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

    fun import(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: error("Cannot open input stream")
                    backup.importJson(text)
                }
            }.isSuccess
            if (ok) {
                // Imported classes change what's upcoming/overdue — re-run auto-complete & reminders,
                // and re-mirror to this device's calendar (imported event ids came from another device).
                workScheduler.refreshNow()
                runCatching { classes.resyncCalendar() } // best-effort, like all calendar work
            }
            _events.send(if (ok) BackupResult.IMPORT_OK else BackupResult.ERROR)
        }
    }
}
