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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ButtonDefaults
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
import com.seblful.klas.R
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.launchExternally
import com.seblful.klas.ui.common.rememberMessages
import dev.zacsweers.metrox.viewmodel.metroViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = metroViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val messages = rememberMessages()
    val noPickerMsg = stringResource(R.string.student_contact_failed)
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
                // The app's empty state, compact: a sentence on its own read as a stray line of the
                // description above it.
                EmptyState(
                    title = stringResource(R.string.diagnostics_empty),
                    icon = Icons.Outlined.CheckCircle,
                    compact = true,
                )
                // Nothing claims the leftover height, so this is what keeps the debug button at the
                // foot of the screen rather than under the empty-log sentence.
                Spacer(Modifier.weight(1f))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
                    OutlinedButton(onClick = { exportLauncher.launchExternally(exportFilename, noPickerMsg, messages) }) {
                        Text(stringResource(R.string.diagnostics_export))
                    }
                    OutlinedButton(onClick = { confirmingClear = true }) {
                        Text(stringResource(R.string.diagnostics_clear))
                    }
                }
                // **The log takes the space that is left, and scrolls inside it.** A crash log is
                // 64 000 characters at its cap, and a card sized to its content is a card taller than
                // the phone: the log's own `verticalScroll` then had nothing to scroll *within*, so the
                // text simply ran past the bottom of the screen and took «Проверить сбой» — and the
                // «Очистить» button, if the log were long enough — with it. `weight` is what turns a
                // nested scroll from a mistake into the right control: it bounds the viewport the inner
                // scroll moves through.
                SectionCard(Modifier.weight(1f)) {
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
            destructive = true,
            onConfirm = { viewModel.clear() },
            onDismiss = { confirmingClear = false },
        )
    }
}
