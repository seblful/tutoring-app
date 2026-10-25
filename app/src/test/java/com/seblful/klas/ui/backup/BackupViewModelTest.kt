package com.seblful.klas.ui.backup

import com.seblful.klas.data.backup.BackupFailure
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.storage.AesBackupCipher
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentStore
import com.seblful.klas.testutil.FakeDocumentStore
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.backupExports
import com.seblful.klas.testutil.current
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.days
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
    private val cipher = AesBackupCipher(mainRule.dispatcher)

    private val exportedAt: Instant = Instant.parse("2026-06-20T18:30:00Z")

    private fun vm(
        documents: DocumentStore = this.documents,
        clock: WallClock = WallClock.fixed(exportedAt, TimeZone.of("Europe/Minsk")),
    ) = mainRule.own(
        BackupViewModel(
            exports = env.backupExports(documents = documents, cipher = cipher, clock = clock),
            settings = env.settingsRepo,
            appScope = mainRule.appScope,
            clock = clock,
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
    fun `an export is named for the day it was made`() {
        assertEquals(LocalDate(2026, 6, 20), vm().exportDay())
    }

    @Test
    fun `a backup older than two weeks reads as stale`() {
        val vm = vm()
        assertTrue(vm.isStale(null))
        assertTrue(vm.isStale(exportedAt - 15.days))
        assertFalse(vm.isStale(exportedAt - 13.days))
    }

    @Test
    fun `a backup saved with a password opens only with it`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            env.fixtures.student(name = "Аня")

            vm.export(ref, password = "лето")
            advanceUntilIdle()

            val file = documents.docs.getValue(ref.value)
            assertFalse(file.contains("Аня"))
            assertEquals(1, env.backupManager.preview(cipher.open(file, "лето")).students)
        }

    @Test
    fun `export and history recording finish after the backup screen leaves`() = runTest(mainRule.dispatcher) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val slow = object : DocumentStore by documents {
            override suspend fun write(ref: DocumentRef, text: String) {
                entered.complete(Unit)
                release.await()
                documents.write(ref, text)
            }
        }
        val vm = vm(slow)
        var received: BackupResult? = null
        backgroundScope.launch { vm.events.stream.collect { received = it } }

        vm.export(ref)
        runCurrent()
        entered.await()
        assertNull(received)
        mainRule.clearViewModels()
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(BackupResult.ExportOk, received)
        assertEquals(exportedAt, env.settingsRepo.get().lastExportAt)
        assertTrue(documents.docs.containsKey(ref.value))
    }
}
