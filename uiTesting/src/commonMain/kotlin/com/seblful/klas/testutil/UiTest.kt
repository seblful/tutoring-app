package com.seblful.klas.testutil

/**
 * The base of a `:ui` test that needs the platform under the screens: Room, `SavedStateHandle`. On Android's host
 * JVM it runs on Robolectric, whose framework classes are real where `android.jar`'s throw or drop exception
 * messages Room reads; on iOS it is a plain test.
 */
expect abstract class UiTest()
