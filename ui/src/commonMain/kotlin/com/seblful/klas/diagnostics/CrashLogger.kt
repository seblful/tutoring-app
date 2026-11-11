package com.seblful.klas.diagnostics

import com.seblful.klas.domain.time.WallClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant

/** On-device crash diagnostics — offline, no backend, in keeping with the app's local-only stance. */
@SingleIn(AppScope::class)
class CrashLogger @Inject constructor(
    private val store: CrashLogStore,
    private val device: DeviceInfo,
    private val clock: WallClock,
) {

    /**
     * Appends one crash to the log, trimming the oldest entries past the cap. [now] is the injected clock's,
     * so a test can pin the stamp.
     */
    @JvmSynchronized
    fun record(throwable: Throwable, now: Instant = clock.now()) {
        val entry = Diagnostics.formatEntry(
            time = now,
            appVersion = device.appVersion,
            system = device.system,
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
