package com.seblful.klas.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.ui.common.IconAvatar
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.OpensScreenMark
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.languageLabel
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * @param onBack leaves Settings, with the page open beside the index if there is one.
 * @param open the page open beside the index, marked on it; null when none is.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    navigation: SettingsNavigation,
    open: SettingsCategory? = null,
    viewModel: SettingsViewModel = metroViewModel(),
) {
    SettingsPage(R.string.settings_title, onBack, viewModel) { page, modifier, bottomInset, _ ->
        SettingsIndex(
            page.settings, page.notifications, page.reminders, page.calendar,
            navigation,
            open,
            modifier,
            bottomInset,
        )
    }
}

class SettingsNavigation(
    val lessons: () -> Unit,
    val notifications: () -> Unit,
    val calendar: () -> Unit,
    val appearance: () -> Unit,
    val diagnostics: () -> Unit,
)

/** The pages the index opens. */
enum class SettingsCategory { APPEARANCE, NOTIFICATIONS, LESSONS, CALENDAR, DIAGNOSTICS }

@Composable
internal fun SettingsIndex(
    settings: AppSettings,
    notifications: NotificationDelivery,
    reminders: ReminderDelivery,
    calendar: CalendarDelivery,
    navigation: SettingsNavigation,
    open: SettingsCategory? = null,
    modifier: Modifier = Modifier,
    bottomInset: PaddingValues = PaddingValues(),
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.gutter).padding(bottomInset)) {
        SettingsGroup { SettingsCategories(settings, notifications, reminders, calendar, navigation, open) }
        // Its own card: secondary to the four above, and the break between them now says so.
        SettingsGroup {
            SettingsCategoryRow(
                KlasIcons.Outlined.Info,
                stringResource(R.string.diagnostics_title),
                stringResource(R.string.settings_diagnostics_summary),
                navigation.diagnostics,
                selected = open == SettingsCategory.DIAGNOSTICS,
            )
        }
    }
}

@Composable
private fun SettingsCategories(
    settings: AppSettings,
    notifications: NotificationDelivery,
    reminders: ReminderDelivery,
    calendar: CalendarDelivery,
    navigation: SettingsNavigation,
    open: SettingsCategory?,
) {
    SettingsCategoryRow(
        KlasIcons.Outlined.Language,
        stringResource(R.string.settings_appearance_title),
        stringResource(
            R.string.settings_pair_summary,
            if (settings.language == AppLanguage.SYSTEM) {
                stringResource(R.string.settings_system_language)
            } else {
                languageLabel(settings.language)
            },
            stringResource(R.string.settings_theme_summary, themeLabel(settings.themeMode)),
        ),
        navigation.appearance,
        selected = open == SettingsCategory.APPEARANCE,
    )
    SettingsCategoryRow(
        KlasIcons.Outlined.Notifications,
        stringResource(R.string.settings_notifications_title),
        notificationSummary(settings, notifications, reminders),
        navigation.notifications,
        selected = open == SettingsCategory.NOTIFICATIONS,
    )
    SettingsCategoryRow(
        KlasIcons.Outlined.Payments,
        stringResource(R.string.settings_lessons_title),
        stringResource(
            R.string.settings_pair_summary, durationLabel(settings.defaultLessonMinutes), settings.currency.name,
        ),
        navigation.lessons,
        selected = open == SettingsCategory.LESSONS,
    )
    SettingsCategoryRow(
        KlasIcons.Outlined.CalendarMonth,
        stringResource(R.string.settings_calendar_title),
        calendarSummary(settings, calendar),
        navigation.calendar,
        selected = open == SettingsCategory.CALENDAR,
    )
}

/** @param selected for the page open beside the index, tinted within the row's press shape. */
@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    selected: Boolean,
) {
    ListItem(
        leadingContent = { IconAvatar(icon) },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { OpensScreenMark() },
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            settingsRowColors()
        },
        modifier = Modifier
            .then(if (selected) Modifier.semantics { this.selected = true } else Modifier)
            .insetRowPress { Modifier.clickable(it, indication = null, onClick = onClick) },
    )
}

@Composable
private fun notificationSummary(
    settings: AppSettings,
    notifications: NotificationDelivery,
    reminders: ReminderDelivery,
): String = when {
    notifications == NotificationDelivery.BLOCKED -> stringResource(R.string.settings_all_notifications_blocked)
    reminders == ReminderDelivery.LATE -> stringResource(R.string.settings_notifications_delayed)
    settings.enabledNotificationCount == 0 -> stringResource(R.string.settings_off)
    else -> pluralStringResource(
        R.plurals.settings_notification_types, settings.enabledNotificationCount, settings.enabledNotificationCount,
    )
}

@Composable
private fun calendarSummary(settings: AppSettings, delivery: CalendarDelivery): String = when (delivery) {
    CalendarDelivery.BLOCKED -> stringResource(R.string.settings_calendar_sync_blocked)
    CalendarDelivery.OFF -> stringResource(R.string.settings_off)
    CalendarDelivery.ON -> stringResource(
        if (settings.emailInvitesEnabled) {
            R.string.settings_calendar_invites_on
        } else {
            R.string.settings_calendar_invites_off
        },
    )
}

/**
 * Each destination owns its dialogs, permission launchers and one-shot feedback.
 *
 * @param onBack the back arrow, or null beside the index the page was opened from.
 */
@Composable
internal fun SettingsPage(
    title: Int,
    onBack: (() -> Unit)?,
    viewModel: SettingsViewModel,
    content: @Composable (SettingsState, Modifier, PaddingValues, Messages) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = rememberMessages()
    val resources = LocalResources.current
    val failed = stringResource(R.string.action_failed)
    ShowMessagesFor(viewModel.failed, messages) { failed }
    ShowMessagesFor(viewModel.swept, messages) { count ->
        resources.getQuantityString(R.plurals.settings_autocomplete_swept, count, count)
    }
    RefreshSettingsPermissions(viewModel)
    KlasScreen(title = stringResource(title), onBack = onBack, messages = messages) { modifier, bottomInset ->
        when (val loaded = state) {
            Loadable.Loading -> ListSkeleton(modifier.padding(bottomInset), rows = 5, rowHeight = SettingsRowHeight)
            is Loadable.Ready -> content(loaded.value, modifier, bottomInset, messages)
        }
    }
}
