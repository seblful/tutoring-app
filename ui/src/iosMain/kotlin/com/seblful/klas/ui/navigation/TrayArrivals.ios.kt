package com.seblful.klas.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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

    /** A `klas://` link: a notification's tap, a widget's button, a quick action or another app's. */
    fun openLink(link: String) {
        destinations.trySend(TrayNavigation.destinationForLink(link))
    }
}
