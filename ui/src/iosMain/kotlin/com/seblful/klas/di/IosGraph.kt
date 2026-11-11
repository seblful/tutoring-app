package com.seblful.klas.di

import com.seblful.klas.diagnostics.CrashLogger
import com.seblful.klas.notifications.IosNotificationPermission
import com.seblful.klas.widget.IosHomeScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

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

    /** The widgets and the quick actions on the app's icon. */
    val homeScreen: IosHomeScreen
}
