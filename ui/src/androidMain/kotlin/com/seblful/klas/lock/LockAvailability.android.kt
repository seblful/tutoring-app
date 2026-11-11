package com.seblful.klas.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Biometrics or the phone's own screen lock — the combination every API level the app runs on accepts. */
private const val UNLOCK_WITH: Int = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/**
 * The unlock prompt, titled [title]. No negative button: with the screen lock allowed, the prompt offers it
 * itself.
 */
fun unlockPrompt(title: String): BiometricPrompt.PromptInfo =
    BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(UNLOCK_WITH).build()

/** Whether this phone can unlock the app at all: it has a fingerprint, a face or a screen lock set. */
fun Context.canUnlock(): Boolean =
    BiometricManager.from(this).canAuthenticate(UNLOCK_WITH) == BiometricManager.BIOMETRIC_SUCCESS

@Composable
actual fun rememberLockAvailable(): Boolean {
    val context = LocalContext.current
    var available by remember { mutableStateOf(context.canUnlock()) }
    LifecycleResumeEffect(context) {
        available = context.canUnlock()
        onPauseOrDispose {}
    }
    return available
}
