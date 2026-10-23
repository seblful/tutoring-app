package com.seblful.klas.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.ui.common.ChoiceRow
import com.seblful.klas.ui.common.LanguageChoices
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.languageLabel
import com.seblful.klas.ui.designsystem.component.SettingsGroup
import com.seblful.klas.ui.theme.dynamicColorAvailable
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun AppearanceSettingsScreen(onBack: (() -> Unit)?, viewModel: SettingsViewModel = metroViewModel()) {
    SettingsPage(R.string.settings_appearance_title, onBack, viewModel) { page, modifier, bottomInset, _ ->
        AppearanceSettingsContent(page.settings, viewModel::update, dynamicColorAvailable, modifier, bottomInset)
    }
}

@Composable
internal fun AppearanceSettingsContent(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    showDynamicColor: Boolean,
    modifier: Modifier = Modifier,
    bottomInset: PaddingValues = PaddingValues(),
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.gutter).padding(bottomInset)) {
        SettingsGroup {
            ChoiceRow(
                title = stringResource(R.string.settings_language),
                options = LanguageChoices.map { it to languageLabel(it) },
                selected = settings.language,
                onSelect = { picked -> if (picked != settings.language) onUpdate { it.copy(language = picked) } },
            )
        }
        SettingsGroup(stringResource(R.string.settings_section_appearance)) {
            ChoiceRow(
                title = stringResource(R.string.settings_theme),
                options = listOf(ThemeChoice.SYSTEM, ThemeChoice.LIGHT, ThemeChoice.DARK).map { it to themeLabel(it) },
                selected = settings.themeMode,
                onSelect = { mode -> onUpdate { it.copy(themeMode = mode) } },
            )
            if (showDynamicColor) {
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    description = stringResource(R.string.settings_dynamic_color_desc),
                    checked = settings.dynamicColorEnabled,
                    onCheckedChange = { on -> onUpdate { it.copy(dynamicColorEnabled = on) } },
                )
            }
        }
    }
}

@Composable
internal fun themeLabel(theme: ThemeChoice): String = stringResource(
    when (theme) {
        ThemeChoice.SYSTEM -> R.string.theme_system
        ThemeChoice.LIGHT -> R.string.theme_light
        ThemeChoice.DARK -> R.string.theme_dark
    },
)
