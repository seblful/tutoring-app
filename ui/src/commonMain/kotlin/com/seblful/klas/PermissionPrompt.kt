package com.seblful.klas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** A platform permission prompt as a screen holds it. */
class PermissionPrompt(
    /** Shows the platform's prompt. */
    val launch: () -> Unit,
    /**
     * Whether the tutor refused this prompt once and Android advises saying why before showing it again.
     * False before the first ask, after a grant, and after a refusal for good — when [launch] answers at
     * once, unseen. Read it at the tap: the last answer changes it.
     */
    val rationaleDue: () -> Boolean,
)
