package com.seblful.klas.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.domain.model.WeekStartChoice
import com.seblful.klas.format.fullDisplayName
import com.seblful.klas.ui.common.ChoiceRow
import com.seblful.klas.ui.common.DialogRadioRow
import com.seblful.klas.ui.common.LanguageChoices
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.languageLabel
import com.seblful.klas.ui.designsystem.component.ChoiceDialog
import com.seblful.klas.ui.designsystem.component.SettingsGroup
import com.seblful.klas.ui.theme.dynamicColorAvailable
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.datetime.DayOfWeek

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
            ListSetting(
                stringResource(R.string.settings_language),
                LanguageChoices,
                settings.language,
                { languageLabel(it) },
            ) { picked -> onUpdate { it.copy(language = picked) } }
            ListSetting(
                stringResource(R.string.settings_week_start),
                WeekStartChoice.entries,
                settings.weekStart,
                { weekStartLabel(it) },
            ) { picked -> onUpdate { it.copy(weekStart = picked) } }
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
internal fun weekStartLabel(choice: WeekStartChoice): String = when (choice) {
    WeekStartChoice.PHONE -> stringResource(R.string.week_start_phone)
    WeekStartChoice.MONDAY -> DayOfWeek.MONDAY.fullDisplayName()
    WeekStartChoice.SUNDAY -> DayOfWeek.SUNDAY.fullDisplayName()
    WeekStartChoice.SATURDAY -> DayOfWeek.SATURDAY.fullDisplayName()
}

@Composable
internal fun themeLabel(theme: ThemeChoice): String = stringResource(
    when (theme) {
        ThemeChoice.SYSTEM -> R.string.theme_system
        ThemeChoice.LIGHT -> R.string.theme_light
        ThemeChoice.DARK -> R.string.theme_dark
    },
)

/**
 * A value row opening the list, as Currency's does: past four choices [ChoiceRow] became an outlined field,
 * and a form field in a settings card gave it two edges and two kinds of row. The week's first day too:
 * four full day names in segments shrank to «Понедель…» in Russian.
 */
@Composable
private fun <T> ListSetting(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    ValueRow(title = title, value = label(selected), enabled = true) { picking = true }
    if (picking) {
        ChoiceDialog(title = title, onDismiss = { picking = false }) {
            // Twenty-six short rows compose at once without a stall, so the list is a plain scrolling column.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { choice ->
                    DialogRadioRow(
                        label = label(choice),
                        selected = choice == selected,
                        onClick = {
                            picking = false
                            if (choice != selected) onPick(choice)
                        },
                    )
                }
            }
        }
    }
}
