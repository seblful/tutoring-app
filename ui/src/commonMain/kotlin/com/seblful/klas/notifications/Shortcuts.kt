package com.seblful.klas.notifications

import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings

/** The glyph a [Shortcut] is drawn with; each platform maps it to its own icon set. */
enum class ShortcutIcon { BOOK, TODAY, PEOPLE, HOMEWORK, DEBTS }

/** One way into the app from outside it, a launcher shortcut or a widget's button: its words and its place. */
data class Shortcut(val icon: ShortcutIcon, val label: StringResource, val action: String) {
    /** Stable across releases: a launcher keeps the shortcuts a tutor pinned by it. */
    val id: String get() = icon.name.lowercase()

    /** The same place as a `klas://` link, which is how iOS opens it. */
    val link: String get() = checkNotNull(TrayNavigation.linkFor(action)) { "$action has no link" }
}

/** The ways in, listed once for both apps. */
object Shortcuts {

    /**
     * The long-press menu on the app's icon: the four things a tutor opens the app *to do*, each the tray's own
     * entry ([TrayNavigation]), so it lands where the widget's button of the same name does. Booking first.
     */
    val launcher = listOf(
        Shortcut(ShortcutIcon.BOOK, Strings.schedule_add_class, TrayNavigation.ACTION_BOOK_LESSON),
        Shortcut(ShortcutIcon.TODAY, Strings.nav_today, TrayNavigation.ACTION_OPEN_TODAY),
        Shortcut(ShortcutIcon.HOMEWORK, Strings.nav_homework, TrayNavigation.ACTION_OPEN_HOMEWORK),
        Shortcut(ShortcutIcon.DEBTS, Strings.reports_debts, TrayNavigation.ACTION_OPEN_DEBTS),
    )

    /** The quick-actions widget's prominent button. */
    val widgetHeader = Shortcut(ShortcutIcon.BOOK, Strings.schedule_add_class, TrayNavigation.ACTION_BOOK_LESSON)

    /**
     * The quick-actions widget's other buttons. A payment is not here because it needs a student first, so
     * debts, whose rows open the student who owes, stand in for it.
     */
    val widget = listOf(
        Shortcut(ShortcutIcon.TODAY, Strings.schedule_today, TrayNavigation.ACTION_OPEN_TODAY),
        Shortcut(ShortcutIcon.PEOPLE, Strings.nav_people, TrayNavigation.ACTION_OPEN_PEOPLE),
        Shortcut(ShortcutIcon.HOMEWORK, Strings.nav_homework, TrayNavigation.ACTION_OPEN_HOMEWORK),
        Shortcut(ShortcutIcon.DEBTS, Strings.debts_title, TrayNavigation.ACTION_OPEN_DEBTS),
    )
}
