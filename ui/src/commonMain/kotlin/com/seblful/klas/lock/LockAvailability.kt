package com.seblful.klas.lock

import androidx.compose.runtime.Composable

/**
 * Whether this phone can unlock the app at all — it has a fingerprint, a face or a screen lock set — asked
 * again each time the screen resumes, so a screen lock set in system settings shows at once.
 */
@Composable
expect fun rememberLockAvailable(): Boolean
