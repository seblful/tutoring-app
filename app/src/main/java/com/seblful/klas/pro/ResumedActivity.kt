package com.seblful.klas.pro

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.lang.ref.WeakReference

/**
 * The activity on screen, for the one call that needs one: Play's purchase sheet. `KlasApp` registers it before
 * any activity starts. Weak, so a destroyed activity is never kept.
 */
@SingleIn(AppScope::class)
class ResumedActivity @Inject constructor() : Application.ActivityLifecycleCallbacks {
    private var current = WeakReference<Activity>(null)

    val activity: Activity? get() = current.get()

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (current.get() === activity) current = WeakReference(null)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
