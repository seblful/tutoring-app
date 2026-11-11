package com.seblful.klas.ui.students

import androidx.compose.runtime.Composable

/**
 * Opens the phone's own contact picker and hands back the chosen contact's name and number. The picker
 * grants a one-time read of that row, so the app asks for no contacts permission.
 */
@Composable
expect fun rememberContactPicker(onPicked: (name: String, number: String) -> Unit): () -> Unit
