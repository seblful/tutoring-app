package com.seblful.klas.data.backup

import com.seblful.klas.testutil.FakeSealCrypto
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BackupCipherTest {
    private val env = TestEnv()
    private val crypto = FakeSealCrypto()
    private val cipher = BackupCipher(crypto)

    @AfterTest
    fun close() = env.close()

    @Test
    fun `a sealed backup opens with its password and restores as the plain one would`() = runTest {
        env.fixtures.student(name = "Аня")
        val plain = env.backupManager.exportJson()

        val opened = cipher.open(cipher.seal(plain, "лето 2026"), "лето 2026")

        assertEquals(plain, opened)
        assertEquals(1, env.backupManager.preview(opened).students)
    }

    @Test
    fun `a wrong password is told apart from a broken file`() = runTest {
        val sealed = cipher.seal(env.backupManager.exportJson(), "right")

        assertFailsWith<WrongPasswordException> { cipher.open(sealed, "wrong") }
    }

    @Test
    fun `a sealed file asks for its password instead of reading as malformed`() = runTest {
        val sealed = cipher.seal(env.backupManager.exportJson(), "right")

        assertFailsWith<PasswordNeededException> { env.backupManager.parse(sealed) }
    }

    @Test
    fun `a file asking for an unworkable key derivation is malformed, not a hang`() = runTest {
        val greedy = SealedBackup.decode(cipher.seal("{}", "pw")).copy(iterations = Int.MAX_VALUE).encode()

        val refused = runCatching { cipher.open(greedy, "pw") }.exceptionOrNull()

        assertEquals(BackupFailure.Malformed, refused?.let(BackupFailure::of))
        assertTrue(Int.MAX_VALUE !in crypto.derivations, "no key was derived for it")
    }

    @Test
    fun `each seal has its own salt and iv, at this build's iterations`() = runTest {
        val first = SealedBackup.decode(cipher.seal("{}", "same"))
        val second = SealedBackup.decode(cipher.seal("{}", "same"))

        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.iv, second.iv)
        assertEquals(BackupCipher.ITERATIONS, first.iterations)
    }

    @Test
    fun `a file opens at the iterations it was sealed with`() = runTest {
        val older = SealedBackup.decode(cipher.seal("{}", "pw")).copy(iterations = 1000).encode()

        runCatching { cipher.open(older, "pw") }

        assertEquals(1000, crypto.derivations.last())
    }

    @Test
    fun `an empty password seals nothing`() = runTest {
        assertFailsWith<IllegalArgumentException> { cipher.seal("{}", "") }
    }
}
