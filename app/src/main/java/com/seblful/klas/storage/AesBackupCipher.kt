package com.seblful.klas.storage

import com.seblful.klas.data.backup.BackupCipher
import com.seblful.klas.data.backup.SealedBackup
import com.seblful.klas.data.backup.WrongPasswordException
import com.seblful.klas.di.Computation
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

/**
 * [BackupCipher] with the platform's own primitives — see [SealedBackup] for the format. PBKDF2 is spelled
 * out over `Mac` because `SecretKeyFactory`'s PBKDF2WithHmacSHA256 arrived only in API 26.
 */
@ContributesBinding(AppScope::class)
@Inject
class AesBackupCipher(
    // Hundreds of thousands of HMAC rounds: well clear of the main thread.
    @param:Computation private val computation: CoroutineDispatcher,
) : BackupCipher {
    private val random = SecureRandom()

    override suspend fun seal(json: String, password: String): String = withContext(computation) {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, ITERATIONS, iv)
        SealedBackup(
            iterations = ITERATIONS,
            salt = SealedBackup.field(salt),
            iv = SealedBackup.field(iv),
            data = SealedBackup.field(cipher.doFinal(json.encodeToByteArray())),
        ).encode()
    }

    override suspend fun open(text: String, password: String): String = withContext(computation) {
        val sealed = SealedBackup.decode(text)
        val cipher = cipher(
            Cipher.DECRYPT_MODE,
            password,
            SealedBackup.bytes(sealed.salt),
            sealed.iterations,
            SealedBackup.bytes(sealed.iv),
        )
        // GCM checks the tag before giving anything back, so a wrong key fails here rather than as garbage.
        val plain = try {
            cipher.doFinal(SealedBackup.bytes(sealed.data))
        } catch (e: GeneralSecurityException) {
            throw WrongPasswordException(e)
        }
        plain.decodeToString()
    }

    private fun cipher(mode: Int, password: String, salt: ByteArray, iterations: Int, iv: ByteArray): Cipher {
        require(password.isNotEmpty()) { "A sealed backup needs a password" }
        val key = SecretKeySpec(pbkdf2(password.encodeToByteArray(), salt, iterations), "AES")
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, key, GCMParameterSpec(TAG_BITS, iv)) }
    }

    internal companion object {
        const val ITERATIONS = 310_000
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        /** RFC 8018 PBKDF2 with HMAC-SHA256, one 32-byte block — exactly an AES-256 key. */
        fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(password, "HmacSHA256")) }
            mac.update(salt)
            var block = mac.doFinal(byteArrayOf(0, 0, 0, 1))
            val key = block.copyOf()
            repeat(iterations - 1) {
                block = mac.doFinal(block)
                for (i in key.indices) key[i] = key[i] xor block[i]
            }
            return key
        }
    }
}
