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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.format.formatDate
import com.seblful.klas.format.toLocalDate
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.currentTexts
import com.seblful.klas.text.pluralStringResource
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.GuideViewModel
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
import com.seblful.klas.ui.designsystem.component.Banner
import com.seblful.klas.ui.designsystem.component.BannerKind
import com.seblful.klas.ui.designsystem.component.SectionAction
import com.seblful.klas.ui.designsystem.component.SettingsGroup
import com.seblful.klas.ui.designsystem.component.settingsRowColors
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
    guide: GuideViewModel = metroViewModel(),
) {
    SettingsPage(Strings.settings_title, onBack, viewModel) { page, modifier, bottomInset, messages ->
        val allow = rememberNotificationAction(viewModel, messages)
        val shownAgain = stringResource(Strings.settings_tips_shown_again)
        SettingsIndex(
            page.settings, page.notifications, page.reminders, page.calendar,
            navigation,
            open,
            modifier,
            bottomInset,
            facts = page.facts,
            onAllowNotifications = { allow(viewModel.unblockNotifications()) },
            onShowTipsAgain = {
                guide.showAgain()
                messages.show(shownAgain)
            },
        )
    }
}

class SettingsNavigation(
    val lessons: () -> Unit,
    val notifications: () -> Unit,
    val calendar: () -> Unit,
    val appearance: () -> Unit,
    val privacy: () -> Unit,
    val diagnostics: () -> Unit,
    val subjects: () -> Unit = {},
    val backup: () -> Unit = {},
    val about: () -> Unit = {},
)

/** The pages the index opens. */
enum class SettingsCategory { APPEARANCE, NOTIFICATIONS, LESSONS, CALENDAR, PRIVACY, DIAGNOSTICS }

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
    facts: IndexFacts = IndexFacts(),
    onAllowNotifications: () -> Unit = {},
    onShowTipsAgain: () -> Unit = {},
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.gutter).padding(bottomInset)) {
        // Something that does not work, above everything that is merely set (R2).
        if (notifications == NotificationDelivery.BLOCKED) {
            NotificationsBlockedBanner(onAllowNotifications, Modifier.padding(horizontal = Spacing.gutter))
        }
        SettingsGroup { SettingsCategories(settings, notifications, reminders, calendar, navigation, open) }
        // Its own card: the app's data and the app itself. Secondary to the settings above, and the break
        // between them says so.
        SettingsGroup {
            SettingsCategoryRow(
                KlasIcons.Outlined.MenuBook,
                stringResource(Strings.more_subjects),
                if (facts.subjects == 0) {
                    stringResource(Strings.settings_subjects_none)
                } else {
                    pluralStringResource(Plurals.settings_subjects_count, facts.subjects, facts.subjects)
                },
                navigation.subjects,
                selected = false,
            )
            SettingsCategoryRow(
                KlasIcons.Outlined.Save,
                stringResource(Strings.more_backup),
                backupSummary(settings),
                navigation.backup,
                selected = false,
            )
            // An act, not a page: it puts the first steps and every tip back, and says so.
            SettingsCategoryRow(
                KlasIcons.Outlined.Lightbulb,
                stringResource(Strings.settings_tips_again),
                stringResource(Strings.settings_tips_again_summary),
                onShowTipsAgain,
                selected = false,
                opens = false,
            )
            SettingsCategoryRow(
                KlasIcons.Outlined.Info,
                stringResource(Strings.about_title),
                stringResource(Strings.about_version, facts.version),
                navigation.about,
                selected = false,
            )
            SettingsCategoryRow(
                KlasIcons.Outlined.QueryStats,
                stringResource(Strings.diagnostics_title),
                stringResource(
                    if (facts.crashRecorded) {
                        Strings.settings_diagnostics_crash
                    } else {
                        Strings.settings_diagnostics_none
                    },
                ),
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
        stringResource(Strings.settings_appearance_title),
        stringResource(
            Strings.settings_pair_summary,
            if (settings.language == AppLanguage.SYSTEM) {
                stringResource(Strings.settings_system_language)
            } else {
                languageLabel(settings.language)
            },
            stringResource(Strings.settings_theme_summary, themeLabel(settings.themeMode)),
        ),
        navigation.appearance,
        selected = open == SettingsCategory.APPEARANCE,
    )
    SettingsCategoryRow(
        KlasIcons.Outlined.Notifications,
        stringResource(Strings.settings_notifications_title),
        notificationSummary(settings, notifications, reminders),
        navigation.notifications,
        selected = open == SettingsCategory.NOTIFICATIONS,
    )
    SettingsCategoryRow(
        KlasIcons.Outlined.Payments,
        stringResource(Strings.settings_lessons_title),
        stringResource(
            Strings.settings_pair_summary, durationLabel(settings.defaultLessonMinutes), settings.currency.name,
        ),
        navigation.lessons,
        selected = open == SettingsCategory.LESSONS,
    )
    SettingsCategoryRow(
        KlasIcons.Outlined.CalendarMonth,
        stringResource(Strings.settings_calendar_title),
        calendarSummary(settings, calendar),
        navigation.calendar,
        selected = open == SettingsCategory.CALENDAR,
    )
    SettingsCategoryRow(
        KlasIcons.Outlined.PhonelinkLock,
        stringResource(Strings.settings_privacy_title),
        privacySummary(settings.privacy),
        navigation.privacy,
        selected = open == SettingsCategory.PRIVACY,
    )
}

/**
 * @param selected for the page open beside the index, tinted within the row's press shape.
 * @param opens false for a row that does something in place rather than opening a page: it has no chevron.
 */
@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    selected: Boolean,
    opens: Boolean = true,
) {
    ListItem(
        leadingContent = { IconAvatar(icon, selected = selected) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = if (opens) {
            {
                val colors = MaterialTheme.colorScheme
                OpensScreenMark(if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant)
            }
        } else {
            null
        },
        colors = if (selected) {
            val onSelected = MaterialTheme.colorScheme.onSecondaryContainer
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                headlineColor = onSelected,
                supportingColor = onSelected,
                trailingIconColor = onSelected,
            )
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
    notifications == NotificationDelivery.BLOCKED -> stringResource(Strings.settings_all_notifications_blocked)
    reminders == ReminderDelivery.LATE -> stringResource(Strings.settings_notifications_delayed)
    settings.enabledNotificationCount == 0 -> stringResource(Strings.settings_off)
    else -> pluralStringResource(
        Plurals.settings_notification_types, settings.enabledNotificationCount, settings.enabledNotificationCount,
    )
}

/** «Never · set up weekly», «Weekly · last 3 Oct» — whether a lost phone would take the data with it. */
@Composable
private fun backupSummary(settings: AppSettings): String {
    val last = settings.lastExportAt?.toLocalDate()?.formatDate()
    return when {
        settings.autoBackupFailed -> stringResource(Strings.settings_backup_failed)
        settings.autoBackupFolder != null && last != null -> stringResource(Strings.settings_backup_weekly, last)
        settings.autoBackupFolder != null -> stringResource(Strings.settings_backup_weekly_none)
        last != null -> stringResource(Strings.settings_backup_last, last)
        else -> stringResource(Strings.settings_backup_never)
    }
}

/** Notifications the phone blocks, as the blocking banner every page says it with (R2). */
@Composable
internal fun NotificationsBlockedBanner(onAllow: () -> Unit, modifier: Modifier = Modifier) {
    Banner(
        title = stringResource(Strings.settings_all_notifications_blocked),
        kind = BannerKind.Blocking,
        modifier = modifier.padding(top = Spacing.itemGap),
        action = SectionAction(stringResource(Strings.action_allow), onClick = onAllow),
    )
}

@Composable
private fun calendarSummary(settings: AppSettings, delivery: CalendarDelivery): String = when (delivery) {
    CalendarDelivery.BLOCKED -> stringResource(Strings.settings_calendar_sync_blocked)
    CalendarDelivery.OFF -> stringResource(Strings.settings_off)
    CalendarDelivery.ON -> stringResource(
        if (settings.emailInvitesEnabled) {
            Strings.settings_calendar_invites_on
        } else {
            Strings.settings_calendar_invites_off
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
    title: StringResource,
    onBack: (() -> Unit)?,
    viewModel: SettingsViewModel,
    content: @Composable (SettingsState, Modifier, PaddingValues, Messages) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = rememberMessages()
    val resources = currentTexts()
    val failed = stringResource(Strings.action_failed)
    ShowMessagesFor(viewModel.failed, messages) { failed }
    ShowMessagesFor(viewModel.swept, messages) { count ->
        resources.plural(Plurals.settings_autocomplete_swept, count, count)
    }
    RefreshSettingsPermissions(viewModel)
    KlasScreen(title = stringResource(title), onBack = onBack, messages = messages) { modifier, bottomInset ->
        when (val loaded = state) {
            Loadable.Loading -> ListSkeleton(modifier.padding(bottomInset), rows = 5, rowHeight = SettingsRowHeight)
            is Loadable.Ready -> content(loaded.value, modifier, bottomInset, messages)
        }
    }
}
