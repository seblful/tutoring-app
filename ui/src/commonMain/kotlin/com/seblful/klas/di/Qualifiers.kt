package com.seblful.klas.di

import dev.zacsweers.metro.Qualifier

// Qualifiers shared by the screens and the hosts; each host's graph binds them.

/**
 * Process scope for writes that must outlive the screen that started them.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Dispatcher for upstream repository mapping and state combination. `flowOn` keeps that work off the
 * main thread; injection lets tests use their controlled dispatcher.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Computation

/**
 * Dispatcher for blocking file, content-provider and calendar calls, injected for the reason [Computation] is.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Io

/**
 * Marks "is this a debuggable build". Injected as a plain flag rather than read from a `Context` at
 * the use site, so the screens that gate on it stay reachable from a JVM test.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DebugBuild

/**
 * Where a week starts, for the schedule's calendar — the phone's answer, read once (see
 * [com.seblful.klas.format.deviceFirstDayOfWeek]).
 *
 * Injected for the same reason the [WallClock] is: the week strip and the month grid are anchored on it, so a
 * test has to be able to say "this tutor's week starts on Sunday" without a device set that way. Read once
 * per component rather than observed, because changing a regional preference restarts the activity anyway.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FirstDayOfWeek
