package com.seblful.klas.ui.pro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.pro.ProAccess
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.Messages
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ProEndedViewModel @Inject constructor(
    private val pro: ProAccess,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    val ended: Flow<Boolean> = pro.ended

    fun seen() {
        appScope.launch { pro.endedSeen() }
    }
}

/**
 * Says once, on Today, that Klas Pro has ended — otherwise calendar sync and the summaries going quiet would look
 * like a fault. Then never again, until Pro is bought and ends once more.
 */
@Composable
fun ProEndedNotice(messages: Messages, viewModel: ProEndedViewModel = metroViewModel()) {
    val door = LocalPro.current
    val text = stringResource(Strings.pro_ended)
    val see = stringResource(Strings.pro_see)
    LaunchedEffect(viewModel) {
        viewModel.ended.first { it }
        messages.show(text, see) { door.open(null) }
        viewModel.seen()
    }
}
