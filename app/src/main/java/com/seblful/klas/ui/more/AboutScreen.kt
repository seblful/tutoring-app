package com.seblful.klas.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.ui.common.KlasLogo
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.KlasWordmark
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.NoticeDialog
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.designsystem.component.ButtonKind
import com.seblful.klas.ui.designsystem.component.KlasButton
import com.seblful.klas.ui.theme.KlasIcons

/** What the app is and which build of it this is. */
@Composable
fun AboutScreen(onBack: () -> Unit, onBackup: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var showingLicence by remember { mutableStateOf(false) }
    // Read from the installed package rather than from BuildConfig, so it is the version the
    // platform actually has — including the `.debug` suffix a debug install carries.
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    KlasScreen(title = stringResource(R.string.about_title), onBack = onBack) { modifier, bottomInset ->
        Column(
            modifier
                .verticalScroll(rememberScrollState())
                .padding(bottomInset)
                // The app's gutter at the sides, so the card lines up with every other screen's; the
                // extra air stays above and below, where the logo wants it.
                .padding(horizontal = Spacing.gutter, vertical = Spacing.sectionGap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            KlasLogo(Modifier.size(88.dp))
            KlasWordmark(Modifier.width(144.dp).height(56.dp))
            Text(
                stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (version.isNotEmpty()) {
                Text(
                    stringResource(R.string.about_version, version),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionCard(Modifier.padding(top = Spacing.itemGap)) {
                Column(
                    Modifier.fillMaxWidth().padding(Spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
                ) {
                    Text(
                        stringResource(R.string.about_offline),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.about_backup_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Outlined, not filled: this screen has no primary action — it is a statement —
                    // and the button is the way to act on one sentence of it.
                    KlasButton(
                        onClick = onBackup,
                        modifier = Modifier.align(Alignment.End),
                        kind = ButtonKind.Outlined,
                    ) {
                        // Filled: it is a button's glyph, and the app's rule is filled for actions,
                        // outlined for art. «Ещё»'s row uses the outlined one for the same screen,
                        // correctly — that is a leading picture, this is the thing being pressed.
                        Icon(KlasIcons.Filled.Save, contentDescription = null)
                        Text(
                            stringResource(R.string.more_backup),
                            Modifier.padding(start = Spacing.inline),
                        )
                    }
                }
            }
            // The typeface credit, last and quietest — an obligation rather than a feature. The face
            // ships inside the APK, so the OFL's notice ships with it, and a notice nobody can reach is
            // not one: tapping this opens it. It sits on the one screen that already exists to state
            // what the app is made of.
            //
            // A button rather than a caption, because it does something and has to look like it does —
            // `labelSmall` keeps it the smallest text on the screen while the primary colour says it is
            // a control, and the minimum height is the same 48dp everything tappable in this app gets.
            KlasButton(
                onClick = { showingLicence = true },
                modifier = Modifier
                    .padding(top = Spacing.itemGap)
                    .heightIn(min = MinTouchTarget),
                kind = ButtonKind.Text,
            ) {
                Text(
                    stringResource(R.string.about_typeface),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (showingLicence) {
        // Read when it is asked for rather than with the screen: 4KB off disk is nothing, but it is
        // nothing that a tutor opening «О приложении» has any reason to pay for.
        val licence = remember(resources) {
            resources.openRawResource(R.raw.golos_text_ofl).bufferedReader().use { it.readText() }
        }
        NoticeDialog(
            onDismiss = { showingLicence = false },
            title = stringResource(R.string.about_typeface_licence),
        ) {
            // Left in English, as licences are. Scrollable because it is four screens long, and the OFL's
            // terms are not something to show an abridged version of.
            Text(
                licence,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        }
    }
}
