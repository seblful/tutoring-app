package com.seblful.klas.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.backup.AutoBackup
import com.seblful.klas.data.backup.BackupFailure
import com.seblful.klas.data.backup.BackupManager
import com.seblful.klas.data.backup.BackupPreview
import com.seblful.klas.data.backup.BackupRestore
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.domain.time.today
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentStore
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.hold
import com.seblful.klas.ui.common.state.ready
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * How an export or an import ended. A failure carries [BackupFailure] rather than being one flat
 * `ERROR`, so the screen can say which thing went wrong — see that type for why the distinction is
 * worth keeping.
 */
sealed interface BackupResult {
    data object ExportOk : BackupResult
    data object ImportOk : BackupResult
    data object RestoreUndone : BackupResult
    data class Failed(val failure: BackupFailure) : BackupResult
}

@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class BackupViewModel @Inject constructor(
    private val backup: BackupManager,
    private val restore: BackupRestore,
    private val documents: DocumentStore,
    private val settings: SettingsRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    private val clock: WallClock,
) : ViewModel() {

    val events = UiEvents<BackupResult>()

    /** Last successful export time; [Loadable.Loading] distinguishes unread settings from no export. */
    val lastExportAt: StateFlow<Loadable<Instant?>> = settings.observe()
        .map { it.lastExportAt }
        .ready()
        .hold(this, Loadable.Loading)

    /** No copy in [AutoBackup.STALE_AFTER] — or none ever. */
    fun isStale(lastExportAt: Instant?): Boolean = AutoBackup.isStale(lastExportAt, clock.now())

    /** Writes the snapshot, then **records that it happened** — in that order, and only if it did. */
    fun export(ref: DocumentRef) {
        viewModelScope.launch {
            val written = runCatchingCancellable { documents.write(ref, backup.exportJson()) }
            if (written.isSuccess) recordExport()
            events.send(written.fold({ BackupResult.ExportOk }, ::failed))
        }
    }

    private fun recordExport() {
        appScope.launch { settings.update { it.copy(lastExportAt = clock.now()) } }
    }

    private val _importing = MutableStateFlow(false)

    /**
     * Whether a restore is under way. A large one takes seconds, and a screen with nothing moving read as
     * a tap that had not taken — so the tutor pressed back, or tapped «Импорт» again.
     */
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

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

    /** Reads the picked file through [DocumentStore] and hands the rest to [BackupRestore]. */
    fun import(ref: DocumentRef) = restoring { restore.restore(documents.read(ref)); BackupResult.ImportOk }

    /**
     * Reads the picked file and says what it holds ([pendingRestore]) before anything is replaced. A file
     * that would be refused is refused here, with the same message, instead of after a «yes».
     */
    fun preview(ref: DocumentRef) {
        if (_importing.value) return
        viewModelScope.launch {
            runCatchingCancellable { documents.read(ref).let { PendingRestore(it, backup.preview(it)) } }
                .onSuccess { _pendingRestore.value = it }
                .onFailure { events.send(failed(it)) }
        }
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

    /**
     * The day an export is named for — `klas-backup-2026-09-24.json` — by the injected clock. One fixed
     * name made every copy after the first «klas-backup (1).json», which says nothing about which is newest.
     */
    fun exportDay(): LocalDate = clock.today()

    private fun failed(error: Throwable): BackupResult = BackupResult.Failed(BackupFailure.of(error))
}
