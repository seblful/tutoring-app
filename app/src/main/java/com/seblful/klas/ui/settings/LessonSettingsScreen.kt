package com.seblful.klas.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.CurrencyPicker
import com.seblful.klas.ui.common.DurationPickerModal
import com.seblful.klas.ui.common.currencyChoiceLabel
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun LessonSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = metroViewModel()) {
    val backlog by viewModel.pendingSweep.collectAsState()
    SettingsPage(R.string.settings_lessons_title, onBack, viewModel) { page, modifier, messages ->
        val settings = page.settings
        val notify = rememberNotificationAction(viewModel, messages)
        LessonSettingsContent(settings, viewModel::update, viewModel::setAutoCompleteEnabled, modifier) {
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
    notificationStatus: @Composable () -> Unit = {},
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        SectionHeader(stringResource(R.string.settings_section_defaults))
        DefaultLessonDuration(settings, onUpdate)
        MoneyRow(
            title = stringResource(R.string.settings_default_group_price),
            description = stringResource(R.string.settings_default_group_price_desc),
            amount = settings.defaultGroupPrice,
            onSet = { price -> onUpdate { it.copy(defaultGroupPrice = price) } },
        )
        SectionHeader(stringResource(R.string.settings_section_billing))
        CurrencySetting(settings, onUpdate)
        SwitchRow(
            title = stringResource(R.string.settings_autocomplete),
            description = stringResource(R.string.settings_autocomplete_desc),
            checked = settings.autoCompleteEnabled,
            onCheckedChange = onAutoComplete,
        )
        notificationStatus()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultLessonDuration(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    var pickingDuration by rememberSaveable { mutableStateOf(false) }
    DurationPresetRow(
        title = stringResource(R.string.settings_default_lesson),
        presets = LessonPresets,
        minutes = settings.defaultLessonMinutes,
        customWord = stringResource(R.string.class_duration_custom),
        onSelect = { minutes -> onUpdate { it.copy(defaultLessonMinutes = minutes) } },
        onCustom = { pickingDuration = true },
    )
    SettingsHint(stringResource(R.string.settings_default_lesson_desc))
    if (pickingDuration) {
        DurationPickerModal(
            initialMinutes = settings.defaultLessonMinutes,
            title = stringResource(R.string.settings_default_lesson),
            onConfirm = { minutes ->
                onUpdate { it.copy(defaultLessonMinutes = minutes) }
                pickingDuration = false
            },
            onDismiss = { pickingDuration = false },
        )
    }
}

@Composable
private fun CurrencySetting(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    var pendingCurrency by rememberSaveable { mutableStateOf<Currency?>(null) }
    CurrencyPicker(
        description = stringResource(R.string.settings_currency_desc),
        selected = settings.currency,
        onSelect = { if (it != settings.currency) pendingCurrency = it },
    )
    pendingCurrency?.let { currency ->
        ConfirmDialog(
            text = stringResource(R.string.settings_currency_change_confirm, currencyChoiceLabel(currency)),
            confirmLabel = stringResource(R.string.settings_currency_change),
            onConfirm = { onUpdate { it.copy(currency = currency) } },
            onDismiss = { pendingCurrency = null },
        )
    }
}
