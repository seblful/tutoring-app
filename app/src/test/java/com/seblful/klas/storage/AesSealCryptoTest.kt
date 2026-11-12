package com.seblful.klas.storage

import com.seblful.klas.data.backup.BackupCipher
import com.seblful.klas.data.backup.WrongPasswordException
import com.seblful.klas.testutil.TestEnvRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The real cryptography under [BackupCipher]; the format itself is held by the common `BackupCipherTest`. */
@RunWith(RobolectricTestRunner::class)
class AesSealCryptoTest {

    @get:Rule
    val env = TestEnvRule()
    private val cipher = BackupCipher(AesSealCrypto(Dispatchers.Unconfined))

    @Test
    fun `the key derivation is PBKDF2-HMAC-SHA256`() {
        // RFC 7914 §11's test vectors for PBKDF2-HMAC-SHA256.
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            AesSealCrypto.pbkdf2("password".toByteArray(), "salt".toByteArray(), 1).toHex(),
        )
        assertEquals(
            "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            AesSealCrypto.pbkdf2("password".toByteArray(), "salt".toByteArray(), 2).toHex(),
        )
    }

    @Test
    fun `a sealed backup is unreadable and opens with its password`() = runTest {
        env.fixtures.student(name = "Аня")
        val plain = env.backupManager.exportJson()

        val sealed = cipher.seal(plain, "лето 2026")

        assertFalse("nothing readable is left in the file", sealed.contains("Аня"))
        assertEquals(plain, cipher.open(sealed, "лето 2026"))
    }

    @Test
    fun `GCM's tag refuses a wrong password`() = runTest {
        val sealed = cipher.seal(env.backupManager.exportJson(), "right")

        val refused = runCatching { cipher.open(sealed, "wrong") }.exceptionOrNull()

        assertTrue("was $refused", refused is WrongPasswordException)
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
