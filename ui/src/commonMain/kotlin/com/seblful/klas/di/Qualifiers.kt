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
 * The locales the **phone** asks for, most-preferred first — the device half of two settings that start at
 * whatever the tutor's phone already says: which language the app is shown in
 * ([com.seblful.klas.domain.model.AppLanguage.SYSTEM]) and which currency a fresh install captions money
 * with ([com.seblful.klas.domain.model.Currency.preferredFor]).
 *
 * Injected as a value rather than reached for statically, for the reason [FirstDayOfWeek] is: what a phone
 * is set to is precisely the thing a test has to be able to state. "A tutor in Minsk with an English phone
 * is paid in rubles" is a rule about a `ru`-less locale list with a `BY` region in it, and there is no way
 * to write that assertion against `Locale.getDefault()`.
 *
 * Read once per component rather than observed: changing the phone's language restarts the process, so
 * there is no live value to follow. The list, not one locale, because the two questions read different
 * parts of it — the language off the front, the currency off whichever entry first names a region the app
 * knows.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceLocales

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
