package com.seblful.klas.ui.backup

import com.seblful.klas.data.backup.BackupData
import com.seblful.klas.data.backup.BackupFailure
import com.seblful.klas.data.local.DatabaseSnapshot
import com.seblful.klas.data.local.StudentEntity
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * What the backup screen is told when an export or an import doesn't work.
 *
 * Every failure used to arrive as one `ERROR`, and one message telling the tutor to check their file.
 * These pin the three cases apart, because the advice differs: a copy from a newer build needs an app
 * update, a file that isn't a backup needs a different file, and a file that wouldn't open needs
 * neither.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val env = TestEnv()
    private val documents = FakeDocumentStore()
    private val ref = DocumentRef("content://backup.json")

    /** A fixed instant, so the export receipt below is a value rather than "whenever the test ran". */
    private val exportedAt: Instant = Instant.parse("2026-06-20T18:30:00Z")

    private fun vm() = BackupViewModel(
        backup = env.backupManager,
        restore = env.backupRestore,
        documents = documents,
        settings = env.settingsRepo,
        // The receipt is a database write, so it outlives the screen — see [BackupViewModel.export].
        appScope = mainRule.appScope,
        clock = Clock.fixed(exportedAt, ZoneId.of("Europe/Minsk")),
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
            env.studentDao.upsert(StudentEntity(name = "Аня"))
            var received: BackupResult? = null
            val job = backgroundScope.launch { vm.events.stream.collect { received = it } }

            vm.export(ref)
            advanceUntilIdle()
            job.cancel()

            assertEquals(BackupResult.ExportOk, received)
            assertTrue(documents.docs.getValue(ref.value).contains("Аня"))
            assertEquals("and it records that it happened", exportedAt, env.settingsRepo.get().lastExportAt)
        }

    /**
     * A **failed** export leaves the receipt alone.
     *
     * The backup screen leads with «Последняя копия: …», so a stamp written for an export that did not
     * happen is the app telling the tutor they have a copy of their students that they do not have. That
     * is the one wrong answer this feature can give, and it is why the stamp is written after the file
     * rather than beside it.
     */
    @Test
    fun `an export that failed records no backup`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            documents.failing = true

            vm.export(ref)
            advanceUntilIdle()

            assertNull(env.settingsRepo.get().lastExportAt)
        }

    /**
     * Never exported is a state the screen says out loud, so it has to survive being asked for — and it
     * must be **said only about a row that was read**. «Копий ещё не было» is the sentence this screen
     * exists to make, and an initial `null` made it to every tutor for the frame or two before the row
     * arrived, including the ones who exported yesterday. `Loading` and never-exported are the same value
     * without [Loadable], and only one of them is a warning.
     */
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

            // The two numbers are the point: the message tells the tutor what they have and what
            // this build reads, which a bare "failed" cannot.
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

    /** A restore says it is running for as long as it runs, and stops saying so when it ends. */
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
        assertEquals(java.time.LocalDate.of(2026, 6, 20), vm().exportDay())
    }

    @Test
    fun `a valid backup imports, and ends first run as part of what a restore is`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            env.studentDao.upsert(StudentEntity(name = "Аня"))
            documents.docs[ref.value] = env.backupManager.exportJson()
            // Then the phone is emptied, so whatever comes back came from the file.
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
