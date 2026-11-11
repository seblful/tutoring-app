package com.seblful.klas.storage

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/** The common GCM against the JDK's: what iOS seals, Android opens, and the other way round. */
class AesGcmTest {

    @Test
    fun `it matches the specification's AES-256 test cases`() {
        val key = ByteArray(32)
        val iv = ByteArray(12)
        assertContentEquals(hex("530f8afbc74536b9a963b4f1c4cb738b"), gcm(key).seal(iv, ByteArray(0)))
        assertContentEquals(
            hex("cea7403d4d606b6e074ec5d3baf39d18" + "d0d1c8a799996bf0265b98b5d48ab919"),
            gcm(key).seal(iv, ByteArray(16)),
        )
    }

    @Test
    fun `what it seals the JDK opens and the other way round`() {
        val random = Random(42)
        for (size in listOf(0, 1, 15, 16, 17, 31, 32, 33, 1000, 4099)) {
            val key = random.nextBytes(32)
            val iv = random.nextBytes(12)
            val plain = random.nextBytes(size)

            val sealed = gcm(key).seal(iv, plain)
            assertContentEquals(jdk(Cipher.ENCRYPT_MODE, key, iv).doFinal(plain), sealed, "size $size")
            assertContentEquals(plain, jdk(Cipher.DECRYPT_MODE, key, iv).doFinal(sealed))
            assertContentEquals(plain, gcm(key).open(iv, sealed))
        }
    }

    @Test
    fun `a changed byte or the wrong key is refused`() {
        val key = ByteArray(32) { it.toByte() }
        val iv = ByteArray(12) { (it * 3).toByte() }
        val sealed = gcm(key).seal(iv, "a sealed backup".encodeToByteArray())

        val tampered = sealed.copyOf().also { it[3] = (it[3] + 1).toByte() }
        assertFailsWith<IllegalArgumentException> { gcm(key).open(iv, tampered) }
        assertFailsWith<IllegalArgumentException> { gcm(ByteArray(32)).open(iv, sealed) }
    }

    private fun gcm(key: ByteArray) = AesGcm { block ->
        Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }
            .doFinal(block)
    }

    private fun jdk(mode: Int, key: ByteArray, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        }

    private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
