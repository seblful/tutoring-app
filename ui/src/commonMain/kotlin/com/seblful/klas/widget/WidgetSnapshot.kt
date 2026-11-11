package com.seblful.klas.widget

import com.seblful.klas.data.settings.Privacy
import com.seblful.klas.notifications.Shortcut
import com.seblful.klas.notifications.ShortcutIcon
import com.seblful.klas.notifications.Shortcuts
import com.seblful.klas.notifications.TrayNavigation
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything iOS's home-screen widgets show, written by the app for its WidgetKit extension
 * (`iosApp/Widgets`) to draw. Every word and every decision is made here, from the same rows Android's Glance
 * widgets draw; the extension, which cannot run the app's code, only lays it out.
 *
 * The extension's `WidgetSnapshot.swift` decodes this shape. Both sides are tested against
 * `iosApp/Tests/Fixtures/widget-snapshot.json`, so a field renamed on one side fails a build.
 */
@Serializable
data class WidgetSnapshot(val today: TodaySnapshot, val quickActions: QuickActionsSnapshot) {

    fun encode(): String = format.encodeToString(serializer(), this)

    companion object {
        /** Every field written, so the extension never meets a missing one. */
        val format = Json {
            encodeDefaults = true
            prettyPrint = true
        }

        fun decode(json: String): WidgetSnapshot = format.decodeFromString(serializer(), json)
    }
}

/** The Today widget: its words, and its pages over the days ahead. */
@Serializable
data class TodaySnapshot(
    val title: String,
    val empty: String,
    val onNow: String,
    val bookLesson: String,
    val bookLink: String,
    val openLink: String,
    /** Redacted on a locked phone unless the tutor chose to show lessons there ([Privacy.lockScreenWidgets]). */
    val privateWhenLocked: Boolean,
    /** In time order; each is what the widget says from its moment until the next one's. */
    val pages: List<TodayPage>,
)

@Serializable
data class TodayPage(
    /** From when, in seconds since the epoch. */
    val from: Long,
    val rows: List<WidgetRow>,
)

/** One lesson, as [TodayRow] states it, with where tapping it goes. */
@Serializable
data class WidgetRow(
    val title: String,
    val detail: String,
    val taught: Boolean,
    val onNow: Boolean,
    /** What VoiceOver says for the row's status: «Mark Anna, 09:00 taught». */
    val statusLabel: String,
    val link: String,
)

@Serializable
data class QuickActionsSnapshot(
    val appName: String,
    val header: WidgetButton,
    val buttons: List<WidgetButton>,
)

@Serializable
data class WidgetButton(
    val label: String,
    val link: String,
    /** The platform's own glyph name for it: an SF Symbol on iOS. */
    val icon: String,
)

/** Builds [WidgetSnapshot]s; a function of plain values, so what a widget says is tested without one. */
object WidgetSnapshots {

    /** How far ahead the Today widget is drawn: it keeps turning its pages for this long with the app closed. */
    const val DAYS_AHEAD = 7

    /**
     * The snapshot for [days], in order from today. The Today widget gets one page from each day's midnight
     * and one at each lesson's start and end, which is when «On now» moves.
     */
    fun build(
        days: List<Pair<LocalDate, List<TodayLesson>>>,
        zone: TimeZone,
        texts: Texts,
        hour12: Boolean,
        privacy: Privacy,
        icon: (ShortcutIcon) -> String,
    ): WidgetSnapshot {
        val rowTexts = texts.todayTexts()
        val pages = days.flatMap { (day, lessons) ->
            val midnight = day.atStartOfDayIn(zone)
            val nextMidnight = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            val moments = (listOf(midnight) + lessons.flatMap { listOf(it.start, it.end) })
                .filter { it >= midnight && it < nextMidnight }
                .distinct()
                .sorted()
            moments.map { moment ->
                TodayPage(
                    from = moment.epochSeconds,
                    rows = lessons.map { lesson ->
                        val row = lesson.toRow(hour12, texts.tag, moment, rowTexts)
                        WidgetRow(
                            title = row.title,
                            detail = row.supportingText,
                            taught = row.taught,
                            onNow = row.onNow,
                            statusLabel = row.tickLabel,
                            link = TrayNavigation.lessonLink(row.classId),
                        )
                    },
                )
            }
        }
        fun button(shortcut: Shortcut) = WidgetButton(texts.string(shortcut.label), shortcut.link, icon(shortcut.icon))
        return WidgetSnapshot(
            today = TodaySnapshot(
                title = rowTexts.title,
                empty = rowTexts.empty,
                onNow = rowTexts.onNow,
                bookLesson = rowTexts.bookLesson,
                bookLink = Shortcuts.widgetHeader.link,
                openLink = checkNotNull(TrayNavigation.linkFor(TrayNavigation.ACTION_OPEN_TODAY)),
                privateWhenLocked = !privacy.lockScreenWidgets,
                pages = pages,
            ),
            quickActions = QuickActionsSnapshot(
                appName = texts.string(Strings.app_name),
                header = button(Shortcuts.widgetHeader),
                buttons = Shortcuts.widget.map(::button),
            ),
        )
    }
}
