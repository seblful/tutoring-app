package com.seblful.klas.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.seblful.klas.notifications.ACTION_VIEW
import com.seblful.klas.notifications.TrayNavigation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@Composable
internal actual fun PlatformArrivals(arrive: (TrayNavigation.Destination?) -> Unit) {
    val current by rememberUpdatedState(arrive)
    LaunchedEffect(Unit) { IosArrivals.destinations.receiveAsFlow().collect { current(it) } }
}

/**
 * Where the iOS host says the app was asked to open: a notification's tap or a `klas://` link. Buffered, so
 * one that arrives before the screens are up (a cold start from a notification) is answered once they are.
 */
object IosArrivals {
    internal val destinations = Channel<TrayNavigation.Destination?>(Channel.BUFFERED)

    /** A `klas://` link the system opened the app with. */
    fun openLink(link: String) {
        destinations.trySend(TrayNavigation.destinationFor(action = ACTION_VIEW, classId = 0L, link = link))
    }

    /** A notification's tap: its action, and the lesson or day it carries (0 / null when none). */
    fun openNotification(action: String, classId: Long, epochDay: Long?) {
        destinations.trySend(TrayNavigation.destinationFor(action = action, classId = classId, epochDay = epochDay))
    }
}
