package com.seblful.klas

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.compose.LifecycleStartEffect
import com.seblful.klas.di.IosGraph
import com.seblful.klas.diagnostics.CrashLogger
import com.seblful.klas.lock.canUnlock
import com.seblful.klas.notifications.IosNotifier
import com.seblful.klas.ui.AppRoot
import com.seblful.klas.ui.RootViewModel
import com.seblful.klas.ui.common.LocalSearchKey
import com.seblful.klas.ui.common.SearchKey
import com.seblful.klas.ui.navigation.IosArrivals
import com.seblful.klas.widget.WidgetReloads
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.Foundation.NSNumber
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.UIKit.UIViewController
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.experimental.ExperimentalNativeApi

/**
 * What the Swift host calls: [start] once as the app launches, [MainViewController] for the window, and
 * [openLink] for a `klas://` URL, which is also how a quick action on the app's icon or a widget's button
 * arrives. The counterpart of Android's `KlasApp` and `MainActivity`.
 */
object KlasIos {

    /** The process's one graph, built by the first thing to need it. */
    internal val graph: IosGraph by lazy { createGraph<IosGraph>() }

    private val taps = NotificationTaps()

    /**
     * Before the first frame: crashes are recorded from here on, and a notification tapped while the app
     * was not running reaches [taps] — iOS hands it over only to a delegate set before launch finishes.
     * [widgets] is WidgetKit's reload, which only Swift can call.
     */
    fun start(widgets: WidgetReloads) {
        graph.crashLogger.install()
        UNUserNotificationCenter.currentNotificationCenter().delegate = taps
        graph.homeScreen.start(widgets)
        IosHostEffects.install()
        graph.applicationScope.launch(Dispatchers.Main) { graph.session.keepApplied(IosHostEffects) }
    }

    fun openLink(link: String) = IosArrivals.openLink(link)
}

/** The app's screens in one view controller, as `MainActivity` hosts them on Android. */
@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    val graph = KlasIos.graph
    val searchKey = remember { SearchKey() }
    CompositionLocalProvider(
        LocalMetroViewModelFactory provides graph.metroViewModelFactory,
        LocalSearchKey provides searchKey,
    ) {
        val root: RootViewModel = metroViewModel()
        // Coming to the front is when iOS's upkeep happens (see `IosScheduler`): AppRoot catches up as the
        // screens come up, and each later return does it again.
        val returns = remember { intArrayOf(0) }
        LifecycleStartEffect(root) {
            graph.notificationPermission.refresh()
            graph.homeScreen.foregrounded()
            if (returns[0]++ > 0) root.catchUp()
            graph.session.foregrounded(canUnlock())
            onStopOrDispose { graph.session.backgrounded() }
        }
        AppRoot(root, onUnlock = { title -> unlock(root, title) })
    }
}

/** Face ID, Touch ID or the passcode, as the system asks for it; [title] is why. */
private fun unlock(root: RootViewModel, title: String) {
    val lock = root.appLock
    lock.prompting(true)
    LAContext().evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = title) { success, _ ->
        dispatch_async(dispatch_get_main_queue()) { if (success) lock.unlocked() else lock.prompting(false) }
    }
}

/**
 * Records every uncaught Kotlin exception before the process ends — the crash still ends it, and the
 * system's own report still follows.
 */
@OptIn(ExperimentalNativeApi::class)
private fun CrashLogger.install() {
    var previous: ReportUnhandledExceptionHook? = null
    previous = setUnhandledExceptionHook { throwable ->
        runCatching { record(throwable) }
        previous?.invoke(throwable)
    }
}

/**
 * A tap on one of the app's notifications goes where the same tap goes on Android ([IosArrivals]); one
 * arriving while the app is open still shows, as a banner.
 */
private class NotificationTaps : NSObject(), UNUserNotificationCenterDelegateProtocol {

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val info = didReceiveNotificationResponse.notification.request.content.userInfo
        (info[IosNotifier.KEY_ACTION] as? String)?.let { action ->
            IosArrivals.openNotification(
                action = action,
                classId = (info[IosNotifier.KEY_CLASS_ID] as? NSNumber)?.longLongValue ?: 0L,
                epochDay = (info[IosNotifier.KEY_DAY] as? NSNumber)?.longLongValue,
            )
        }
        withCompletionHandler()
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(
            UNNotificationPresentationOptionBanner or
                UNNotificationPresentationOptionList or
                UNNotificationPresentationOptionSound,
        )
    }
}
