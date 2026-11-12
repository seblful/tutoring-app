package com.seblful.klas.ui.backup

import androidx.lifecycle.ViewModel
import com.seblful.klas.data.backup.AutoBackup
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.settings.SettingsWorkflow
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.FolderGrants
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The Backup page's weekly copy — see [AutoBackup]: which folder, whether it still works, picking another. */
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class WeeklyBackupViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val workflow: SettingsWorkflow,
    private val autoBackup: AutoBackup,
    private val grants: FolderGrants,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    enum class Event { SAVED, FOLDER_REFUSED }

    val events = UiEvents<Event>()

    /** Where the copies go, by the folder's own name, and whether the last one could not be written there. */
    data class Weekly(val folder: String?, val folderName: String?, val failed: Boolean)

    val weekly: StateFlow<Loadable<Weekly>> = settings.observe()
        .map { it.autoBackupFolder to it.autoBackupFailed }
        .distinctUntilChanged()
        .map { (folder, failed) -> Weekly(folder, folder?.let { grants.name(it) }, failed) }
        .ready()
        .hold(this, Loadable.Loading)

    /**
     * Sends the weekly copy to the folder picked, keeping access to it across restarts, and makes the first
     * copy at once if one is due — so the tutor sees it work rather than trusting it will next week.
     */
    fun choose(ref: DocumentRef) {
        appScope.launch {
            if (runCatchingCancellable { grants.keep(ref.value) }.isFailure) {
                events.send(Event.FOLDER_REFUSED)
                return@launch
            }
            val before = settings.get().autoBackupFolder
            workflow.update { it.copy(autoBackupFolder = ref.value, autoBackupFailed = false) }
            if (before != null && before != ref.value) grants.release(before)
            if (autoBackup.runIfDue() == AutoBackup.Outcome.SAVED) events.send(Event.SAVED)
        }
    }

    fun stop() {
        appScope.launch {
            val before = settings.get().autoBackupFolder ?: return@launch
            workflow.update { it.copy(autoBackupFolder = null, autoBackupFailed = false) }
            grants.release(before)
        }
    }
}
