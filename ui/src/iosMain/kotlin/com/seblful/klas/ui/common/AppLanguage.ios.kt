package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable

@Composable
internal actual fun ProvidePlatformLanguage(tag: String, content: @Composable () -> Unit) = content()
