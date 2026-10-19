package com.seblful.klas.diagnostics

import com.seblful.klas.domain.time.WallClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * On-device crash diagnostics — offline, no backend, in keeping with the app's local-only stance.
 *
 * [install] sets a last-resort uncaught-exception handler that appends each crash to a capped log,
 * then **delegates to the platform's previous handler** so the process still dies normally and
 * Google Play still receives the crash (Android Vitals). The saved log is surfaced — and can be
 * exported — from the Diagnostics screen, so the tutor can send it on without any network.
 *
 * Both Android-bound halves are injected ([CrashLogStore] for the bytes, [DeviceInfo] for the header
 * facts), so the part that can actually be got wrong is ordinary code a JVM test can drive.
 */
@Singleton
class CrashLogger @Inject constructor(
    private val store: CrashLogStore,
    private val device: DeviceInfo,
    private val clock: WallClock,
) {
    /**
     * Wraps any existing handler. Call once, early, from `Application.onCreate`.
     *
     * Returns the handler it installed, which is what lets a test fire a crash through it without
     * touching the JVM's real default handler.
     */
    fun install(): Thread.UncaughtExceptionHandler {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val handler = Thread.UncaughtExceptionHandler { thread, throwable ->
            // Logging must never mask the crash, and must always hand off so Vitals still reports it.
            runCatching { record(throwable) }
            previous?.uncaughtException(thread, throwable)
        }
        Thread.setDefaultUncaughtExceptionHandler(handler)
        return handler
    }

    /** Appends one crash to the log, trimming the oldest entries past the cap. [now] is the injected clock's, so a test can pin the stamp. */
    @Synchronized
    fun record(throwable: Throwable, now: Instant = clock.now()) {
        val entry = Diagnostics.formatEntry(
            time = now,
            appVersion = device.appVersion,
            androidSdk = device.androidSdk,
            device = device.device,
            stackTrace = throwable.stackTraceToString(),
        )
        store.write(Diagnostics.appendBounded(store.read(), entry))
    }

    fun hasLog(): Boolean = !store.isEmpty()

    fun read(): String = store.read()

    fun clear() {
        store.delete()
    }
}
