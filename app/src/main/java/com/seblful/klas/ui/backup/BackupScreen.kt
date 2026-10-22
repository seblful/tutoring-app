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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.data.backup.BackupPreview
import com.seblful.klas.format.formatDate
import com.seblful.klas.format.toLocalDate
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.SkeletonBlock
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.rememberCreateDocument
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.rememberOpenDocument
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.settings.SettingsGroup
import com.seblful.klas.ui.settings.settingsRowColors
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlin.time.Instant

internal val BACKUP_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = metroViewModel(),
) {
    val messages = rememberMessages()
    var confirmUndo by rememberSaveable { mutableStateOf(false) }
    val lastExportAt by viewModel.lastExportAt.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val undoRestoreSince by viewModel.undoRestoreSince.collectAsStateWithLifecycle()

    val exportSuccess = stringResource(R.string.backup_export_success)
    val importSuccess = stringResource(R.string.backup_import_success)
    val restoreUndone = stringResource(R.string.backup_restore_undone)
    val context = LocalContext.current
    val noPickerMsg = stringResource(R.string.student_contact_failed)
    // Named for the day it is made on — see [BackupViewModel.exportDay].
    val backupFilename = stringResource(R.string.backup_filename, viewModel.exportDay().toString())

    ShowMessagesFor(viewModel.events, messages) { result ->
        when (result) {
            BackupResult.ExportOk -> exportSuccess
            BackupResult.ImportOk -> importSuccess
            BackupResult.RestoreUndone -> restoreUndone
            is BackupResult.Failed -> result.failure.messageIn(context)
        }
    }

    val saveBackup = rememberCreateDocument("application/json", noPickerMsg, messages) { viewModel.export(it) }
    // Read and described before it replaces everything.
    val openBackup = rememberOpenDocument(noPickerMsg, messages) { viewModel.preview(it) }

    KlasScreen(
        title = stringResource(R.string.backup_title),
        onBack = onBack,
        messages = messages,
    ) { modifier, bottomInset ->
        BackupContent(
            lastExportAt = lastExportAt,
            importing = importing,
            onExport = { saveBackup(backupFilename) },
            onImport = { openBackup(BACKUP_MIME_TYPES) },
            undoRestoreSince = undoRestoreSince,
            onUndoRestore = { confirmUndo = true },
            modifier = modifier,
            bottomInset = bottomInset,
        )
    }

    pendingRestore?.let { pending ->
        ConfirmRestoreDialog(
            preview = pending.preview,
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::dismissRestore,
        )
    }

    if (confirmUndo) {
        ConfirmDialog(
            text = stringResource(R.string.backup_undo_restore_confirm),
            confirmLabel = stringResource(R.string.backup_undo_restore_action),
            destructive = true,
            onConfirm = viewModel::undoRestore,
            onDismiss = { confirmUndo = false },
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
        text = previewText(preview) + "\n\n" + stringResource(R.string.backup_import_confirm),
        confirmLabel = stringResource(R.string.backup_import),
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** «В копии от 21.06.2026: 12 учеников, 340 занятий, …» — kinds the file has none of are left out. */
@Composable
private fun previewText(preview: BackupPreview): String {
    val res = LocalResources.current
    fun count(plural: Int, n: Int) = res.getQuantityString(plural, n, n)
    val counts = listOfNotNull(
        count(R.plurals.students_count, preview.students),
        preview.groups.takeIf { it > 0 }?.let { count(R.plurals.backup_groups_count, it) },
        count(R.plurals.lessons_count, preview.lessons),
        preview.payments.takeIf { it > 0 }?.let { count(R.plurals.backup_payments_count, it) },
        preview.homework.takeIf { it > 0 }?.let { count(R.plurals.backup_homework_count, it) },
    ).joinToString()
    return preview.exportedAt
        ?.let { stringResource(R.string.backup_restore_holds, it.formatDate(), counts) }
        ?: stringResource(R.string.backup_restore_holds_undated, counts)
}

/**
 * Built from Settings' parts — rows on cards, as on the page it sits beside in «Ещё» — with **when the
 * last copy was made first**: in an app whose data lives on this phone alone, that is the fact here about
 * the tutor rather than about the buttons. Only that row waits to load; the actions can be used before it.
 */
@Composable
internal fun BackupContent(
    lastExportAt: Loadable<Instant?>,
    importing: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    undoRestoreSince: Instant? = null,
    onUndoRestore: () -> Unit = {},
    bottomInset: PaddingValues = PaddingValues(),
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.gutter).padding(bottomInset)) {
        // An action under way rather than a screen loading, so a bar and not a skeleton: the rows are
        // already drawn, and what the tutor needs to know is that the tap took and to wait.
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        SettingsGroup {
            when (lastExportAt) {
                Loadable.Loading -> SkeletonBlock(
                    LastExportRowHeight,
                    Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.inline),
                )
                is Loadable.Ready -> LastExportRow(lastExportAt.value)
            }
        }
        SettingsGroup {
            BackupActionRow(
                icon = KlasIcons.Outlined.Upload,
                title = stringResource(R.string.backup_export),
                description = stringResource(R.string.backup_export_desc),
                enabled = !importing,
                onClick = onExport,
            )
            BackupActionRow(
                icon = KlasIcons.Outlined.Download,
                title = stringResource(R.string.backup_import),
                description = stringResource(if (importing) R.string.backup_importing else R.string.backup_import_desc),
                enabled = !importing,
                onClick = onImport,
            )
            // Only while the data from before the last import is kept — until the next one.
            undoRestoreSince?.let { since ->
                BackupActionRow(
                    icon = KlasIcons.Filled.Undo,
                    title = stringResource(R.string.backup_undo_restore),
                    description = stringResource(R.string.backup_undo_restore_desc, since.formatDate()),
                    enabled = !importing,
                    onClick = onUndoRestore,
                )
            }
        }
    }
}

private val LastExportRowHeight = 72.dp

@Composable
private fun LastExportRow(at: Instant?) {
    val never = at == null
    ListItem(
        leadingContent = {
            Icon(
                if (never) KlasIcons.Outlined.Warning else KlasIcons.Outlined.Schedule,
                contentDescription = null,
                tint = if (never) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(
                if (never) {
                    stringResource(R.string.backup_never)
                } else {
                    stringResource(R.string.backup_last_export, at.toLocalDate().formatDate())
                },
            )
        },
        supportingContent = {
            Text(stringResource(if (never) R.string.backup_never_detail else R.string.backup_last_export_detail))
        },
        colors = settingsRowColors(),
    )
}

@Composable
private fun BackupActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        colors = settingsRowColors(enabled),
        modifier = Modifier.insetRowPress { source ->
            Modifier.clickable(source, indication = null, enabled = enabled, onClick = onClick)
        },
    )
}
