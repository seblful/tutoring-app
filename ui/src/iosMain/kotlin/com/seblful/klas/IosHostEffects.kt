package com.seblful.klas

import com.seblful.klas.notifications.Shortcuts
import com.seblful.klas.text.Texts
import com.seblful.klas.ui.HostEffects
import com.seblful.klas.widget.sfSymbol
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationShortcutIcon
import platform.UIKit.UIApplicationShortcutItem
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.shortcutItems
import platform.UIKit.systemBackgroundColor

/** [HostEffects] for iOS, on the main thread: the app's window and the quick actions on its icon. */
internal object IosHostEffects : HostEffects {

    /** Whether the window is covered while the app is inactive, as Android blanks its recent-apps preview. */
    private var hide = false

    private var cover: UIView? = null

    /**
     * Covers the window as the app resigns active and uncovers it as it returns. iOS takes the switcher's
     * picture once the app is inactive, so the cover is what that picture shows. Pulling down Control
     * Center or the Face ID prompt resigns active too, and shows the cover briefly — the usual price.
     */
    fun install() {
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
            if (hide) coverWindow()
        }
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
            cover?.removeFromSuperview()
            cover = null
        }
    }

    override fun hideFromSwitcher(hide: Boolean) {
        this.hide = hide
    }

    /** The long-press menu on the app's icon, in the app's language: the same entries as Android's launcher. */
    override fun publishShortcuts(texts: Texts) {
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

    @OptIn(ExperimentalForeignApi::class)
    private fun coverWindow() {
        if (cover != null) return
        val window = keyWindow() ?: return
        cover = UIView(frame = window.bounds).apply {
            backgroundColor = UIColor.systemBackgroundColor
            autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        }.also(window::addSubview)
    }

    private fun keyWindow(): UIWindow? = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { it.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { it.keyWindow }
}
