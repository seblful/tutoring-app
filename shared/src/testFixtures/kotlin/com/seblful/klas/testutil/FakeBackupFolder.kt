package com.seblful.klas.testutil

import com.seblful.klas.data.backup.BackupFolder

/** [BackupFolder] as a map of folder to files; [revoked] folders refuse every call, as a lost grant does. */
class FakeBackupFolder : BackupFolder {
    val files = mutableMapOf<String, MutableMap<String, String>>()
    val revoked = mutableSetOf<String>()

    override suspend fun write(folder: String, name: String, text: String) {
        reach(folder)[name] = text
    }

    override suspend fun list(folder: String): List<String> = reach(folder).keys.toList()

    override suspend fun delete(folder: String, name: String) {
        reach(folder).remove(name)
    }

    private fun reach(folder: String): MutableMap<String, String> {
        if (folder in revoked) throw IllegalStateException("No access to $folder")
        return files.getOrPut(folder) { mutableMapOf() }
    }
}
