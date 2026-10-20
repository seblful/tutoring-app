package com.seblful.klas.diagnostics

import com.seblful.klas.domain.time.WallClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant

/** n-device crash diagnostics — offline, no backend, in keeping with the app's local-only stance. */
@SingleIn(AppScope::class)
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
