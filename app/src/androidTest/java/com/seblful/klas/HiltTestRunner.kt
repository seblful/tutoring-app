package com.seblful.klas

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Swaps in [KlasTestApp]'s generated application so Hilt can inject into instrumented tests — and so
 * WorkManager can answer for itself when one of its jobs lands in the test process. [WorkAwareTestApp]
 * is where the second half is argued; it is why this is not Hilt's stock `HiltTestApplication`.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, KlasTestApp_Application::class.java.name, context)
}
