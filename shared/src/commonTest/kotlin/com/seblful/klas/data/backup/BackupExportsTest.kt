package com.seblful.klas.data.backup

import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentStore
import com.seblful.klas.testutil.FakeBackupFolder
import com.seblful.klas.testutil.FakeDocumentStore
import com.seblful.klas.testutil.FakeSealCrypto
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.backupExports
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class BackupExportsTest {
    private val env = TestEnv()
    private val documents = FakeDocumentStore()
    private val folders = FakeBackupFolder()
    private val crypto = FakeSealCrypto()
    private val cipher = BackupCipher(crypto)
    private val ref = DocumentRef("manual-backup")

    @AfterTest
    fun close() = env.close()

    @Test
    fun `a slow export records the timestamp in its file`() = runTest {
        val at = env.clock.now()
        val slow = object : DocumentStore by documents {
            override suspend fun write(ref: DocumentRef, text: String) {
                env.clock.advanceBy(1.days.inWholeMilliseconds)
                documents.write(ref, text)
            }
        }

        val exported = env.backupExports(documents = slow).toDocument(ref)

        assertEquals(at, exported)
        assertEquals(at, env.backupManager.preview(documents.docs.getValue(ref.value)).exportedAt)
        assertEquals(at, env.settingsRepo.get().lastExportAt)
    }

    @Test
    fun `a failed write or sealing attempt records no backup`() = runTest {
        val exports = env.backupExports(documents = documents, cipher = cipher)
        documents.failing = true
        assertFailsWith<IllegalStateException> { exports.toDocument(ref) }
        assertNull(env.settingsRepo.get().lastExportAt)

        documents.failing = false
        crypto.failing = true
        assertFailsWith<IllegalStateException> { exports.toDocument(ref, password = "summer") }
        assertNull(env.settingsRepo.get().lastExportAt)
        assertTrue(documents.docs.isEmpty())
    }

    @Test
    fun `a password seals the snapshot before the document is written`() = runTest {
        env.fixtures.student("Ann")
        val exports = env.backupExports(documents = documents, cipher = cipher)

        val at = exports.toDocument(ref, password = "summer")

        val opened = cipher.open(documents.docs.getValue(ref.value), "summer")
        assertEquals(at, env.backupManager.preview(opened).exportedAt)
        assertEquals(1, env.backupManager.preview(opened).students)
        assertEquals(at, env.settingsRepo.get().lastExportAt)
    }

    @Test
    fun `export completion waits for history after the file is written`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val storage = object : DocumentStore by documents {
            override suspend fun write(ref: DocumentRef, text: String) {
                documents.write(ref, text)
                backgroundScope.launch {
                    env.transactor.transaction {
                        entered.complete(Unit)
                        release.await()
                    }
                }
                entered.await()
            }
        }
        val exports = env.backupExports(documents = storage)
        val pending = async { exports.toDocument(ref) }
        entered.await()
        try {
            runCurrent()
            assertTrue(documents.docs.containsKey(ref.value))
            assertFalse(pending.isCompleted, "a written file is not yet a completed export")
        } finally {
            release.complete(Unit)
        }

        assertEquals(pending.await(), env.settingsRepo.get().lastExportAt)
    }

    @Test
    fun `a slower old export cannot replace newer backup history`() = runTest {
        val oldAt = env.clock.now()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val slow = object : DocumentStore by documents {
            override suspend fun write(ref: DocumentRef, text: String) {
                if (ref == this@BackupExportsTest.ref) {
                    entered.complete(Unit)
                    release.await()
                }
                documents.write(ref, text)
            }
        }
        val exports = env.backupExports(documents = slow)
        val older = async { exports.toDocument(ref) }
        entered.await()
        val newerAt = try {
            env.clock.advanceBy(1.days.inWholeMilliseconds)
            exports.toDocument(DocumentRef("newer-backup"))
        } finally {
            release.complete(Unit)
        }

        assertEquals(oldAt, older.await())
        assertEquals(newerAt, env.settingsRepo.get().lastExportAt)
        assertEquals(oldAt, env.backupManager.preview(documents.docs.getValue(ref.value)).exportedAt)
    }

    @Test
    fun `a slow weekly copy names and records its original snapshot day`() = runTest {
        env.settingsRepo.update { it.copy(autoBackupFolder = "weekly") }
        val at = env.clock.now()
        val slow = object : BackupFolder by folders {
            override suspend fun write(folder: String, name: String, text: String) {
                env.clock.advanceBy(1.days.inWholeMilliseconds)
                folders.write(folder, name, text)
            }
        }

        assertEquals(at, env.backupExports(folders = slow).toFolder("weekly"))

        val file = folders.files.getValue("weekly").getValue("klas-auto-backup-2026-06-01.json")
        assertEquals(at, env.backupManager.preview(file).exportedAt)
        assertEquals(at, env.settingsRepo.get().lastExportAt)
    }

    @Test
    fun `a successful copy into a replaced folder does not update the new folder history`() = runTest {
        env.settingsRepo.update { it.copy(autoBackupFolder = "old") }
        val changing = object : BackupFolder by folders {
            override suspend fun write(folder: String, name: String, text: String) {
                folders.write(folder, name, text)
                env.settingsRepo.update { it.copy(autoBackupFolder = "new") }
            }
        }

        env.backupExports(folders = changing).toFolder("old")

        assertEquals(1, folders.files.getValue("old").size)
        assertNull(env.settingsRepo.get().lastExportAt)
        assertFalse(env.settingsRepo.get().autoBackupFailed)
    }

    @Test
    fun `cancelling a weekly copy neither records success nor flags the folder`() = runTest {
        env.settingsRepo.update { it.copy(autoBackupFolder = "weekly") }
        val entered = CompletableDeferred<Unit>()
        val blocked = object : BackupFolder by folders {
            override suspend fun write(folder: String, name: String, text: String) {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        val pending = launch { env.backupExports(folders = blocked).toFolder("weekly") }
        entered.await()

        pending.cancelAndJoin()

        assertNull(env.settingsRepo.get().lastExportAt)
        assertFalse(env.settingsRepo.get().autoBackupFailed)
        assertTrue(folders.files.isEmpty())
    }
}
