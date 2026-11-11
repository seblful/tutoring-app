package com.seblful.klas.ui.backup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.data.backup.AutoBackup
import com.seblful.klas.data.backup.BackupPreview
import com.seblful.klas.format.formatDate
import com.seblful.klas.format.toLocalDate
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.text.PluralResource
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.Strings
import com.seblful.klas.text.currentTexts
import com.seblful.klas.text.pluralStringResource
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.SkeletonBlock
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.rememberCreateDocument
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.rememberOpenDocument
import com.seblful.klas.ui.common.rememberOpenFolder
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.designsystem.component.Banner
import com.seblful.klas.ui.designsystem.component.BannerKind
import com.seblful.klas.ui.designsystem.component.SettingsGroup
import com.seblful.klas.ui.designsystem.component.settingsRowColors
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasSemantics
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlin.time.Instant

internal const val BACKUP_MIME_TYPE = "application/json"

internal val BACKUP_MIME_TYPES = arrayOf(BACKUP_MIME_TYPE, "application/octet-stream", "text/plain")

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = metroViewModel(),
    restoreViewModel: RestoreViewModel = metroViewModel(),
    weeklyViewModel: WeeklyBackupViewModel = metroViewModel(),
) {
    val messages = rememberMessages()
    var confirmUndo by rememberSaveable { mutableStateOf(false) }
    var askPassword by rememberSaveable { mutableStateOf(false) }
    // The password is held only until the picker answers — see PasswordDialogs for why it is never saved. That
    // one was asked for is saved, so an answer after the process was put away cannot pass for "no password".
    var exportPassword by remember { mutableStateOf<String?>(null) }
    var exportSealed by rememberSaveable { mutableStateOf(false) }
    var passwordLostFor by rememberSaveable { mutableStateOf<String?>(null) }
    val lastExportAt by viewModel.lastExportAt.collectAsStateWithLifecycle()
    val importing by restoreViewModel.importing.collectAsStateWithLifecycle()
    val undoRestoreSince by restoreViewModel.undoRestoreSince.collectAsStateWithLifecycle()
    val weekly by weeklyViewModel.weekly.collectAsStateWithLifecycle()

    val exportSuccess = stringResource(Strings.backup_export_success)
    val importSuccess = stringResource(Strings.backup_import_success)
    val restoreUndone = stringResource(Strings.backup_restore_undone)
    val weeklySaved = stringResource(Strings.backup_weekly_saved)
    val folderRefused = stringResource(Strings.backup_error_file)
    val context = LocalContext.current
    val texts = currentTexts()
    val noPickerMsg = stringResource(Strings.student_contact_failed)
    // Named for the day it is made on — see [BackupViewModel.exportDay].
    val backupFilename = stringResource(Strings.backup_filename, viewModel.exportDay().toString())

    val words: (BackupResult) -> String = { result ->
        when (result) {
            BackupResult.ExportOk -> exportSuccess
            BackupResult.ImportOk -> importSuccess
            BackupResult.RestoreUndone -> restoreUndone
            is BackupResult.Failed -> result.failure.messageIn(texts)
        }
    }
    ShowMessagesFor(viewModel.events, messages, words = words)
    ShowMessagesFor(restoreViewModel.events, messages, words = words)

    val saveBackup = rememberCreateDocument(BACKUP_MIME_TYPE, noPickerMsg, messages) { ref ->
        when (val picked = pickedExport(exportSealed, exportPassword)) {
            is PickedExport.Write -> viewModel.export(ref, picked.password)
            PickedExport.PasswordLost -> {
                passwordLostFor = ref.value
                askPassword = true
            }
        }
        exportPassword = null
        exportSealed = false
    }
    // Read and described before it replaces everything.
    val openBackup = rememberOpenDocument(noPickerMsg, messages) { restoreViewModel.open(it) }
    ShowMessagesFor(weeklyViewModel.events, messages) { event ->
        when (event) {
            WeeklyBackupViewModel.Event.SAVED -> weeklySaved
            WeeklyBackupViewModel.Event.FOLDER_REFUSED -> folderRefused
        }
    }
    val pickFolder = rememberOpenFolder(noPickerMsg, messages) { weeklyViewModel.choose(it) }

    KlasScreen(
        title = stringResource(Strings.backup_title),
        onBack = onBack,
        messages = messages,
    ) { modifier, bottomInset ->
        BackupContent(
            lastExportAt = lastExportAt,
            stale = viewModel.isStale(lastExportAt.valueOrNull()),
            weekly = weekly,
            onPickFolder = pickFolder,
            onStopWeekly = weeklyViewModel::stop,
            importing = importing,
            onExport = { askPassword = true },
            onImport = { openBackup(BACKUP_MIME_TYPES) },
            undoRestoreSince = undoRestoreSince,
            onUndoRestore = { confirmUndo = true },
            modifier = modifier,
            bottomInset = bottomInset,
        )
    }

    RestoreDialogs(restoreViewModel)

    if (askPassword) {
        ExportPasswordDialog(
            onSave = { password ->
                val chosen = passwordLostFor
                if (chosen != null) {
                    viewModel.export(DocumentRef(chosen), password)
                } else {
                    exportPassword = password
                    exportSealed = password != null
                    saveBackup(backupFilename)
                }
            },
            onDismiss = {
                askPassword = false
                passwordLostFor = null
            },
        )
    }

    if (confirmUndo) {
        ConfirmDialog(
            text = stringResource(Strings.backup_undo_restore_confirm),
            confirmLabel = stringResource(Strings.backup_undo_restore_action),
            destructive = true,
            onConfirm = restoreViewModel::undoRestore,
            onDismiss = { confirmUndo = false },
        )
    }
}

/**
 * What a restore asks on the way: a sealed file's password, then what the file holds. Here and on first run
 * alike, so the two places a backup can be restored cannot ask differently.
 */
@Composable
fun RestoreDialogs(viewModel: RestoreViewModel) {
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val pending by viewModel.pendingRestore.collectAsStateWithLifecycle()
    locked?.let {
        UnlockBackupDialog(wrong = it.wrong, onUnlock = viewModel::unlock, onDismiss = viewModel::dismissUnlock)
    }
    pending?.let {
        ConfirmRestoreDialog(
            preview = it.preview,
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::dismissRestore,
        )
    }
}

/**
 * «Восстановить из копии?» — asked before a restore that would replace what is on the phone, here and on
 * first run's welcome step alike. It first says what the file holds and when it was made, so a tutor with
 * two copies can tell they picked the right one. A restore is a full replace, so it is a loss and says its
 * button in the error colour.
 */
@Composable
fun ConfirmRestoreDialog(preview: BackupPreview, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        text = previewText(preview) + "\n\n" + stringResource(Strings.backup_import_confirm),
        confirmLabel = stringResource(Strings.backup_import),
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** «В копии от 21.06.2026: 12 учеников, 340 занятий, …» — kinds the file has none of are left out. */
@Composable
private fun previewText(preview: BackupPreview): String {
    val res = currentTexts()
    fun count(plural: PluralResource, n: Int) = res.plural(plural, n, n)
    val counts = listOfNotNull(
        count(Plurals.students_count, preview.students),
        preview.groups.takeIf { it > 0 }?.let { count(Plurals.backup_groups_count, it) },
        count(Plurals.lessons_count, preview.lessons),
        preview.payments.takeIf { it > 0 }?.let { count(Plurals.backup_payments_count, it) },
        preview.homework.takeIf { it > 0 }?.let { count(Plurals.backup_homework_count, it) },
    ).joinToString()
    return preview.exportedAt
        ?.let { stringResource(Strings.backup_restore_holds, it.formatDate(), counts) }
        ?: stringResource(Strings.backup_restore_holds_undated, counts)
}

/**
 * Built from Settings' parts — rows on cards, as on the page it sits beside in «Ещё» — with **when the
 * last copy was made first**: in an app whose data lives on this phone alone, that is the fact here about
 * the tutor rather than about the buttons. Only that row waits to load; the actions can be used before it.
 */
@Composable
fun BackupContent(
    lastExportAt: Loadable<Instant?>,
    weekly: Loadable<WeeklyBackupViewModel.Weekly>,
    importing: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    undoRestoreSince: Instant? = null,
    onUndoRestore: () -> Unit = {},
    stale: Boolean = false,
    onPickFolder: () -> Unit = {},
    onStopWeekly: () -> Unit = {},
    bottomInset: PaddingValues = PaddingValues(),
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.gutter).padding(bottomInset)) {
        // An action under way rather than a screen loading, so a bar and not a skeleton: the rows are
        // already drawn, and what the tutor needs to know is that the tap took and to wait.
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        LastExport(lastExportAt, stale)
        SettingsGroup(stringResource(Strings.backup_weekly_header)) {
            when (weekly) {
                Loadable.Loading -> SkeletonBlock(
                    LastExportRowHeight,
                    Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.inline),
                )
                is Loadable.Ready -> WeeklyRows(weekly.value, onPickFolder, onStopWeekly)
            }
        }
        SettingsGroup {
            BackupActionRow(
                icon = KlasIcons.Outlined.Upload,
                title = stringResource(Strings.backup_export),
                description = stringResource(Strings.backup_export_desc),
                enabled = !importing,
                onClick = onExport,
            )
            BackupActionRow(
                icon = KlasIcons.Outlined.Download,
                title = stringResource(Strings.backup_import),
                description = stringResource(if (importing) Strings.backup_importing else Strings.backup_import_desc),
                enabled = !importing,
                onClick = onImport,
            )
            // Only while the data from before the last import is kept — until the next one.
            undoRestoreSince?.let { since ->
                BackupActionRow(
                    icon = KlasIcons.Filled.Undo,
                    title = stringResource(Strings.backup_undo_restore),
                    description = stringResource(Strings.backup_undo_restore_desc, since.formatDate()),
                    enabled = !importing,
                    onClick = onUndoRestore,
                )
            }
        }
    }
}

private val LastExportRowHeight = 72.dp

/**
 * Off: one row that picks a folder. On: where the copies go, and a way to stop. A copy that could not be
 * written comes first, in the warning colour, since it means the copies have stopped without being asked.
 */
@Composable
private fun WeeklyRows(weekly: WeeklyBackupViewModel.Weekly, onPickFolder: () -> Unit, onStopWeekly: () -> Unit) {
    val keeps = pluralStringResource(Plurals.backup_weekly_keeps, AutoBackup.KEEP, AutoBackup.KEEP)
    if (weekly.failed) {
        ListItem(
            leadingContent = {
                Icon(KlasIcons.Outlined.Warning, contentDescription = null, tint = klasSemantics.attention)
            },
            headlineContent = { Text(stringResource(Strings.backup_weekly_failed)) },
            supportingContent = { Text(stringResource(Strings.backup_weekly_failed_desc)) },
            colors = settingsRowColors(),
            modifier = Modifier.insetRowPress { source ->
                Modifier.clickable(source, indication = null, onClick = onPickFolder)
            },
        )
    }
    if (weekly.folder == null) {
        BackupActionRow(
            icon = KlasIcons.Outlined.Folder,
            title = stringResource(Strings.backup_weekly_off),
            description = stringResource(Strings.backup_weekly_off_desc) + " " + keeps,
            enabled = true,
            onClick = onPickFolder,
        )
        return
    }
    BackupActionRow(
        icon = KlasIcons.Outlined.Folder,
        title = weekly.folderName?.let { stringResource(Strings.backup_weekly_on, it) }
            ?: stringResource(Strings.backup_weekly_on_unnamed),
        description = keeps + " " + stringResource(Strings.backup_weekly_change),
        enabled = true,
        onClick = onPickFolder,
    )
    BackupActionRow(
        icon = KlasIcons.Filled.Close,
        title = stringResource(Strings.backup_weekly_stop),
        description = null,
        enabled = true,
        onClick = onStopWeekly,
    )
}

/**
 * When the last copy was made: a row while it is recent; missing or [stale], the advisory banner (R2) — nothing
 * is broken, but a lost phone would take everything since with it.
 */
@Composable
private fun LastExport(lastExportAt: Loadable<Instant?>, stale: Boolean) {
    val at = when (lastExportAt) {
        Loadable.Loading -> {
            SettingsGroup {
                SkeletonBlock(
                    LastExportRowHeight,
                    Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.inline),
                )
            }
            return
        }
        is Loadable.Ready -> lastExportAt.value
    }
    if (at != null && !stale) {
        SettingsGroup { LastExportRow(at) }
        return
    }
    Banner(
        title = at?.let { stringResource(Strings.backup_last_export, it.toLocalDate().formatDate()) }
            ?: stringResource(Strings.backup_never),
        kind = BannerKind.Advisory,
        detail = stringResource(if (at == null) Strings.backup_never_detail else Strings.backup_stale_detail),
        modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.itemGap),
    )
}

/** When the last copy was made, while it is recent; an old or missing one is a banner instead. */
@Composable
private fun LastExportRow(at: Instant) {
    ListItem(
        leadingContent = {
            Icon(
                KlasIcons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(stringResource(Strings.backup_last_export, at.toLocalDate().formatDate())) },
        supportingContent = { Text(stringResource(Strings.backup_last_export_detail)) },
        colors = settingsRowColors(),
    )
}

@Composable
private fun BackupActionRow(
    icon: ImageVector,
    title: String,
    description: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        colors = settingsRowColors(enabled),
        modifier = Modifier.insetRowPress { source ->
            Modifier.clickable(source, indication = null, enabled = enabled, onClick = onClick)
        },
    )
}
