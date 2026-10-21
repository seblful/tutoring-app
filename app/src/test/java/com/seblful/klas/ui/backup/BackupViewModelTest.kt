package com.seblful.klas.ui.backup

import com.seblful.klas.data.backup.BackupData
import com.seblful.klas.data.backup.BackupFailure
import com.seblful.klas.data.local.DatabaseSnapshot
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.testutil.FakeDocumentStore
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupViewModelTest {

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 0)
    val env = TestEnv()
    private val documents = FakeDocumentStore()
    private val ref = DocumentRef("content://backup.json")

    private val exportedAt: Instant = Instant.parse("2026-06-20T18:30:00Z")

    private fun vm() = mainRule.own(
        BackupViewModel(
            backup = env.backupManager,
            restore = env.backupRestore,
            documents = documents,
            settings = env.settingsRepo,
            appScope = mainRule.appScope,
            clock = WallClock.fixed(exportedAt, TimeZone.of("Europe/Minsk")),
        ),
    )

    @Test
    fun `an export that cannot write the file reports the file, not the contents`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            documents.failing = true
            var received: BackupResult? = null
            val job = backgroundScope.launch { vm.events.stream.collect { received = it } }

            vm.export(ref)
            advanceUntilIdle()
            job.cancel()

            assertEquals(BackupResult.Failed(BackupFailure.FileUnavailable), received)
        }

    @Test
    fun `a successful export reports success and leaves the document behind`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            env.fixtures.student(name = "Аня")
            var received: BackupResult? = null
            val job = backgroundScope.launch { vm.events.stream.collect { received = it } }

            vm.export(ref)
            advanceUntilIdle()
            job.cancel()

            assertEquals(BackupResult.ExportOk, received)
            assertTrue(documents.docs.getValue(ref.value).contains("Аня"))
            assertEquals("and it records that it happened", exportedAt, env.settingsRepo.get().lastExportAt)
        }

    @Test
    fun `an export that failed records no backup`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            documents.failing = true

            vm.export(ref)
            advanceUntilIdle()

            assertNull(env.settingsRepo.get().lastExportAt)
        }

    @Test
    fun `nothing exported yet reads as never, and only once the row has been read`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        assertEquals(Loadable.Loading, vm.lastExportAt.value)

        val job = backgroundScope.launch { vm.lastExportAt.collect {} }
        advanceUntilIdle()

        assertNull("read, and genuinely never exported", vm.lastExportAt.current)

        vm.export(ref)
        advanceUntilIdle()
        job.cancel()

        assertEquals(exportedAt, vm.lastExportAt.current)
    }

    @Test
    fun `a backup from a newer build is refused by version, and says which`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            val newer = BackupData.CURRENT_VERSION + 3
            documents.docs[ref.value] = """{"version":$newer}"""
            var received: BackupResult? = null
            val job = backgroundScope.launch { vm.events.stream.collect { received = it } }

            vm.import(ref)
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
    fun `a file that is not a backup is reported as malformed, not as unreadable`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            documents.docs[ref.value] = "this is not json at all"
            var received: BackupResult? = null
            val job = backgroundScope.launch { vm.events.stream.collect { received = it } }

            vm.import(ref)
            advanceUntilIdle()
            job.cancel()

            assertEquals(BackupResult.Failed(BackupFailure.Malformed), received)
        }

    @Test
    fun `a file that cannot be opened is reported as unavailable, not as malformed`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            documents.failing = true
            var received: BackupResult? = null
            val job = backgroundScope.launch { vm.events.stream.collect { received = it } }

            vm.import(ref)
            advanceUntilIdle()
            job.cancel()

            assertEquals(BackupResult.Failed(BackupFailure.FileUnavailable), received)
        }

    @Test
    fun `a restore is shown as under way until it has finished`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            documents.docs[ref.value] = env.backupManager.exportJson()
            val seen = mutableListOf<Boolean>()
            val events = backgroundScope.launch { vm.events.stream.collect { } }
            val watching = backgroundScope.launch { vm.importing.collect { seen += it } }

            vm.import(ref)
            advanceUntilIdle()
            events.cancel()
            watching.cancel()

            assertEquals(listOf(false, true, false), seen)
        }

    @Test
    fun `an export is named for the day it was made`() {
        assertEquals(LocalDate(2026, 6, 20), vm().exportDay())
    }

    @Test
    fun `a valid backup imports, and ends first run as part of what a restore is`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            env.fixtures.student(name = "Аня")
            documents.docs[ref.value] = env.backupManager.exportJson()
            env.snapshotDao.replace(DatabaseSnapshot())
            var received: BackupResult? = null
            val job = backgroundScope.launch { vm.events.stream.collect { received = it } }

            vm.import(ref)
            advanceUntilIdle()
            job.cancel()

            assertEquals(BackupResult.ImportOk, received)
            assertEquals(listOf("Аня"), env.stored().students.map { it.name })
            assertTrue(env.settingsRepo.get().onboardingComplete)
        }
}
