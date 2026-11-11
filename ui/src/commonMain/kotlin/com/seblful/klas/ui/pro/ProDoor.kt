package com.seblful.klas.ui.pro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.pro.Access
import com.seblful.klas.domain.pro.ProFeature
import com.seblful.klas.domain.pro.ProGate
import com.seblful.klas.domain.pro.ProOffer
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.pro.ProAccess
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.common.state.hold
import com.seblful.klas.ui.navigation.KlasNavigator
import com.seblful.klas.ui.navigation.Routes
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * What a screen asks before a Klas Pro feature: whether the tutor may, and the way to the Klas Pro screen when
 * they may not. One for the whole app ([LocalPro]), so no screen carries Pro through its parameters.
 *
 * @param gate what the tutor may do; null until it has been read.
 */
@Stable
class ProDoor(
    private val gate: () -> ProGate?,
    private val open: (ProFeature?) -> Unit,
    private val offered: () -> Boolean = { false },
) {

    /** Whether the bar may show a free tutor Klas Pro's icon — see [ProOffer]. */
    val offersPro: Boolean get() = access == Access.Free && offered()

    /** Whether the tutor has Pro, and why; null until read. */
    val access: Access? get() = gate()?.access

    /** Whether [feature] is the tutor's — true while not yet read, so nothing flashes locked for a Pro tutor. */
    fun allows(feature: ProFeature): Boolean = gate()?.allows(feature) ?: true

    /** Whether [feature] is the tutor's; null until known. */
    fun decided(feature: ProFeature): Boolean? = gate()?.allows(feature)

    /** Runs [action] when [feature] is the tutor's; otherwise opens the Klas Pro screen for it. */
    fun guard(feature: ProFeature, action: () -> Unit) {
        if (allows(feature)) action() else open(feature)
    }

    /** The Klas Pro screen, leading with [feature] when it was opened for one. */
    fun open(feature: ProFeature?) = open.invoke(feature)

    companion object {
        /** Everything allowed: previews, tests, and a screen shown outside the app's root. */
        val Unlocked = ProDoor(gate = { null }, open = {})
    }
}

val LocalPro = staticCompositionLocalOf { ProDoor.Unlocked }

/** The root's reading of [ProAccess.gate], behind [LocalPro]. */
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ProGateViewModel @Inject constructor(
    pro: ProAccess,
    settings: SettingsRepository,
    clock: WallClock,
) : ViewModel() {
    val gate: StateFlow<ProGate?> = pro.gate.hold(this, null)

    /** Whether the bar's icon is past a new tutor's quiet first week ([ProOffer]). */
    val offered: StateFlow<Boolean> = settings.observe()
        .map { ProOffer.shown(it.setupAt, clock.now(), it.guide.firstStepsHidden) }
        .hold(this, false)
}

/**
 * A free tutor's way to Klas Pro on every tab's bar, just before Settings: one still icon, there and nothing
 * more — no badge, no count, nothing that moves or asks. Gone once the tutor has Pro.
 */
@Composable
fun ProAction() {
    val door = LocalPro.current
    if (!door.offersPro) return
    NamedIconButton(
        KlasIcons.Outlined.WorkspacePremium,
        stringResource(Strings.pro_get),
        onClick = { door.open(null) },
        tint = MaterialTheme.colorScheme.primary,
    )
}

/** The app's [ProDoor]: the root's reading of the gate, opening the Klas Pro screen on [navigator]. */
@Composable
fun rememberProDoor(navigator: KlasNavigator, viewModel: ProGateViewModel = metroViewModel()): ProDoor {
    val gate = viewModel.gate.collectAsStateWithLifecycle()
    val offered = viewModel.offered.collectAsStateWithLifecycle()
    return remember(navigator) {
        ProDoor(gate = { gate.value }, open = { navigator.navigate(Routes.Pro(it)) }, offered = { offered.value })
    }
}

/**
 * [content] when [feature] is the tutor's as the screen opens, else the Klas Pro screen in its place — which
 * turns into [content] once bought. Decided once: the form a free tutor opened with room for one more student
 * stays open when saving it fills the room.
 */
@Composable
fun ProGated(feature: ProFeature, onBack: () -> Unit, content: @Composable () -> Unit) {
    val door = LocalPro.current
    var entered by rememberSaveable { mutableStateOf(false) }
    val allowed = door.decided(feature)
    if (allowed == true && !entered) SideEffect { entered = true }
    when {
        entered || allowed == true -> content()
        allowed == false -> ProScreen(feature = feature, onBack = onBack)
        // Read in a frame or two; nothing is shown rather than the wrong screen.
        else -> Box(Modifier.fillMaxSize())
    }
}
