package com.seblful.klas.ui.settings

import com.seblful.klas.diagnostics.DeviceInfo
import com.seblful.klas.settings.SettingsWorkflow
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
abstract class SettingsTestFixture {

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 0)
    val env = TestEnv()

    protected fun vm() = mainRule.own(
        SettingsViewModel(
            env.settingsRepo,
            SettingsWorkflow(env.settingsRepo, env.calendarMirror, env.reminders, env.maintenance, env.notifier),
            env.calendarPermission, env.notificationPermission, env.exactAlarmPermission, mainRule.appScope,
            env.subjectRepo, NoCrashLogs, DeviceInfo("1.0", "Android SDK 34", "Pixel"),
        ),
    )
}
