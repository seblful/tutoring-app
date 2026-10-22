package com.seblful.klas.lock

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.seblful.klas.R
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.designsystem.component.KlasButton
import com.seblful.klas.ui.theme.KlasIcons

/**
 * [content], covered by the page that asks to unlock while [locked]. The content stays composed beneath,
 * so the tutor comes back to the screen they left — hidden from touch and from screen readers until then.
 *
 * @param onUnlock shows the system prompt, titled with the string it is given.
 */
@Composable
fun AppLockGate(locked: Boolean, onUnlock: (title: String) -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(if (locked) Modifier.clearAndSetSemantics {} else Modifier) { content() }
        if (locked) AppLockScreen(onUnlock)
    }
}

/** The prompt opens as the page does; a dismissed prompt leaves the page, with its button, to ask again. */
@Composable
internal fun AppLockScreen(onUnlock: (title: String) -> Unit) {
    val title = stringResource(R.string.app_lock_prompt_title, stringResource(R.string.app_name))
    LaunchedEffect(Unit) { onUnlock(title) }
    // Back leaves the app rather than reaching the screens underneath.
    val activity = LocalActivity.current
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = { activity?.moveTaskToBack(true) },
    )
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // Every touch stops here, short of the content beneath.
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            }
            .safeDrawingPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            KlasIcons.Outlined.PhonelinkLock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            stringResource(R.string.app_lock_locked, stringResource(R.string.app_name)),
            style = MaterialTheme.typography.titleLarge,
        )
        KlasButton(onClick = { onUnlock(title) }) { Text(stringResource(R.string.app_lock_unlock)) }
    }
}
