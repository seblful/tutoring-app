package com.seblful.klas.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.backup.BackupFailure
import com.seblful.klas.data.backup.BackupManager
import com.seblful.klas.data.backup.BackupRestore
import com.seblful.klas.data.repository.SettingsRepository
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import kotlin.time.Instant

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
    private val settings: SettingsRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    private val clock: WallClock,
) : ViewModel() {

    val events = UiEvents<BackupResult>()

    /**
     * When a backup was last exported, or null if one never has been — what the screen leads with.
     *
     * The screen used to state nothing at all: two rows describing what export and import *are*, on the
     * one screen of an app whose whole promise is that the data is on this device and nowhere else. When
     * the last copy was made is the single most valuable fact in that promise, and the app knew it and
     * threw it away.
     */
    /**
     * ...and [Loadable], because **null is the answer that matters here.** «Копий ещё не было» is the
     * sentence this screen exists to make, tinted so it is the one thing on it worth acting on today — and
     * an initial `null` said it to every tutor for the frame or two before the row was read, including the
     * ones who exported yesterday. Loading and never-exported are the same value without this, and only
     * one of them is a warning. See [Loadable].
     */
    val lastExportAt: StateFlow<Loadable<Instant?>> = settings.observe()
        .map { it.lastExportAt }
        .ready()
        .hold(this, Loadable.Loading)

    /**
     * Writes the snapshot, then **records that it happened** — in that order, and only if it did.
     *
     * Two guarded steps rather than one, and the split is the point in both directions. A stamp written
     * for an export that failed would have the app telling the tutor they have a copy they do not have.
     * And a stamp that *could not* be written is still not a failed export — the file is on the disk they
     * chose — so it gets its own guard instead of turning a successful export into a reported error.
     *
     * **The two steps run in different scopes, and that is the whole reason the receipt is reliable.**
     * The export itself stays in `viewModelScope`, as `DiagnosticsViewModel`'s does: what an abandoned one
     * leaves behind is a short file in a place the tutor picked and can see, not a row missing from the
     * ledger. The receipt is a **write to the database**, so it goes to the application scope by the rule
     * every other write in this app follows — and here the rule has teeth. `viewModelScope` dies when this
     * screen is popped, so a tutor who exports a large snapshot through SAF and backs out could otherwise
     * be left with the file on disk and «Копий ещё не было» on the screen: the exact wrong answer this
     * feature exists to prevent, and one no failure test can catch, because a cancellation is not a
     * failure.
     */
    fun export(ref: DocumentRef) {
        viewModelScope.launch {
            val written = runCatchingCancellable { documents.write(ref, backup.exportJson()) }
            if (written.isSuccess) recordExport()
            events.send(written.fold({ BackupResult.ExportOk }, ::failed))
        }
    }

    /**
     * Stamps the settings row with the moment the copy was made, outliving the screen that asked for it.
     *
     * The failure is reported to the crash log rather than to the tutor — a receipt that would not write
     * is not something they can act on, and telling them the export failed would be false — but it is
     * reported: a silent `runCatching` here would make a stamp that never lands invisible everywhere.
     * `appScope` carries a `CoroutineExceptionHandler` into the log, so *not* guarding is what reports it.
     */
    private fun recordExport() {
        appScope.launch { settings.update { it.copy(lastExportAt = clock.now()) } }
    }

    private val _importing = MutableStateFlow(false)

    /**
     * Whether a restore is under way. A large one takes seconds, and a screen with nothing moving read as
     * a tap that had not taken — so the tutor pressed back, or tapped «Импорт» again.
     */
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    /**
     * Reads the picked file through [DocumentStore] and hands the rest to [BackupRestore].
     *
     * **In the application scope**, as every write is: the rows commit in one transaction, but the
     * restore goes on past it — the first-run flag, the reminder plan, the calendar re-mirror — and in
     * `viewModelScope` a back press cancelled that tail, leaving the data replaced and the reminders and
     * calendar still describing the old one. A second tap while one is running is ignored.
     */
    fun import(ref: DocumentRef) {
        if (_importing.value) return
        _importing.value = true
        appScope.launch {
            try {
                val result = runCatchingCancellable { restore.restore(documents.read(ref)) }
                events.send(result.fold({ BackupResult.ImportOk }, ::failed))
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
