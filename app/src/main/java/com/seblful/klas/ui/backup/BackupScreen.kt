package com.seblful.klas.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.rememberMessages

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

    val exportSuccess = stringResource(R.string.backup_export_success)
    val importSuccess = stringResource(R.string.backup_import_success)
    val backupFilename = stringResource(R.string.backup_filename)
    val context = LocalContext.current

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
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Upload, null) },
                headlineContent = { Text(stringResource(R.string.backup_export)) },
                supportingContent = { Text(stringResource(R.string.backup_export_desc)) },
                modifier = Modifier.clickable { exportLauncher.launch(backupFilename) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Download, null) },
                headlineContent = { Text(stringResource(R.string.backup_import)) },
                supportingContent = { Text(stringResource(R.string.backup_import_desc)) },
                modifier = Modifier.clickable { importLauncher.launch(BACKUP_MIME_TYPES) },
            )
        }
    }

    confirmImportUri?.let { uri ->
        ConfirmDialog(
            text = stringResource(R.string.backup_import_confirm),
            confirmLabel = stringResource(R.string.backup_import),
            onConfirm = { viewModel.import(DocumentRef(uri.toString())) },
            onDismiss = { confirmImportUri = null },
        )
    }
}
