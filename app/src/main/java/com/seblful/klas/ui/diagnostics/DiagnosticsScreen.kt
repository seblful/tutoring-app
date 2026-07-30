package com.seblful.klas.ui.diagnostics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.rememberMessages

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val messages = rememberMessages()
    var confirmingClear by remember { mutableStateOf(false) }

    val exportOk = stringResource(R.string.diagnostics_export_success)
    val error = stringResource(R.string.diagnostics_error)
    val exportFilename = stringResource(R.string.diagnostics_filename)

    ShowMessagesFor(viewModel.events, messages) { event ->
        when (event) {
            DiagnosticsEvent.EXPORT_OK -> exportOk
            DiagnosticsEvent.ERROR -> error
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> uri?.let { viewModel.export(DocumentRef(it.toString())) } }

    KlasScreen(
        title = stringResource(R.string.diagnostics_title),
        onBack = onBack,
        messages = messages,
    ) { modifier ->
        Column(
            modifier.padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            Text(
                stringResource(R.string.diagnostics_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!state.hasLog) {
                Text(stringResource(R.string.diagnostics_empty))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
                    OutlinedButton(onClick = { exportLauncher.launch(exportFilename) }) {
                        Text(stringResource(R.string.diagnostics_export))
                    }
                    OutlinedButton(onClick = { confirmingClear = true }) {
                        Text(stringResource(R.string.diagnostics_clear))
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        state.log,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(Spacing.itemGap),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }

            if (viewModel.isDebugBuild) {
                // Debug-only: verify the crash handler actually records and survives a real crash.
                // The thrown exception is uncaught on purpose — it kills the app; relaunch to see the
                // entry appear above. Never present in a release build (gated by the debuggable flag).
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { throw RuntimeException("Test crash from Diagnostics screen") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.diagnostics_test_crash))
                }
            }
        }
    }

    if (confirmingClear) {
        ConfirmDialog(
            text = stringResource(R.string.diagnostics_clear_confirm),
            confirmLabel = stringResource(R.string.diagnostics_clear),
            onConfirm = { viewModel.clear() },
            onDismiss = { confirmingClear = false },
        )
    }
}
