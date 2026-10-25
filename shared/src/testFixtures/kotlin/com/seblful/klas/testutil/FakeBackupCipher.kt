package com.seblful.klas.testutil

import com.seblful.klas.data.backup.BackupCipher
import com.seblful.klas.data.backup.WrongPasswordException

/** Records orchestration; Android's cipher tests verify the real sealed format and encryption. */
class FakeBackupCipher : BackupCipher {
    var failing = false

    override suspend fun seal(json: String, password: String): String {
        if (failing) error("Cannot seal backup")
        return "sealed:$password:$json"
    }

    override suspend fun open(text: String, password: String): String {
        val prefix = "sealed:$password:"
        if (!text.startsWith(prefix)) throw WrongPasswordException()
        return text.removePrefix(prefix)
    }
}
