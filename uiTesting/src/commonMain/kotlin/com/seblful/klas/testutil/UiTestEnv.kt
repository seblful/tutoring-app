package com.seblful.klas.testutil

import com.seblful.klas.settings.DevicePreferences

/** [TestEnv] with the permissions the screens ask about, all granted until a test says otherwise. */
open class UiTestEnv(
    database: TestDatabase = TestDatabase(),
    clock: MutableClock = testClock(),
    preferences: DevicePreferences = DevicePreferences(listOf("ru-BY"), listOf("BYN")),
) : TestEnv(database, clock, preferences) {
    val calendarPermission = FakeCalendarPermission()
    val notificationPermission = FakeNotificationPermission()
    val exactAlarmPermission = FakeExactAlarmPermission()
}
