package com.seblful.klas.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.format.formatDate
import com.seblful.klas.format.toLocalDate
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.SkeletonBlock
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.launchExternally
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import java.time.Instant

/**
 * The types a backup file can arrive as: plenty of file managers hand a `.json` over as
 * octet-stream or plain text, so the picker has to accept those too. Shared with the first-run
 * restore so both pickers offer the same files.
 */
internal val BACKUP_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val messages = rememberMessages()
    var confirmImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val lastExportAt by viewModel.lastExportAt.collectAsState()
    val importing by viewModel.importing.collectAsState()

    val exportSuccess = stringResource(R.string.backup_export_success)
    val importSuccess = stringResource(R.string.backup_import_success)
    val context = LocalContext.current
    val noPickerMsg = stringResource(R.string.student_contact_failed)
    // Named for the day it is made on — see [BackupViewModel.exportDay].
    val backupFilename = stringResource(R.string.backup_filename, viewModel.exportDay().toString())

    ShowMessagesFor(viewModel.events, messages) { result ->
        when (result) {
            BackupResult.ExportOk -> exportSuccess
            BackupResult.ImportOk -> importSuccess
            is BackupResult.Failed -> result.failure.messageIn(context)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.export(DocumentRef(it.toString())) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> confirmImportUri = uri } // confirm before replacing everything

    KlasScreen(
        title = stringResource(R.string.backup_title),
        onBack = onBack,
        messages = messages,
    ) { modifier ->
        Column(modifier) {
            // An action under way rather than a screen loading, so a bar and not a skeleton: the rows are
            // already drawn, and what the tutor needs to know is that the tap took and to wait.
            if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            // **When the last copy was made, first.** This screen was two rows describing what export
            // and import *are* — and in an app whose whole promise is that the data is on this device
            // and nowhere else, the date of the last backup is the most valuable fact in the product.
            // The app knew it and threw it away; now it keeps it (`AppSettings.lastExportAt`) and leads
            // with it, because it is the only thing here that is about the tutor rather than about the
            // buttons.
            // Only this card waits. The two rows under it are things to *do* and they can be done before
            // the receipt has been read — a screen that withholds «Экспорт» while it works out whether the
            // tutor has a copy is withholding the way to make one.
            when (val loaded = lastExportAt) {
                Loadable.Loading -> SkeletonBlock(
                    LastExportCardHeight,
                    Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.inline),
                )
                is Loadable.Ready -> LastExportCard(loaded.value)
            }
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Upload, null) },
                headlineContent = { Text(stringResource(R.string.backup_export)) },
                supportingContent = { Text(stringResource(R.string.backup_export_desc)) },
                modifier = Modifier.insetRowPress { source ->
                    Modifier.clickable(source, indication = null, enabled = !importing) {
                        exportLauncher.launchExternally(backupFilename, noPickerMsg, messages)
                    }
                },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Download, null) },
                headlineContent = { Text(stringResource(R.string.backup_import)) },
                supportingContent = {
                    Text(stringResource(if (importing) R.string.backup_importing else R.string.backup_import_desc))
                },
                modifier = Modifier.insetRowPress { source ->
                    Modifier.clickable(source, indication = null, enabled = !importing) {
                        importLauncher.launchExternally(BACKUP_MIME_TYPES, noPickerMsg, messages)
                    }
                },
            )
        }
    }

    confirmImportUri?.let { uri ->
        ConfirmRestoreDialog(
            onConfirm = { viewModel.import(DocumentRef(uri.toString())) },
            onDismiss = { confirmImportUri = null },
        )
    }
}

/**
 * «Восстановить из копии?» — asked before a restore that would replace what is on the phone, here and on
 * first run's welcome step alike. A restore is a full replace, so it is a loss and says its button in the
 * error colour; the two places used to spell the question, its button and that colour out separately.
 */
@Composable
fun ConfirmRestoreDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        text = stringResource(R.string.backup_import_confirm),
        confirmLabel = stringResource(R.string.backup_import),
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * The height [LastExportCard] comes to: an icon beside a `titleSmall` over a `bodySmall`, at
 * [Spacing.gutter] all round. Measured off that card rather than set to the spacing scale, for the reason
 * `Skeleton.kt`'s own row heights are — it stands in for something whose size is not its own choice.
 */
private val LastExportCardHeight = 84.dp

/**
 * «Последняя копия: 20 июня 2026» — or «Копий ещё не было», which is the version that matters.
 *
 * A [SectionCard] rather than a third `ListItem`: the two rows below it are things to *do* and this is a
 * statement of fact, and a row that looks like the others but does nothing when tapped is a worse lie
 * than a paragraph. Tinted when there has never been a backup — not the error red, because nothing is
 * broken and the tutor may have decided this deliberately, but not the same grey as the rest of the
 * screen either: it is the one thing here they might want to act on today.
 */
@Composable
private fun LastExportCard(at: Instant?) {
    val never = at == null
    SectionCard(
        Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.inline),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (never) Icons.Outlined.WarningAmber else Icons.Outlined.Schedule,
                contentDescription = null,
                tint = if (never) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column(Modifier.padding(start = Spacing.leading)) {
                Text(
                    if (never) {
                        stringResource(R.string.backup_never)
                    } else {
                        stringResource(R.string.backup_last_export, at.toLocalDate().formatDate())
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(
                        if (never) R.string.backup_never_detail else R.string.backup_last_export_detail
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
