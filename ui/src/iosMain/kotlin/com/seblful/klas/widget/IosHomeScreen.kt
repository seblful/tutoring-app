package com.seblful.klas.widget

import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Io
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.notifications.ShortcutIcon
import com.seblful.klas.notifications.Shortcuts
import com.seblful.klas.queries.lessons.NamedLesson
import com.seblful.klas.queries.lessons.TodayLessonsQuery
import com.seblful.klas.storage.writeBytes
import com.seblful.klas.text.Texts
import com.seblful.klas.ui.common.phoneHour12
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationShortcutIcon
import platform.UIKit.UIApplicationShortcutItem
import platform.UIKit.shortcutItems

/**
 * What the Home Screen shows of the app: the widgets (`iosApp/Widgets`) and the quick actions on the app's
 * icon. The counterpart of Android's `WidgetUpdates` and `AppShortcuts`.
 *
 * The WidgetKit extension cannot run this code, so the app writes it a [WidgetSnapshot] in the app group
 * they share, covering [WidgetSnapshots.DAYS_AHEAD] days, and asks WidgetKit to redraw. Written again on every
 * change to those days' lessons, the language and the lock-screen choice, and as the app comes to the front.
 */
@SingleIn(AppScope::class)
@Inject
class IosHomeScreen(
    private val lessons: TodayLessonsQuery,
    private val displayLocale: DisplayLocale,
    private val settings: SettingsRepository,
    private val clock: WallClock,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:Io private val io: CoroutineDispatcher,
) {
    /** The phone's 12/24-hour switch, which the Settings app changes while the app is away. */
    private val hour12 = MutableStateFlow(phoneHour12())

    /** Starts keeping both current. */
    fun start(widgets: WidgetReloads) {
        scope.launch {
            snapshots().collect { snapshot ->
                if (write(snapshot)) withContext(Dispatchers.Main) { widgets.reloadAll() }
            }
        }
        scope.launch(Dispatchers.Main) {
            displayLocale.observe().collect { tag -> publishQuickActions(Texts.of(tag)) }
        }
    }

    fun foregrounded() {
        hour12.value = phoneHour12()
    }

    private fun snapshots() = combine(
        lessons.observeDays(WidgetSnapshots.DAYS_AHEAD),
        displayLocale.observe(),
        settings.observe().map { it.privacy }.distinctUntilChanged(),
        hour12,
    ) { days, tag, privacy, hour12 ->
        WidgetSnapshots.build(
            days = days.map { it.day to it.lessons.map(NamedLesson::toTodayLesson) },
            zone = clock.zone,
            texts = Texts.of(tag),
            hour12 = hour12,
            privacy = privacy,
            icon = ShortcutIcon::sfSymbol,
        )
    }.distinctUntilChanged()

    /** False when there is no app group to write to: a build signed without it, as CI's simulator build is. */
    private suspend fun write(snapshot: WidgetSnapshot): Boolean = withContext(io) {
        val group = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP)
        val file = group?.URLByAppendingPathComponent(SNAPSHOT_FILE) ?: return@withContext false
        runCatching { file.writeBytes(snapshot.encode().encodeToByteArray()) }.isSuccess
    }

    /** The long-press menu on the app's icon, in the app's language: the same entries as Android's launcher. */
    private fun publishQuickActions(texts: Texts) {
        UIApplication.sharedApplication.shortcutItems = Shortcuts.launcher.map { shortcut ->
            UIApplicationShortcutItem(
                type = shortcut.link,
                localizedTitle = texts.string(shortcut.label),
                localizedSubtitle = null,
                icon = UIApplicationShortcutIcon.iconWithSystemImageName(shortcut.icon.sfSymbol),
                userInfo = null,
            )
        }
    }

    companion object {
        /** Shared with the extension; `iosApp/project.yml` grants it to both targets. */
        const val APP_GROUP = "group.com.seblful.klas"

        /** The extension's `WidgetSnapshot.swift` reads this file by the same name. */
        const val SNAPSHOT_FILE = "widget-snapshot.json"
    }
}

/** WidgetKit's reload, which only Swift can call: the host implements this over `WidgetCenter`. */
interface WidgetReloads {
    fun reloadAll()
}

/** The SF Symbol for [this] glyph, on the widget and on the app icon's quick actions alike. */
internal val ShortcutIcon.sfSymbol: String
    get() = when (this) {
        ShortcutIcon.BOOK -> "plus"
        ShortcutIcon.TODAY -> "calendar"
        ShortcutIcon.PEOPLE -> "person.2"
        ShortcutIcon.HOMEWORK -> "book.closed"
        ShortcutIcon.DEBTS -> "creditcard"
    }
