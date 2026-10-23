package com.seblful.klas.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.DialogRadioRow
import com.seblful.klas.ui.common.DurationPickerModal
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.currencyChoiceLabel
import com.seblful.klas.ui.common.currencyChoices
import com.seblful.klas.ui.designsystem.component.ChoiceDialog
import com.seblful.klas.ui.designsystem.component.SettingsGroup
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun LessonSettingsScreen(onBack: (() -> Unit)?, viewModel: SettingsViewModel = metroViewModel()) {
    val backlog by viewModel.pendingSweep.collectAsStateWithLifecycle()
    SettingsPage(R.string.settings_lessons_title, onBack, viewModel) { page, modifier, bottomInset, messages ->
        val settings = page.settings
        val notify = rememberNotificationAction(viewModel, messages)
        LessonSettingsContent(settings, viewModel::update, viewModel::setAutoCompleteEnabled, modifier, bottomInset) {
            if (settings.autoCompleteEnabled && page.notifications == NotificationDelivery.BLOCKED) {
                SystemSettingsRow(
                    title = stringResource(R.string.settings_notifications_fix),
                    description = stringResource(R.string.settings_completion_notifications_blocked),
                    onClick = { notify(viewModel.unblockNotifications()) },
                )
            }
        }
    }
    backlog?.let { count ->
        ConfirmDialog(
            text = pluralStringResource(R.plurals.settings_autocomplete_sweep_confirm, count, count),
            confirmLabel = stringResource(R.string.action_enable),
            onConfirm = viewModel::confirmPendingSweep,
            onDismiss = viewModel::dismissPendingSweep,
        )
    }
}

@Composable
internal fun LessonSettingsContent(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onAutoComplete: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: PaddingValues = PaddingValues(),
    notificationStatus: @Composable () -> Unit = {},
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.gutter).padding(bottomInset)) {
        // By what a row is about, not by when it applies: auto-complete decides what happens to a lesson once
        // it ends, so it sits with the lesson's length; the currency and the group rate are both money.
        SettingsGroup(stringResource(R.string.settings_section_lessons)) {
            DefaultLessonDuration(settings, onUpdate)
            SwitchRow(
                title = stringResource(R.string.settings_autocomplete),
                description = stringResource(R.string.settings_autocomplete_desc),
                checked = settings.autoCompleteEnabled,
                onCheckedChange = onAutoComplete,
            )
            notificationStatus()
        }
        SettingsGroup(stringResource(R.string.settings_section_billing)) {
            CurrencySetting(settings, onUpdate)
            MoneyRow(
                title = stringResource(R.string.settings_default_group_price),
                description = stringResource(R.string.settings_default_group_price_desc),
                amount = settings.defaultGroupPrice,
                onSet = { price -> onUpdate { it.copy(defaultGroupPrice = price) } },
            )
        }
    }
}

@Composable
private fun DefaultLessonDuration(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    LessonLengthSetting(
        minutes = settings.defaultLessonMinutes,
        onSelect = { minutes -> onUpdate { it.copy(defaultLessonMinutes = minutes) } },
        hint = stringResource(R.string.settings_default_lesson_desc),
    )
}

/** The usual lesson length, as presets and a custom length — here, and in setup with its own [hint]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LessonLengthSetting(minutes: Int, onSelect: (Int) -> Unit, hint: String) {
    var pickingDuration by rememberSaveable { mutableStateOf(false) }
    DurationPresetRow(
        title = stringResource(R.string.settings_default_lesson),
        presets = LessonPresets,
        minutes = minutes,
        customWord = stringResource(R.string.class_duration_custom),
        onSelect = onSelect,
        onCustom = { pickingDuration = true },
    )
    SettingsHint(hint)
    if (pickingDuration) {
        DurationPickerModal(
            initialMinutes = minutes,
            title = stringResource(R.string.settings_default_lesson),
            onConfirm = { picked ->
                onSelect(picked)
                pickingDuration = false
            },
            onDismiss = { pickingDuration = false },
        )
    }
}

/**
 * A row like its neighbours, not the setup's outlined field: a form control in a settings card gave the card
 * two edges and two kinds of row.
 */
@Composable
private fun CurrencySetting(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var pendingCurrency by rememberSaveable { mutableStateOf<Currency?>(null) }
    val title = stringResource(R.string.settings_currency)
    ValueRow(
        title = title,
        value = settings.currency.name,
        enabled = true,
        description = stringResource(R.string.settings_currency_desc),
    ) { picking = true }
    if (picking) {
        val locale = LocalResources.current.configuration.locales[0]
        val choices = remember(locale) { currencyChoices(locale) }
        // Picking is the answer, as in the weekday dialogs; the change is confirmed below, not here.
        ChoiceDialog(title = title, onDismiss = { picking = false }) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { currency ->
                    DialogRadioRow(
                        label = currencyChoiceLabel(currency),
                        selected = currency == settings.currency,
                        onClick = {
                            picking = false
                            if (currency != settings.currency) pendingCurrency = currency
                        },
                    )
                }
            }
        }
    }
    pendingCurrency?.let { currency ->
        ConfirmDialog(
            text = stringResource(R.string.settings_currency_change_confirm, currencyChoiceLabel(currency)),
            confirmLabel = stringResource(R.string.settings_currency_change),
            onConfirm = { onUpdate { it.copy(currency = currency) } },
            onDismiss = { pendingCurrency = null },
        )
    }
}
