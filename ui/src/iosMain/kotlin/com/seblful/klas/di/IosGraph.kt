package com.seblful.klas.di

import com.seblful.klas.diagnostics.CrashLogger
import com.seblful.klas.notifications.IosNotificationPermission
import com.seblful.klas.ui.AppSession
import com.seblful.klas.widget.IosHomeScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.CoroutineScope

/**
 * The iOS app's graph: every binding contributed to [AppScope] in the shared modules and the screens, plus
 * the iOS adapters in `iosMain`. The counterpart of Android's `KlasGraph`; one per process, owned by
 * `KlasIos`.
 */
@DependencyGraph(AppScope::class)
interface IosGraph : ViewModelGraph {
    val crashLogger: CrashLogger

    /** Read again each time the app comes to the front: the Settings app may have changed it. */
    val notificationPermission: IosNotificationPermission

    /** The Home Screen widgets. */
    val homeScreen: IosHomeScreen

    /** The app coming and going: its lock, and what the settings ask of the window and the icon. */
    val session: AppSession

    @ApplicationScope
    val applicationScope: CoroutineScope
}
