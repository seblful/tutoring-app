package com.seblful.klas.ui.common.state

import androidx.lifecycle.SavedStateHandle
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.coroutines.flow.MutableStateFlow

/** Saves only the editable draft, when Android requests state, rather than serializing every keystroke. */
inline fun <reified T : Any> SavedStateHandle.savedDraft(key: String, initial: T): MutableStateFlow<T> {
    val restored = get<SavedState>(key)?.let { decodeFromSavedState<T>(it) }
    val draft = MutableStateFlow(restored ?: initial)
    setSavedStateProvider(key) { encodeToSavedState(draft.value) }
    return draft
}
