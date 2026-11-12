package com.seblful.klas.testutil

import com.seblful.klas.data.backup.BackupCipher
import com.seblful.klas.data.backup.SealedBackup
import com.seblful.klas.data.backup.WrongPasswordException

/**
 * Seals into a real [SealedBackup] envelope, so restore recognises the file as sealed, but stores the password and
 * text unencrypted. Each platform's cipher tests verify the encryption itself.
 */
class FakeBackupCipher : BackupCipher {
    var failing = false

    override suspend fun seal(json: String, password: String): String {
        if (failing) error("Cannot seal backup")
        return SealedBackup(
            iterations = 1,
            salt = SealedBackup.field(password.encodeToByteArray()),
            iv = SealedBackup.field(ByteArray(0)),
            data = SealedBackup.field(json.encodeToByteArray()),
        ).encode()
    }

    override suspend fun open(text: String, password: String): String {
        val sealed = SealedBackup.decode(text)
        if (SealedBackup.bytes(sealed.salt).decodeToString() != password) throw WrongPasswordException()
        return SealedBackup.bytes(sealed.data).decodeToString()
    }
}
