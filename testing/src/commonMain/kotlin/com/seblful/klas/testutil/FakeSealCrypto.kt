package com.seblful.klas.testutil

import com.seblful.klas.data.backup.SealCrypto
import kotlin.random.Random

/**
 * A stand-in for the device's cryptography, so the real [com.seblful.klas.data.backup.BackupCipher] envelope runs
 * in common tests: the "key" is the password and salt, and sealing only puts it in front of the text. Each
 * platform's adapter test verifies the cryptography itself.
 */
class FakeSealCrypto : SealCrypto {
    var failing = false

    /** The iteration count of every key derived, newest last. */
    val derivations = mutableListOf<Int>()

    override suspend fun deriveKey(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        derivations += iterations
        return password + salt
    }

    override fun randomBytes(count: Int): ByteArray = Random.nextBytes(count)

    override suspend fun seal(key: ByteArray, iv: ByteArray, plain: ByteArray): ByteArray {
        if (failing) error("Cannot seal backup")
        return key + iv + plain
    }

    override suspend fun open(key: ByteArray, iv: ByteArray, sealed: ByteArray): ByteArray? {
        val prefix = key + iv
        if (sealed.size < prefix.size || !sealed.copyOf(prefix.size).contentEquals(prefix)) return null
        return sealed.copyOfRange(prefix.size, sealed.size)
    }
}
