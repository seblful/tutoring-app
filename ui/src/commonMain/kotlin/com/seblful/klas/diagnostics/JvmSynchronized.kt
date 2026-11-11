package com.seblful.klas.diagnostics

/** `kotlin.jvm.Synchronized` where there is a JVM; elsewhere nothing, as common code cannot lock a method. */
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
@Target(AnnotationTarget.FUNCTION)
expect annotation class JvmSynchronized()
