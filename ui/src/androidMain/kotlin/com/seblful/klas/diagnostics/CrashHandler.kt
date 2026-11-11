package com.seblful.klas.diagnostics

/**
 * Records every uncaught crash through [this], wrapping any existing handler. Call once, early, from
 * `Application.onCreate`.
 *
 * Returns the handler it installed, which is what lets a test fire a crash through it without
 * touching the JVM's real default handler.
 */
fun CrashLogger.install(): Thread.UncaughtExceptionHandler {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    val handler = Thread.UncaughtExceptionHandler { thread, throwable ->
        // Logging must never mask the crash, and must always hand off so Vitals still reports it.
        runCatching { record(throwable) }
        previous?.uncaughtException(thread, throwable)
    }
    Thread.setDefaultUncaughtExceptionHandler(handler)
    return handler
}
