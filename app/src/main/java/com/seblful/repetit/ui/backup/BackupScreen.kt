package com.seblful.repetit.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import com.seblful.repetit.R
import com.seblful.repetit.ui.common.ConfirmDialog
import com.seblful.repetit.ui.common.constrainedContent

/**
 * The types a backup file can arrive as: plenty of file managers hand a `.json` over as
 * octet-stream or plain text, so the picker has to accept those too. Shared with the first-run
 * restore so both pickers offer the same files.
 */
internal val BACKUP_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val snackbarHost = remember { SnackbarHostState() }
    var confirmImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportSuccess = stringResource(R.string.backup_export_success)
    val importSuccess = stringResource(R.string.backup_import_success)
    val errorMessage = stringResource(R.string.backup_error)
    val backupFilename = stringResource(R.string.backup_filename)

    LaunchedEffect(Unit) {
        viewModel.events.collect { result ->
            val message = when (result) {
                BackupResult.EXPORT_OK -> exportSuccess
                BackupResult.IMPORT_OK -> importSuccess
                BackupResult.ERROR -> errorMessage
            }
            snackbarHost.showSnackbar(message)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> confirmImportUri = uri } // confirm before replacing everything

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).constrainedContent()) {
            ListItem(
                leadingContent = { Icon(Icons.Filled.Upload, null) },
                headlineContent = { Text(stringResource(R.string.backup_export)) },
                supportingContent = { Text(stringResource(R.string.backup_export_desc)) },
                modifier = Modifier.clickable { exportLauncher.launch(backupFilename) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Download, null) },
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
            onConfirm = { viewModel.import(uri) },
            onDismiss = { confirmImportUri = null },
        )
    }
}
