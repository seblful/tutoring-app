package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.data.settings.GuideRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.domain.guide.Tip
import com.seblful.klas.ui.common.state.hold
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The tips this phone has seen, for whichever screen has one to show — see [rememberScreenTip]. */
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class GuideViewModel @Inject constructor(
    private val guide: GuideRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    /** Null until read: a tip must not open for the moment before the row says it was seen long ago. */
    val seen: StateFlow<Set<Tip>?> = guide.guide.map<_, Set<Tip>?> { it.seen }.hold(this, null)

    fun seen(tip: Tip) {
        appScope.launch { guide.seen(tip) }
    }

    /** Settings' «Show tips again». */
    fun showAgain() {
        appScope.launch { guide.showAgain() }
    }
}

/**
 * The tip a screen shows now, if any.
 *
 * @property tip the one to show; null when there is none, or something is in front of the screen.
 * @property dismiss the tutor has read [tip].
 * @property done the tutor has made [Tip]'s gesture, which teaches it better than the tip: it is not shown after.
 */
@Immutable
class ScreenTip(val tip: Tip?, val dismiss: () -> Unit, val done: (Tip) -> Unit) {
    companion object {
        val NONE = ScreenTip(null, {}, {})
    }
}

/**
 * **One tip per visit to a screen.** [pick] chooses among those not yet seen; once one has been read, the next
 * waits until the screen is left and come back to, so tips never follow one another like a tour. Nothing opens
 * while [blocked] — a sheet, a dialog, a selection in front of what the tip is about.
 */
@Composable
fun rememberScreenTip(
    blocked: Boolean,
    viewModel: GuideViewModel = metroViewModel(),
    pick: (seen: Set<Tip>) -> Tip?,
): ScreenTip {
    val seen by viewModel.seen.collectAsStateWithLifecycle()
    // Remembered rather than saved: a screen leaving composition is a visit ending.
    var read by remember { mutableStateOf(false) }
    val tip = seen?.takeUnless { read || blocked }?.let(pick)
    // Read at the gesture, so a swipe made long after the tutor saw its tip writes nothing.
    val latestSeen by rememberUpdatedState(seen)
    return remember(tip, viewModel) {
        ScreenTip(
            tip = tip,
            dismiss = {
                if (tip != null) viewModel.seen(tip)
                read = true
            },
            done = { made ->
                if (made == tip) read = true
                if (latestSeen?.contains(made) == false) viewModel.seen(made)
            },
        )
    }
}
