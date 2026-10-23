package com.seblful.klas.screenshot

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * Ends every tap's ripple on the showing screen. A ripple is the platform's RippleDrawable, run by the looper's
 * clock rather than Compose's and not always finished by it: left alone, a tab tapped earlier is caught in its
 * press shade, a different tab each run.
 */
internal fun finishRipples() {
    // Lets the ripple see its press released, which it is told after a delay.
    ShadowLooper.idleMainLooper(RELEASE_MS, TimeUnit.MILLISECONDS)
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .forEach { it.window.decorView.jumpDrawablesToCurrentState() }
    }
}

private const val RELEASE_MS = 500L
