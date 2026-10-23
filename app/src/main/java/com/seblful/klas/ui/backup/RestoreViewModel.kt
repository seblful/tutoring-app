package com.seblful.klas.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.backup.BackupCipher
import com.seblful.klas.data.backup.BackupFailure
import com.seblful.klas.data.backup.BackupManager
import com.seblful.klas.data.backup.BackupPreview
import com.seblful.klas.data.backup.BackupRestore
import com.seblful.klas.data.backup.WrongPasswordException
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentStore
import com.seblful.klas.ui.common.state.UiEvents
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * Restoring a backup, from the Backup page and first run alike: read the picked file, ask for its password
 * when it is sealed, say what it holds, replace everything on a yes — and put back what was here, after.
 */
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class RestoreViewModel @Inject constructor(
    private val backup: BackupManager,
    private val restore: BackupRestore,
    private val cipher: BackupCipher,
    private val documents: DocumentStore,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val events = UiEvents<BackupResult>()

    private val _importing = MutableStateFlow(false)

    /**
     * Whether a restore is under way. A large one takes seconds, and a screen with nothing moving read as
     * a tap that had not taken — so the tutor pressed back, or tapped «Импорт» again.
     */
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    /** A sealed file waiting for its password; [wrong] once a password has failed to open it. */
    data class Locked(val text: String, val askFirst: Boolean, val wrong: Boolean = false)

    private val _locked = MutableStateFlow<Locked?>(null)
    val locked: StateFlow<Locked?> = _locked.asStateFlow()

    /** A picked file, read and understood, waiting for the tutor to say yes. */
    data class PendingRestore(val text: String, val preview: BackupPreview)

    private val _pendingRestore = MutableStateFlow<PendingRestore?>(null)

    /**
     * The file a restore would replace everything with, while the tutor is asked about it. Kept here rather
     * than in the screen, so the question survives a rotation without reading the file again.
     */
    val pendingRestore: StateFlow<PendingRestore?> = _pendingRestore.asStateFlow()

    /** When the data from before the last restore was kept — the restore can be undone while it is. */
    val undoRestoreSince: StateFlow<Instant?> = restore.undoableSince

    /**
     * Reads the picked file. With [askFirst] it says what the file holds ([pendingRestore]) before anything
     * is replaced; without, as on a first run with nothing to lose, it restores at once. A sealed file asks
     * for its password ([locked]) either way. A file that would be refused is refused here.
     */
    fun open(ref: DocumentRef, askFirst: Boolean = true) {
        if (_importing.value) return
        viewModelScope.launch {
            runCatchingCancellable { documents.read(ref) }
                .onSuccess { text ->
                    if (backup.isSealed(text)) _locked.value = Locked(text, askFirst) else proceed(text, askFirst)
                }
                .onFailure { events.send(failed(it)) }
        }
    }

    fun unlock(password: String) {
        val locked = _locked.value ?: return
        viewModelScope.launch {
            runCatchingCancellable { cipher.open(locked.text, password) }
                .onSuccess {
                    _locked.value = null
                    proceed(it, locked.askFirst)
                }
                .onFailure { error ->
                    if (error is WrongPasswordException) _locked.value = locked.copy(wrong = true)
                    else events.send(failed(error)).also { _locked.value = null }
                }
        }
    }

    fun dismissUnlock() {
        _locked.value = null
    }

    fun confirmRestore() {
        val pending = _pendingRestore.value ?: return
        _pendingRestore.value = null
        restoring { restore.restore(pending.text); BackupResult.ImportOk }
    }

    fun dismissRestore() {
        _pendingRestore.value = null
    }

    /** Puts back what was here before the last restore — see [BackupRestore.undoRestore]. */
    fun undoRestore() = restoring {
        check(restore.undoRestore()) { "Nothing kept from before the last restore" }
        BackupResult.RestoreUndone
    }

    private suspend fun proceed(text: String, askFirst: Boolean) {
        runCatchingCancellable { backup.preview(text) }
            .onSuccess { preview ->
                if (askFirst) {
                    _pendingRestore.value = PendingRestore(text, preview)
                } else {
                    restoring { restore.restore(text); BackupResult.ImportOk }
                }
            }
            .onFailure { events.send(failed(it)) }
    }

    /** One replace at a time, in the app's scope: leaving the screen must not stop one halfway. */
    private fun restoring(replace: suspend () -> BackupResult) {
        if (_importing.value) return
        _importing.value = true
        appScope.launch {
            try {
                events.send(runCatchingCancellable { replace() }.getOrElse(::failed))
            } finally {
                _importing.value = false
            }
        }
    }

    private fun failed(error: Throwable): BackupResult = BackupResult.Failed(BackupFailure.of(error))
}
