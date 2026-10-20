package com.seblful.klas.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.seblful.klas.R
import com.seblful.klas.calendar.rememberCalendarPermissionRequest
import com.seblful.klas.data.repository.AppSettings
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.notifications.rememberNotificationPermissionRequest
import com.seblful.klas.ui.common.ChoiceRow
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.DurationPickerModal
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.OpensScreenMark
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.currencyLabel
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.languageLabel
import com.seblful.klas.ui.common.launchExternally
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.rememberRejectFeedback
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.theme.dynamicColorAvailable
import dev.zacsweers.metrox.viewmodel.metroViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = metroViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val reminderDelivery by viewModel.reminderDelivery.collectAsState()
    val calendarDelivery by viewModel.calendarDelivery.collectAsState()
    val pendingSweep by viewModel.pendingSweep.collectAsState()
    val messages = rememberMessages()
    val deniedMsg = stringResource(R.string.calendar_denied)
    val notificationsDeniedMsg = stringResource(R.string.settings_notifications_denied)
    val openSettingsLabel = stringResource(R.string.action_open_settings)
    val noSettingsPageMsg = stringResource(R.string.settings_page_missing)

    // The count comes with the event, so the plural is resolved from the context in the lambda rather than
    // read above it as the other screens do — there is no state here to pre-resolve it from. This is the
    // *provided* context, in the app's own language (`ProvideLocalizedResources`), which is what makes the
    // quantity rule the right one: a plural's form comes from the configuration's locale and not from the
    // folder it was loaded from. The language can be changed on this very screen; `ShowMessagesFor` uses the
    // newest lambda, so the next count is worded in the language now in force rather than the one just left.
    val context = LocalContext.current
    val resources = LocalResources.current
    ShowMessagesFor(viewModel.swept, messages) { count ->
        resources.getQuantityString(R.plurals.settings_autocomplete_swept, count, count)
    }
    val failedMsg = stringResource(R.string.action_failed)
    ShowMessagesFor(viewModel.failed, messages) { failedMsg }

    // A permission can be granted or revoked in system settings while the app is away, so the screen
    // re-asks each time it comes back rather than trusting what it learned when it opened.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshPermissions()
        }
    }

    // Said in the one place a refusal is: both the app's own (calendar sync without its permission) and
    // the platform's (no reminders will be shown).
    val rejectFeedback = rememberRejectFeedback()

    /** ays a reminder cannot appear — **and carries the one place that can be changed.** */
    val reportBlocked: () -> Unit = {
        // A refusal by the platform, so it is felt like the app's own — the tutor asked for reminders and
        // will not get them, which is exactly the class of answer worth knowing without reading.
        rejectFeedback()
        messages.show(notificationsDeniedMsg, openSettingsLabel) {
            launchExternally(context, appNotificationSettings(context), noSettingsPageMsg, messages)
        }
    }

    val requestNotifications = rememberNotificationPermissionRequest { granted ->
        viewModel.refreshPermissions()
        if (!granted) reportBlocked()
    }

    // Requesting the permission is all this screen does about it — whether sync may be enabled is
    // the view model's rule, so asking again after a grant simply goes back through it.
    val requestCalendar = rememberCalendarPermissionRequest {
        if (viewModel.calendarPermissionAnswered() != CalendarSyncToggle.APPLIED) {
            // The one switch in this screen that can be **refused**, and the refusal is felt as well as
            // read: the tutor asked for sync, the permission was declined, and the switch springs back on
            // its own. `Reject` is what says that was an answer rather than a dropped tap.
            //
            // With the page that can still change it: once the tutor has said «Не спрашивать», the
            // request is refused before anything is shown, and the switch alone read as broken.
            rejectFeedback()
            messages.show(deniedMsg, openSettingsLabel) {
                launchExternally(context, appDetailsSettings(context), noSettingsPageMsg, messages)
            }
        }
    }

    // The currency the tutor picked but has not yet confirmed — null while nothing is being asked.
    // Held rather than applied, because switching it re-labels every amount already recorded.
    var pendingCurrency by remember { mutableStateOf<Currency?>(null) }

    KlasScreen(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        messages = messages,
    ) { modifier ->
        // **No row here can be drawn from a default**, because the defaults are somebody else's settings:
        // a fresh `AppSettings` has reminders on, sync off, BYN and hour-long lessons, so until the row
        // arrives this screen showed the tutor a switch position that was not theirs — and then flipped it,
        // which reads as the app doing something rather than as the app catching up. See [Loadable].
        when (val loaded = state) {
            Loadable.Loading -> ListSkeleton(modifier, rows = 7, rowHeight = SettingsRowHeight)
            is Loadable.Ready -> SettingsList(
                settings = loaded.value,
                reminderDelivery = reminderDelivery,
                calendarDelivery = calendarDelivery,
                viewModel = viewModel,
                onRequestNotifications = requestNotifications,
                onNotificationsBlocked = reportBlocked,
                // The way out of «Уведомления запрещены», which for a long time was a sentence and nothing
                // else. Which of the two things a tap owes is the view model's — ask the platform, or send
                // the tutor to the one page that can undo it. Straight to that page rather than through
                // [reportBlocked]'s snackbar: this row *is* the tutor asking to fix it, and answering a tap
                // with a message offering another tap is the dead end one layer further out.
                onFixNotifications = {
                    when (viewModel.unblockNotifications()) {
                        NotificationsIntent.Request -> requestNotifications()
                        NotificationsIntent.BlockedBySystem ->
                            launchExternally(context, appNotificationSettings(context), noSettingsPageMsg, messages)
                        NotificationsIntent.Nothing -> Unit
                    }
                },
                onRequestCalendar = requestCalendar,
                onFixExactAlarms = {
                    launchExternally(context, exactAlarmSettings(context), noSettingsPageMsg, messages)
                },
                onPickCurrency = { picked -> pendingCurrency = picked },
                onDiagnostics = onDiagnostics,
                modifier = modifier,
            )
        }
    }

    pendingCurrency?.let { code ->
        ConfirmDialog(
            text = stringResource(R.string.settings_currency_change_confirm, code.name),
            confirmLabel = stringResource(R.string.settings_currency_change),
            onConfirm = { viewModel.update { it.copy(currency = code) } },
            onDismiss = { pendingCurrency = null },
        )
    }
    // Held in the view model rather than here, unlike the currency above: what the question is *about*
    // — how many finished lessons are waiting — has to be counted before it can be asked.
    pendingSweep?.let { backlog ->
        ConfirmDialog(
            text = pluralStringResource(
                R.plurals.settings_autocomplete_sweep_confirm,
                backlog,
                backlog,
            ),
            confirmLabel = stringResource(R.string.action_enable),
            onConfirm = { viewModel.confirmPendingSweep() },
            onDismiss = { viewModel.dismissPendingSweep() },
        )
    }
}

@Composable
private fun SettingsList(
    settings: AppSettings,
    reminderDelivery: ReminderDelivery,
    calendarDelivery: CalendarDelivery,
    viewModel: SettingsViewModel,
    onRequestNotifications: () -> Unit,
    onNotificationsBlocked: () -> Unit,
    onFixNotifications: () -> Unit,
    onRequestCalendar: () -> Unit,
    onFixExactAlarms: () -> Unit,
    onPickCurrency: (Currency) -> Unit,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Whether the custom-lead picker is up. Held here rather than hoisted: nothing outside this list
    // needs to know, and the value it writes goes straight to the settings row.
    var showLeadPicker by remember { mutableStateOf(false) }
    if (showLeadPicker) {
        DurationPickerModal(
            initialMinutes = settings.reminderLeadMinutes,
            title = stringResource(R.string.settings_reminder_lead),
            onConfirm = { minutes ->
                viewModel.update { it.copy(reminderLeadMinutes = minutes) }
                showLeadPicker = false
            },
            onDismiss = { showLeadPicker = false },
        )
    }
    var showLessonPicker by remember { mutableStateOf(false) }
    if (showLessonPicker) {
        DurationPickerModal(
            initialMinutes = settings.defaultLessonMinutes,
            title = stringResource(R.string.settings_default_lesson),
            onConfirm = { minutes ->
                viewModel.update { it.copy(defaultLessonMinutes = minutes) }
                showLessonPicker = false
            },
            onDismiss = { showLessonPicker = false },
        )
    }
    // What a switch that notifies owes the platform — reminders' and the three digests' alike.
    val askFor: (NotificationsIntent) -> Unit = { intent ->
        when (intent) {
            NotificationsIntent.Request -> onRequestNotifications()
            NotificationsIntent.BlockedBySystem -> onNotificationsBlocked()
            NotificationsIntent.Nothing -> Unit
        }
    }
    Column(modifier.verticalScroll(rememberScrollState())) {
        // Grouped by what each setting governs, so a glance tells you where to look:
        // reminders, then calendar sync, class behaviour, money, the menu, and look.
        SectionHeader(stringResource(R.string.settings_section_reminders))
        SwitchRow(
            title = stringResource(R.string.settings_notifications),
            // The row says what the platform will actually do, not only what the tutor asked for —
            // and there are **two** ways those can differ, not one: nothing may be posted at all, or
            // nothing may be posted *on time*. Which of them it is is [ReminderDelivery]'s answer;
            // this screen holds only the three sentences.
            description = when (reminderDelivery) {
                ReminderDelivery.BLOCKED -> stringResource(R.string.settings_notifications_blocked)
                ReminderDelivery.LATE -> stringResource(R.string.settings_notifications_inexact)
                ReminderDelivery.OFF, ReminderDelivery.ON_TIME ->
                    stringResource(R.string.settings_notifications_desc)
            },
            checked = settings.notificationsEnabled,
            onCheckedChange = { on ->
                // One call: it stores the setting *and* says what that owes the platform. Two calls the
                // screen had to make in order over the same argument is how either half could be left out
                // — see [SettingsViewModel.setNotificationsEnabled]. Asked here, where the tutor has just
                // said they want reminders, rather than over the first screen of a first launch, before
                // they had met the feature. Which of the three things to do is [NotificationsIntent]'s.
                askFor(viewModel.setNotificationsEnabled(on))
            },
        )
        // **One row, one label, whichever of the two grants the platform is withholding.**
        //
        // A reminder needs two permissions and they fail differently, but that is a fact about Android
        // and not about the tutor's morning: what they have is reminders that do not work, and what they
        // want is one thing to tap. The two states are mutually exclusive anyway — `ReminderDelivery.of`
        // stacks the checks, so a reminder nothing can post never also reports being late — so only one
        // of these was ever on screen. What changed was its wording, which made one control read as two.
        // The title is now fixed and only the line underneath says which problem it is.
        //
        // One tap cannot fix both, and that is the platform's doing: the grants live on different system
        // pages and Android publishes no combined one. When both are missing this fixes the notification
        // first — the one that decides whether anything appears at all — and becomes the punctuality row
        // on return, which is the order `of` already puts them in.
        //
        // **It is also the only path that ever asks for `POST_NOTIFICATIONS` in Settings.** The setting is
        // on by default and the permission is not, so the switch — the previous sole caller — could never
        // fire the request: it was already on, and turning on what is on is not a change. Reminders read
        // «включено» while every one of them was dropped, for good.
        //
        // A row of its own rather than more words under the switch, because a report the tutor cannot act
        // on is a better-worded silence — and a `SwitchRow` toggles when tapped anywhere, so it cannot
        // also be a link.
        val reminderProblem: Pair<Int, () -> Unit>? = when (reminderDelivery) {
            ReminderDelivery.BLOCKED -> R.string.settings_reminders_fix_blocked to onFixNotifications
            ReminderDelivery.LATE -> R.string.settings_reminders_fix_late to onFixExactAlarms
            // Nothing to fix, or nothing the tutor asked for — see the OFF arm of [ReminderDelivery].
            ReminderDelivery.OFF, ReminderDelivery.ON_TIME -> null
        }
        reminderProblem?.let { (descriptionRes, fix) ->
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reminders_fix)) },
                supportingContent = { Text(stringResource(descriptionRes)) },
                // Where the tap goes, at the trailing edge, rather than a gear in front. A leading icon on
                // two rows of thirty pushed exactly those two titles off the column every other row's
                // title starts on; the trailing mark says the one thing this row has that its neighbours
                // do not — it leaves the app — and says it the same way whichever problem it fixes.
                trailingContent = { LeavesAppMark() },
                modifier = Modifier.insetRowPress { Modifier.clickable(it, indication = null, onClick = fix) },
            )
        }
        // Three presets and a way out of them. A tutor who wants twenty minutes, or three hours to cross
        // town, had no way to say so — see [DurationPresetRow].
        DurationPresetRow(
            title = stringResource(R.string.settings_reminder_lead),
            presets = LeadPresets,
            minutes = settings.reminderLeadMinutes,
            customWord = stringResource(R.string.settings_reminder_lead_custom),
            onSelect = { minutes -> viewModel.update { it.copy(reminderLeadMinutes = minutes) } },
            onCustom = { showLeadPicker = true },
        )

        // Its own section, not more rows under «Напоминания»: a reminder is about one lesson and
        // arrives because that lesson is about to start, while a digest is a summary the tutor asked to
        // be sent at an hour they chose. All three switches are off by default — the app sends nothing
        // unasked that it was not already sending.
        SectionHeader(stringResource(R.string.settings_section_digests))
        SwitchRow(
            title = stringResource(R.string.settings_agenda),
            description = stringResource(R.string.settings_agenda_desc),
            checked = settings.dailyAgendaEnabled,
            onCheckedChange = { on -> askFor(viewModel.setDigestEnabled(on) { it.copy(dailyAgendaEnabled = on) }) },
        )
        TimeRow(
            title = stringResource(R.string.settings_agenda_time),
            minuteOfDay = settings.agendaMinuteOfDay,
            // Dimmed rather than hidden while the digest is off, as the invitation switch is: a row
            // that comes and goes makes the section a different shape every time it is opened, and the
            // time is worth seeing before deciding whether to switch the thing on.
            enabled = settings.dailyAgendaEnabled,
            onSet = { minutes -> viewModel.update { it.copy(agendaMinuteOfDay = minutes) } },
        )
        // Between the two, in the order of the day rather than of the feature: morning agenda, evening
        // wrap-up, then the weekly money one. It is the counterpart of «Автозавершение» two sections up
        // — with that on there is nothing left unmarked for this to name — and the description says so,
        // because a tutor with both on and nothing arriving should be able to see why.
        SwitchRow(
            title = stringResource(R.string.settings_unfinished_digest),
            description = stringResource(R.string.settings_unfinished_digest_desc),
            checked = settings.unfinishedDigestEnabled,
            onCheckedChange = { on ->
                askFor(viewModel.setDigestEnabled(on) { it.copy(unfinishedDigestEnabled = on) })
            },
        )
        TimeRow(
            title = stringResource(R.string.settings_unfinished_digest_time),
            minuteOfDay = settings.unfinishedMinuteOfDay,
            enabled = settings.unfinishedDigestEnabled,
            onSet = { minutes -> viewModel.update { it.copy(unfinishedMinuteOfDay = minutes) } },
        )
        SwitchRow(
            title = stringResource(R.string.settings_debt_digest),
            description = stringResource(R.string.settings_debt_digest_desc),
            checked = settings.debtDigestEnabled,
            onCheckedChange = { on -> askFor(viewModel.setDigestEnabled(on) { it.copy(debtDigestEnabled = on) }) },
        )
        WeekdayRow(
            title = stringResource(R.string.settings_debt_digest_day),
            dayOfWeek = settings.debtDayOfWeek,
            enabled = settings.debtDigestEnabled,
            onSet = { day -> viewModel.update { it.copy(debtDayOfWeek = day) } },
        )
        TimeRow(
            title = stringResource(R.string.settings_debt_digest_time),
            minuteOfDay = settings.debtMinuteOfDay,
            enabled = settings.debtDigestEnabled,
            onSet = { minutes -> viewModel.update { it.copy(debtMinuteOfDay = minutes) } },
        )

        SectionHeader(stringResource(R.string.settings_section_calendar))
        SwitchRow(
            title = stringResource(R.string.settings_calendar_sync),
            // As the reminder row above: what the platform will actually do, not only what the tutor
            // asked for. The calendar permission is revoked from the same place and with the same
            // effect — sync switched on, nothing mirrored, nothing anywhere saying so.
            description = when (calendarDelivery) {
                CalendarDelivery.BLOCKED -> stringResource(R.string.settings_calendar_sync_blocked)
                CalendarDelivery.OFF, CalendarDelivery.ON -> stringResource(R.string.settings_calendar_sync_desc)
            },
            checked = settings.calendarSyncEnabled,
            onCheckedChange = { on ->
                if (viewModel.setCalendarSyncEnabled(on) == CalendarSyncToggle.PERMISSION_NEEDED) {
                    onRequestCalendar()
                }
            },
        )
        SwitchRow(
            title = stringResource(R.string.settings_email_invites),
            description = stringResource(R.string.settings_email_invites_desc),
            checked = settings.emailInvitesEnabled,
            // The invitation rides on the class's calendar event, so this switch can only do
            // anything while sync is on — shown dimmed rather than hidden, so the tutor can see
            // that syncing alone mails nobody, and where to opt in if they want it to.
            enabled = settings.calendarSyncEnabled,
            onCheckedChange = { on -> viewModel.update { it.copy(emailInvitesEnabled = on) } },
        )

        SectionHeader(stringResource(R.string.settings_section_classes))
        SwitchRow(
            title = stringResource(R.string.settings_autocomplete),
            description = stringResource(R.string.settings_autocomplete_desc),
            checked = settings.autoCompleteEnabled,
            // Not the generic `update`: turning this on settles — and bills — every lesson that has
            // already finished, so whether that needs asking about first is the view model's to
            // decide. It answers through [SettingsViewModel.pendingSweep], below.
            onCheckedChange = { on -> viewModel.setAutoCompleteEnabled(on) },
        )
        // The same way out the reminder lead has, and for the same reason: a few guessed lengths are not
        // every tutor's lesson. A 50-minute academic hour and an 80-minute double were both unsayable.
        //
        // Three presets, not four — «2 ч» gave up its place to «Другая», which reaches it in two taps
        // and everything else besides. That keeps the row inside `MaxSegments` and drawn as segments,
        // so both duration settings look and behave alike instead of one being a strip and the other a
        // dropdown.
        DurationPresetRow(
            title = stringResource(R.string.settings_default_lesson),
            presets = LessonPresets,
            minutes = settings.defaultLessonMinutes,
            // The booking form's word for the same choice, so «Длительность» agrees with its noun in both
            // places rather than borrowing the reminder's «Другое».
            customWord = stringResource(R.string.class_duration_custom),
            onSelect = { minutes -> viewModel.update { it.copy(defaultLessonMinutes = minutes) } },
            onCustom = { showLessonPicker = true },
        )

        SectionHeader(stringResource(R.string.settings_section_money))
        // Asks first, because the change is a **re-labelling and not a conversion**: money is
        // stored as minor units and the code is only what gets printed beside them, so 100 BYN of
        // recorded history begins reading as 100 RUB. That is the right behaviour for an offline
        // app — inventing an exchange rate would be worse, and rewriting billed history worse
        // still — but it is not what "сменить валюту" leads a tutor to expect, so it is said out
        // loud before every stored amount changes meaning.
        ChoiceRow(
            title = stringResource(R.string.settings_currency),
            // The legal set is [Currency]'s, not this screen's — the row only words it.
            options = Currency.entries.map { it to currencyLabel(it) },
            selected = settings.currency,
            onSelect = { picked -> if (picked != settings.currency) onPickCurrency(picked) },
        )
        // Free text rather than a segmented control: unlike a lesson length or a currency, there
        // is no shortlist of plausible amounts to offer.
        MoneyRow(
            title = stringResource(R.string.settings_default_group_price),
            description = stringResource(R.string.settings_default_group_price_desc),
            amount = settings.defaultGroupPrice,
            onSet = { price -> viewModel.update { it.copy(defaultGroupPrice = price) } },
        )

        SectionHeader(stringResource(R.string.settings_section_appearance))
        // **First in the section**, because it is what a tutor comes here looking for, and it governs more
        // than the section's name suggests: the words on every screen, in every notification and in a
        // mirrored calendar event's location. Which is why it is one of the settings that owes something on
        // the way out — `SettingsChange.calendarWork` re-mirrors the events that still carry the old
        // language, and [update] is what discharges it.
        //
        // Applied without asking, unlike the currency below: a language changes the words around the
        // numbers, where a currency changes what the numbers claim to be.
        ChoiceRow(
            title = stringResource(R.string.settings_language),
            // The legal set is [AppLanguage]'s and the wording is [languageLabel]'s; the row only places it.
            options = AppLanguage.entries.map { it to languageLabel(it) },
            selected = settings.language,
            onSelect = { picked ->
                if (picked != settings.language) viewModel.update { it.copy(language = picked) }
            },
        )
        ChoiceRow(
            title = stringResource(R.string.settings_theme),
            options = listOf(
                ThemeChoice.SYSTEM to stringResource(R.string.theme_system),
                ThemeChoice.LIGHT to stringResource(R.string.theme_light),
                ThemeChoice.DARK to stringResource(R.string.theme_dark),
            ),
            selected = settings.themeMode,
            onSelect = { mode -> viewModel.update { it.copy(themeMode = mode) } },
        )
        // Hidden rather than disabled below Android 12, which is the opposite of what the two rows
        // above do — and for the opposite reason. A blocked reminder or a dimmed invitation switch
        // is a condition the tutor can *change*, so saying so is help; an OS that has no Material
        // You is not, and a permanently dead row would only ever be clutter with an explanation
        // attached. `dynamicColorAvailable` is the same answer the theme itself acts on.
        if (dynamicColorAvailable) {
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                description = stringResource(R.string.settings_dynamic_color_desc),
                checked = settings.dynamicColorEnabled,
                onCheckedChange = { on ->
                    viewModel.update { it.copy(dynamicColorEnabled = on) }
                },
            )
        }

        // Last, and deliberately dull. Diagnostics used to stand in «Ещё» beside «Настройки» and
        // «Резервная копия», which put a crash-log viewer at the same level as the things a tutor
        // uses. It belongs where service tools belong: at the bottom of settings.
        SectionHeader(stringResource(R.string.settings_section_service))
        ListItem(
            headlineContent = { Text(stringResource(R.string.diagnostics_title)) },
            supportingContent = { Text(stringResource(R.string.diagnostics_desc)) },
            // A chevron, as «Ещё» marks a row that opens a screen — not a leading bug glyph that set
            // this one title in from all the others above it.
            trailingContent = { OpensScreenMark() },
            modifier = Modifier.insetRowPress { Modifier.clickable(it, indication = null, onClick = onDiagnostics) },
        )
    }
}
