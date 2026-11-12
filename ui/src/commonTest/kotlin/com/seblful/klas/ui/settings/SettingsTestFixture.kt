package com.seblful.klas.ui.settings

import com.seblful.klas.diagnostics.DeviceInfo
import com.seblful.klas.settings.SettingsWorkflow
import com.seblful.klas.testutil.TestMain
import com.seblful.klas.testutil.UiTest
import com.seblful.klas.testutil.UiTestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@OptIn(ExperimentalCoroutinesApi::class)
abstract class SettingsTestFixture : UiTest() {

    @BeforeTest
    fun startMain() = main.start()

    @AfterTest
    fun finishMainThenCloseDatabase() {
        try {
            main.finish()
        } finally {
            env.close()
        }
    }

    protected val main = TestMain()

    protected val env = UiTestEnv()

    protected fun vm() = main.own(
        SettingsViewModel(
            env.settingsRepo,
            SettingsWorkflow(env.settingsRepo, env.calendarMirror, env.reminders, env.maintenance, env.notifier),
            env.calendarPermission, env.notificationPermission, env.exactAlarmPermission, main.appScope,
            env.subjectRepo, NoCrashLogs, DeviceInfo("1.0", "Android SDK 34", "Pixel"),
        ),
    )
}
