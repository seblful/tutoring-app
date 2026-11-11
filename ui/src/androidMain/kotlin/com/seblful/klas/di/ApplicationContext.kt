package com.seblful.klas.di

import dev.zacsweers.metro.Qualifier

/** The application's `Context`, as opposed to an activity's — the only one the graph holds. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationContext
