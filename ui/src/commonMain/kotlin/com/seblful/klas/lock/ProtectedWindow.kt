package com.seblful.klas.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

/** Only protected app content inherits this; the unlock screen can still open its system prompt. */
internal val LocalAppLocked = compositionLocalOf { false }

/**
 * A popup owns another window, so the activity's lock cannot cover it. Its drafts stay here while hidden;
 * bind their SaveableStateProvider inside the popup's body, whose Android owner replaces the parent registry.
 */
@Composable
internal fun UnlockedWindow(content: @Composable (drafts: SaveableStateHolder) -> Unit) {
    val drafts = rememberSaveableStateHolder()
    if (!LocalAppLocked.current) content(drafts)
}
