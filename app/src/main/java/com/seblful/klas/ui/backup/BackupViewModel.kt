package com.seblful.klas.ui.backup

import androidx.lifecycle.ViewModel
import com.seblful.klas.data.backup.AutoBackup
import com.seblful.klas.data.backup.BackupExports
import com.seblful.klas.data.backup.BackupFailure
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.domain.time.today
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.storage.DocumentRef
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
import kotlinx.coroutines.flow.StateFlow
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

/**
 * The Backup page's export side: making a copy, and when the last one was made. Restoring is
 * [RestoreViewModel]'s, the weekly copy [WeeklyBackupViewModel]'s.
 */
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class BackupViewModel @Inject constructor(
    private val exports: BackupExports,
    settings: SettingsRepository,
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

    /**
     * Writes the snapshot — sealed with [password] when one is given — then **records that it happened**, in
     * that order, and only if it did.
     */
    fun export(ref: DocumentRef, password: String? = null) {
        appScope.launch {
            val written = runCatchingCancellable { exports.toDocument(ref, password) }
            events.send(written.fold({ BackupResult.ExportOk }, { BackupResult.Failed(BackupFailure.of(it)) }))
        }
    }

    /**
     * The day an export is named for — `klas-backup-2026-09-24.json` — by the injected clock. One fixed
     * name made every copy after the first «klas-backup (1).json», which says nothing about which is newest.
     */
    fun exportDay(): LocalDate = clock.today()
}
