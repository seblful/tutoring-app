package com.seblful.klas.ui.backup

import com.seblful.klas.data.backup.BackupData
import com.seblful.klas.data.backup.BackupFailure
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.testutil.FakeDocumentStore
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What the backup screen is told when an export or an import doesn't work.
 *
 * Every failure used to arrive as one `ERROR`, and one message telling the tutor to check their file.
 * These pin the three cases apart, because the advice differs: a copy from a newer build needs an app
 * update, a file that isn't a backup needs a different file, and a file that wouldn't open needs
 * neither.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()
    private val documents = FakeDocumentStore()
    private val ref = DocumentRef("content://backup.json")

    private fun vm() = BackupViewModel(
        backup = env.backupManager,
        restore = env.backupRestore,
        documents = documents,
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

    @Test
    fun `a valid backup imports, and ends first run as part of what a restore is`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            env.studentDao.upsert(StudentEntity(name = "Аня"))
            documents.docs[ref.value] = env.backupManager.exportJson()
            env.studentDao.items.value = emptyList()
            var received: BackupResult? = null
            val job = backgroundScope.launch { vm.events.stream.collect { received = it } }

            vm.import(ref)
            advanceUntilIdle()
            job.cancel()

            assertEquals(BackupResult.ImportOk, received)
            assertEquals(listOf("Аня"), env.studentDao.items.value.map { it.name })
            assertTrue(env.settingsRepo.get().onboardingComplete)
        }
}
