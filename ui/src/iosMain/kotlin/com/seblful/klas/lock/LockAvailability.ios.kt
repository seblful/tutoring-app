package com.seblful.klas.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.cinterop.ExperimentalForeignApi
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication

@Composable
actual fun rememberLockAvailable(): Boolean {
    var available by remember { mutableStateOf(canUnlock()) }
    LifecycleResumeEffect(Unit) {
        available = canUnlock()
        onPauseOrDispose {}
    }
    return available
}

/** Face ID, Touch ID or the device passcode: the policy that falls back to the passcode. */
@OptIn(ExperimentalForeignApi::class)
fun canUnlock(): Boolean = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)
