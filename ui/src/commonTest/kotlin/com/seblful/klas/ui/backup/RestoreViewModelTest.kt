package com.seblful.klas.ui.backup

import com.seblful.klas.data.backup.BackupCipher
import com.seblful.klas.data.backup.BackupData
import com.seblful.klas.data.backup.BackupFailure
import com.seblful.klas.data.local.DatabaseSnapshot
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.testutil.FakeDocumentStore
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.TestMain
import com.seblful.klas.testutil.UiTest
import com.seblful.klas.testutil.UiTestEnv
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
class RestoreViewModelTest : UiTest() {

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
    private val documents = FakeDocumentStore()
    private val ref = DocumentRef("content://backup.json")

    private val cipher = BackupCipher(FakeSealCrypto())

    private fun vm() = main.own(
        RestoreViewModel(
            backup = env.backupManager,
            restore = env.backupRestore,
            cipher = cipher,
            documents = documents,
            appScope = main.appScope,
        ),
    )

    @Test
    fun `a backup from a newer build is refused by version - and says which`() =
        runTest(main.dispatcher) {
            val vm = vm()
            val newer = BackupData.CURRENT_VERSION + 3
            documents.docs[ref.value] = """{"version":$newer}"""
            var received: BackupResult? = null
            val job = backgroundScope.launch { vm.events.stream.collect { received = it } }

            vm.open(ref, askFirst = false)
            advanceUntilIdle()
            job.cancel()

            assertEquals(
                BackupResult.Failed(
                    BackupFailure.UnsupportedVersion(found = newer, supported = BackupData.CURRENT_VERSION),
                ),
                received,
            )
        }

    @Test
    fun `a file that is not a backup is reported as malformed - not as unreadable`() =
        runTest(main.dispatcher) {
            val vm = vm()
            documents.docs[ref.value] = "this is not json at all"
            var received: BackupResult? = null
            val job = backgroundScope.launch { vm.events.stream.collect { received = it } }

            vm.open(ref, askFirst = false)
            advanceUntilIdle()
            job.cancel()

            assertEquals(BackupResult.Failed(BackupFailure.Malformed), received)
        }

    @Test
    fun `a file that cannot be opened is reported as unavailable - not as malformed`() =
        runTest(main.dispatcher) {
            val vm = vm()
            documents.failing = true
            var received: BackupResult? = null
            val job = backgroundScope.launch { vm.events.stream.collect { received = it } }

            vm.open(ref, askFirst = false)
            advanceUntilIdle()
            job.cancel()

            assertEquals(BackupResult.Failed(BackupFailure.FileUnavailable), received)
        }

    @Test
    fun `a restore is shown as under way until it has finished`() =
        runTest(main.dispatcher) {
            val vm = vm()
            documents.docs[ref.value] = env.backupManager.exportJson()
            val seen = mutableListOf<Boolean>()
            val events = backgroundScope.launch { vm.events.stream.collect { } }
            val watching = backgroundScope.launch { vm.importing.collect { seen += it } }

            vm.open(ref, askFirst = false)
            advanceUntilIdle()
            events.cancel()
            watching.cancel()

            assertEquals(listOf(false, true, false), seen)
        }

    @Test
    fun `a valid backup imports - and ends first run as part of what a restore is`() =
        runTest(main.dispatcher) {
            val vm = vm()
            env.fixtures.student(name = "Аня")
            documents.docs[ref.value] = env.backupManager.exportJson()
            env.snapshotDao.replace(DatabaseSnapshot())
            var received: BackupResult? = null
            val job = backgroundScope.launch { vm.events.stream.collect { received = it } }

            vm.open(ref, askFirst = false)
            advanceUntilIdle()
            job.cancel()

            assertEquals(BackupResult.ImportOk, received)
            assertEquals(listOf("Аня"), env.stored().students.map { it.name })
            assertTrue(env.settingsRepo.get().onboardingComplete)
        }

    @Test
    fun `a picked file is described before anything is replaced - and restored on yes`() =
        runTest(main.dispatcher) {
            val vm = vm()
            documents.docs[ref.value] = backupOf("Аня")
            env.fixtures.student(name = "Борис")

            vm.open(ref)
            advanceUntilIdle()

            assertEquals(1, vm.pendingRestore.value?.preview?.students)
            assertEquals(listOf("Борис"), env.stored().students.map { it.name }, "nothing replaced yet")

            vm.confirmRestore()
            advanceUntilIdle()

            assertNull(vm.pendingRestore.value)
            assertEquals(listOf("Аня"), env.stored().students.map { it.name })
        }

    @Test
    fun `a file that would be refused is refused before the question`() =
        runTest(main.dispatcher) {
            val vm = vm()
            documents.docs[ref.value] = "this is not json at all"
            var received: BackupResult? = null
            val job = backgroundScope.launch { vm.events.stream.collect { received = it } }

            vm.open(ref)
            advanceUntilIdle()
            job.cancel()

            assertNull(vm.pendingRestore.value)
            assertEquals(BackupResult.Failed(BackupFailure.Malformed), received)
        }

    @Test
    fun `saying no to the preview replaces nothing`() =
        runTest(main.dispatcher) {
            val vm = vm()
            documents.docs[ref.value] = backupOf("Аня")
            env.fixtures.student(name = "Борис")
            vm.open(ref)
            advanceUntilIdle()

            vm.dismissRestore()
            vm.confirmRestore()
            advanceUntilIdle()

            assertNull(vm.pendingRestore.value)
            assertEquals(listOf("Борис"), env.stored().students.map { it.name })
        }

    @Test
    fun `the last import can be undone - once`() =
        runTest(main.dispatcher) {
            val vm = vm()
            documents.docs[ref.value] = backupOf("Аня")
            env.fixtures.student(name = "Борис")
            val received = mutableListOf<BackupResult>()
            val job = backgroundScope.launch { vm.events.stream.collect { received += it } }
            vm.open(ref, askFirst = false)
            advanceUntilIdle()
            assertEquals(env.clock.now(), vm.undoRestoreSince.value)

            vm.undoRestore()
            advanceUntilIdle()
            job.cancel()

            assertEquals(listOf(BackupResult.ImportOk, BackupResult.RestoreUndone), received)
            assertEquals(listOf("Борис"), env.stored().students.map { it.name })
            assertNull(vm.undoRestoreSince.value)
        }

    @Test
    fun `a sealed file asks for its password - and a wrong one is said in place`() =
        runTest(main.dispatcher) {
            val vm = vm()
            documents.docs[ref.value] = cipher.seal(backupOf("Аня"), "лето")
            env.fixtures.student(name = "Борис")

            vm.open(ref)
            advanceUntilIdle()
            assertEquals(false, vm.locked.value?.wrong)

            vm.unlock("зима")
            advanceUntilIdle()
            assertEquals(true, vm.locked.value?.wrong)
            assertNull(vm.pendingRestore.value)

            vm.unlock("лето")
            advanceUntilIdle()
            assertNull(vm.locked.value)
            assertEquals(1, vm.pendingRestore.value?.preview?.students)
            assertEquals(listOf("Борис"), env.stored().students.map { it.name }, "nothing replaced yet")
        }

    @Test
    fun `on a first run a sealed file restores once unlocked - without a second question`() =
        runTest(main.dispatcher) {
            val vm = vm()
            documents.docs[ref.value] = cipher.seal(backupOf("Аня"), "лето")

            vm.open(ref, askFirst = false)
            advanceUntilIdle()
            vm.unlock("лето")
            advanceUntilIdle()

            assertNull(vm.pendingRestore.value)
            assertEquals(listOf("Аня"), env.stored().students.map { it.name })
        }

    /** A backup of one student, made on another phone. */
    private suspend fun backupOf(name: String): String = TestEnv().use { other ->
        other.fixtures.student(name = name)
        other.backupManager.exportJson()
    }
}
