package com.seblful.klas.storage

import com.seblful.klas.data.backup.SealCrypto
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
 * [SealCrypto] with the platform's own primitives. PBKDF2 is spelled out over `Mac` because
 * `SecretKeyFactory`'s PBKDF2WithHmacSHA256 arrived only in API 26.
 */
@ContributesBinding(AppScope::class)
@Inject
class AesSealCrypto(
    // Hundreds of thousands of HMAC rounds: well clear of the main thread.
    @param:Computation private val computation: CoroutineDispatcher,
) : SealCrypto {
    private val random = SecureRandom()

    override suspend fun deriveKey(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray =
        withContext(computation) { pbkdf2(password, salt, iterations) }

    override fun randomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)

    override suspend fun seal(key: ByteArray, iv: ByteArray, plain: ByteArray): ByteArray =
        withContext(computation) { gcm(Cipher.ENCRYPT_MODE, key, iv).doFinal(plain) }

    override suspend fun open(key: ByteArray, iv: ByteArray, sealed: ByteArray): ByteArray? =
        withContext(computation) {
            val cipher = gcm(Cipher.DECRYPT_MODE, key, iv)
            try {
                cipher.doFinal(sealed)
            } catch (_: GeneralSecurityException) {
                null
            }
        }

    private fun gcm(mode: Int, key: ByteArray, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        }

    internal companion object {
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
