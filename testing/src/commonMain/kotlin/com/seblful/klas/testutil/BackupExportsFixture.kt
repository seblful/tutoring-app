package com.seblful.klas.testutil

import com.seblful.klas.data.backup.BackupCipher
import com.seblful.klas.data.backup.BackupExports
import com.seblful.klas.data.backup.BackupFolder
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.storage.DocumentStore

fun RepositoryTestEnv.backupExports(
    documents: DocumentStore = FakeDocumentStore(),
    cipher: BackupCipher = BackupCipher(FakeSealCrypto()),
    folders: BackupFolder = FakeBackupFolder(),
    clock: WallClock = this.clock,
): BackupExports = BackupExports(backupManager, settingsWorkflow, documents, cipher, folders, clock)
