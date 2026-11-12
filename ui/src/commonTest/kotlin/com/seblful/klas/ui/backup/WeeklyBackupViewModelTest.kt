package com.seblful.klas.ui.backup

import com.seblful.klas.data.backup.AutoBackup
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.testutil.FakeBackupFolder
import com.seblful.klas.testutil.FakeFolderGrants
import com.seblful.klas.testutil.TestMain
import com.seblful.klas.testutil.UiTest
import com.seblful.klas.testutil.UiTestEnv
import com.seblful.klas.testutil.backupExports
import com.seblful.klas.testutil.current
import com.seblful.klas.ui.backup.WeeklyBackupViewModel.Event
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyBackupViewModelTest : UiTest() {

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

    private val main = TestMain()

    private val env = UiTestEnv()
    private val folders = FakeBackupFolder()
    private val grants = FakeFolderGrants()

    private fun vm() = main.own(
        WeeklyBackupViewModel(
            settings = env.settingsRepo,
            autoBackup = AutoBackup(env.backupExports(folders = folders), env.settingsRepo, folders, env.clock),
            grants = grants,
            appScope = main.appScope,
        ),
    )

    @Test
    fun `picking a folder keeps access to it and saves the first copy at once`() =
        runTest(main.dispatcher) {
            val vm = vm()
            env.fixtures.student(name = "Аня")
            val received = mutableListOf<Event>()
            val job = backgroundScope.launch { vm.events.stream.collect { received += it } }

            vm.choose(FOLDER)
            advanceUntilIdle()
            job.cancel()

            assertEquals(setOf(FOLDER.value), grants.kept)
            assertEquals(FOLDER.value, env.settingsRepo.get().autoBackupFolder)
            assertEquals(1, folders.files.getValue(FOLDER.value).size)
            assertEquals(listOf(Event.SAVED), received)
        }

    @Test
    fun `a folder that came with no access to keep is not taken`() =
        runTest(main.dispatcher) {
            val vm = vm()
            grants.refusing = true
            var received: Event? = null
            val job = backgroundScope.launch { vm.events.stream.collect { received = it } }

            vm.choose(FOLDER)
            advanceUntilIdle()
            job.cancel()

            assertNull(env.settingsRepo.get().autoBackupFolder)
            assertEquals(Event.FOLDER_REFUSED, received)
        }

    @Test
    fun `choosing another folder gives up the first - and stopping gives up the second`() =
        runTest(main.dispatcher) {
            val vm = vm()
            vm.choose(FOLDER)
            advanceUntilIdle()

            vm.choose(DocumentRef("content://tree/other"))
            advanceUntilIdle()
            assertEquals(setOf("content://tree/other"), grants.kept)

            vm.stop()
            advanceUntilIdle()
            assertTrue(grants.kept.isEmpty())
            assertNull(env.settingsRepo.get().autoBackupFolder)
        }

    @Test
    fun `the state names the folder and says when a copy failed`() =
        runTest(main.dispatcher) {
            val vm = vm()
            grants.names[FOLDER.value] = "Klas"
            val job = backgroundScope.launch { vm.weekly.collect {} }
            env.settingsRepo.update { it.copy(autoBackupFolder = FOLDER.value, autoBackupFailed = true) }
            advanceUntilIdle()
            job.cancel()

            assertEquals(WeeklyBackupViewModel.Weekly(FOLDER.value, "Klas", failed = true), vm.weekly.current)
        }

    private companion object {
        val FOLDER = DocumentRef("content://tree/backups")
    }
}
