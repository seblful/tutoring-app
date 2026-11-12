package com.seblful.klas.storage

import com.seblful.klas.data.backup.BackupFailure
import com.seblful.klas.data.backup.PasswordNeededException
import com.seblful.klas.data.backup.SealedBackup
import com.seblful.klas.data.backup.WrongPasswordException
import com.seblful.klas.testutil.TestEnvRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AesBackupCipherTest {

    @get:Rule
    val env = TestEnvRule()
    private val cipher = AesBackupCipher(Dispatchers.Unconfined)

    @Test
    fun `the key derivation is PBKDF2-HMAC-SHA256`() {
        // RFC 7914 §11's test vectors for PBKDF2-HMAC-SHA256.
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            AesBackupCipher.pbkdf2("password".toByteArray(), "salt".toByteArray(), 1).toHex(),
        )
        assertEquals(
            "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            AesBackupCipher.pbkdf2("password".toByteArray(), "salt".toByteArray(), 2).toHex(),
        )
    }

    @Test
    fun `a sealed backup opens with its password and restores as the plain one would`() = runTest {
        env.fixtures.student(name = "Аня")
        val plain = env.backupManager.exportJson()

        val sealed = cipher.seal(plain, "лето 2026")

        assertFalse("nothing readable is left in the file", sealed.contains("Аня"))
        assertEquals(plain, cipher.open(sealed, "лето 2026"))
        assertEquals(1, env.backupManager.preview(cipher.open(sealed, "лето 2026")).students)
    }

    @Test
    fun `a wrong password is told apart from a broken file`() = runTest {
        val sealed = cipher.seal(env.backupManager.exportJson(), "right")

        val refused = runCatching { cipher.open(sealed, "wrong") }.exceptionOrNull()

        assertTrue("was $refused", refused is WrongPasswordException)
    }

    @Test
    fun `a sealed file asks for its password instead of reading as malformed`() = runTest {
        val sealed = cipher.seal(env.backupManager.exportJson(), "right")

        assertThrows(PasswordNeededException::class.java) { env.backupManager.parse(sealed) }
    }

    @Test
    fun `a file asking for an unworkable key derivation is malformed, not a hang`() = runTest {
        val sealed = SealedBackup.decode(cipher.seal("{}", "pw"))
        val greedy = sealed.copy(iterations = Int.MAX_VALUE).encode()

        val refused = runCatching { cipher.open(greedy, "pw") }.exceptionOrNull()

        assertEquals(BackupFailure.Malformed, refused?.let(BackupFailure::of))
    }

    @Test
    fun `each seal has its own salt and iv`() = runTest {
        val plain = env.backupManager.exportJson()
        val first = SealedBackup.decode(cipher.seal(plain, "same"))
        val second = SealedBackup.decode(cipher.seal(plain, "same"))

        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.iv, second.iv)
        assertEquals(AesBackupCipher.ITERATIONS, first.iterations)
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
