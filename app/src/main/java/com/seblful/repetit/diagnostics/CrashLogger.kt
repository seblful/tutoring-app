package com.seblful.repetit.diagnostics

import android.content.Context
import android.os.Build
import com.seblful.repetit.domain.Diagnostics
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device crash diagnostics — offline, no backend, in keeping with the app's local-only stance.
 *
 * [install] sets a last-resort uncaught-exception handler that appends each crash to a single capped
 * file in the app's private storage, then **delegates to the platform's previous handler** so the
 * process still dies normally and Google Play still receives the crash (Android Vitals). The saved
 * file is surfaced — and can be exported via SAF — from the Diagnostics screen, so the tutor can
 * send it on without any network or account.
 */
@Singleton
class CrashLogger @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val file: File get() = File(context.filesDir, FILE_NAME)

    /** Wraps any existing handler. Call once, early, from `Application.onCreate`. */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Logging must never mask the crash, and must always hand off so Vitals still reports it.
            runCatching { record(throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    @Synchronized
    private fun record(throwable: Throwable) {
        val entry = Diagnostics.formatEntry(
            time = Instant.now(),
            appVersion = appVersion(),
            androidSdk = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            stackTrace = throwable.stackTraceToString(),
        )
        val existing = if (file.exists()) file.readText() else ""
        file.writeText(Diagnostics.appendBounded(existing, entry))
    }

    fun hasLog(): Boolean = file.exists() && file.length() > 0L

    fun read(): String = if (file.exists()) file.readText() else ""

    fun clear() {
        file.delete()
    }

    private fun appVersion(): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    private companion object {
        const val FILE_NAME = "crash_log.txt"
    }
}
