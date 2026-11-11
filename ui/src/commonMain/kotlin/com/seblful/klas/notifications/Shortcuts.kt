package com.seblful.klas.notifications

import com.seblful.klas.notifications.TrayNavigation.Destination
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings

/** The glyph a [Shortcut] is drawn with; each platform maps it to its own icon set. */
enum class ShortcutIcon { BOOK, TODAY, PEOPLE, HOMEWORK, DEBTS }

/** One way into the app from outside it, a launcher shortcut or a widget's button: its words and its place. */
data class Shortcut(val icon: ShortcutIcon, val label: StringResource, val destination: Destination) {
    /** Stable across releases: a launcher keeps the shortcuts a tutor pinned by it. */
    val id: String get() = icon.name.lowercase()

    /** How both platforms open it. */
    val link: String get() = TrayNavigation.linkFor(destination)
}

/** The ways in, listed once for both apps. */
object Shortcuts {

    /**
     * The long-press menu on the app's icon: the four things a tutor opens the app *to do*, each a
     * [TrayNavigation] destination, so it lands where the widget's button of the same name does. Booking first.
     */
    val launcher = listOf(
        Shortcut(ShortcutIcon.BOOK, Strings.schedule_add_class, Destination.NewLesson),
        Shortcut(ShortcutIcon.TODAY, Strings.nav_today, Destination.Today),
        Shortcut(ShortcutIcon.HOMEWORK, Strings.nav_homework, Destination.Homework),
        Shortcut(ShortcutIcon.DEBTS, Strings.reports_debts, Destination.Debts),
    )

    /** The quick-actions widget's prominent button. */
    val widgetHeader = Shortcut(ShortcutIcon.BOOK, Strings.schedule_add_class, Destination.NewLesson)

    /**
     * The quick-actions widget's other buttons. A payment is not here because it needs a student first, so
     * debts, whose rows open the student who owes, stand in for it.
     */
    val widget = listOf(
        Shortcut(ShortcutIcon.TODAY, Strings.schedule_today, Destination.Today),
        Shortcut(ShortcutIcon.PEOPLE, Strings.nav_people, Destination.People),
        Shortcut(ShortcutIcon.HOMEWORK, Strings.nav_homework, Destination.Homework),
        Shortcut(ShortcutIcon.DEBTS, Strings.debts_title, Destination.Debts),
    )
}
