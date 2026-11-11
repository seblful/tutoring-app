package com.seblful.klas.storage

/**
 * AES-GCM (NIST SP 800-38D) over a platform's single-block AES, for a platform whose public crypto API has
 * AES but not GCM — iOS's CommonCrypto. The output is Java's `AES/GCM/NoPadding`: the ciphertext followed
 * by its 128-bit tag, so a backup sealed on either platform opens on the other.
 *
 * Only what a sealed backup uses: a 96-bit IV and no additional authenticated data.
 */
internal class AesGcm(private val encryptBlock: (ByteArray) -> ByteArray) {

    private val hashKey: ByteArray = encryptBlock(ByteArray(BLOCK))

    /** [plain] sealed under [iv]: ciphertext, then the tag. */
    fun seal(iv: ByteArray, plain: ByteArray): ByteArray {
        val cipher = ctr(iv, plain)
        return cipher + tag(iv, cipher)
    }

    /** [sealed] opened under [iv]; throws [IllegalArgumentException] when the tag does not match. */
    fun open(iv: ByteArray, sealed: ByteArray): ByteArray {
        require(sealed.size >= TAG) { "Too short to carry a tag" }
        val cipher = sealed.copyOfRange(0, sealed.size - TAG)
        val expected = tag(iv, cipher)
        val actual = sealed.copyOfRange(sealed.size - TAG, sealed.size)
        // Compared in full whatever differs first, so the time taken says nothing about the tag.
        var difference = 0
        for (i in 0 until TAG) difference = difference or (expected[i].toInt() xor actual[i].toInt())
        require(difference == 0) { "The tag does not match" }
        return ctr(iv, cipher)
    }

    /** The keystream from the counter block after J0, XORed over [input]. */
    private fun ctr(iv: ByteArray, input: ByteArray): ByteArray {
        val counter = j0(iv)
        val output = ByteArray(input.size)
        var offset = 0
        while (offset < input.size) {
            increment(counter)
            val stream = encryptBlock(counter.copyOf())
            val length = minOf(BLOCK, input.size - offset)
            for (i in 0 until length) output[offset + i] = (input[offset + i].toInt() xor stream[i].toInt()).toByte()
            offset += length
        }
        return output
    }

    private fun tag(iv: ByteArray, cipher: ByteArray): ByteArray {
        var hash = ByteArray(BLOCK)
        var offset = 0
        while (offset < cipher.size) {
            val block = ByteArray(BLOCK)
            cipher.copyInto(block, 0, offset, minOf(offset + BLOCK, cipher.size))
            hash = multiply(xor(hash, block), hashKey)
            offset += BLOCK
        }
        // No additional data: its length is zero; the ciphertext's is in bits.
        val lengths = ByteArray(BLOCK)
        val bits = cipher.size.toLong() * Byte.SIZE_BITS
        for (i in 0 until Long.SIZE_BYTES) lengths[BLOCK - 1 - i] = (bits ushr (Byte.SIZE_BITS * i)).toByte()
        hash = multiply(xor(hash, lengths), hashKey)
        return xor(encryptBlock(j0(iv)), hash)
    }

    private fun j0(iv: ByteArray): ByteArray {
        require(iv.size == IV) { "A 96-bit IV" }
        return iv.copyOf(BLOCK).also { it[BLOCK - 1] = 1 }
    }

    private companion object {
        const val BLOCK = 16
        const val TAG = 16
        const val IV = 12

        /** The low 32 bits of the counter block, big-endian, step by one. */
        fun increment(counter: ByteArray) {
            for (i in BLOCK - 1 downTo BLOCK - Int.SIZE_BYTES) {
                counter[i] = (counter[i] + 1).toByte()
                if (counter[i] != 0.toByte()) return
            }
        }

        fun xor(a: ByteArray, b: ByteArray): ByteArray = ByteArray(BLOCK) { (a[it].toInt() xor b[it].toInt()).toByte() }

        /** Multiplication in GF(2^128) with GCM's bit order (SP 800-38D, algorithm 1). */
        private const val BYTE_MASK = 0xFF

        fun multiply(x: ByteArray, y: ByteArray): ByteArray {
            val z = ByteArray(BLOCK)
            val v = y.copyOf()
            for (i in 0 until BLOCK * Byte.SIZE_BITS) {
                val bit = (x[i / Byte.SIZE_BITS].toInt() shr (Byte.SIZE_BITS - 1 - i % Byte.SIZE_BITS)) and 1
                if (bit == 1) for (j in 0 until BLOCK) z[j] = (z[j].toInt() xor v[j].toInt()).toByte()
                val carry = v[BLOCK - 1].toInt() and 1
                for (j in BLOCK - 1 downTo 1) {
                    val carried = (v[j - 1].toInt() and 1) shl (Byte.SIZE_BITS - 1)
                    v[j] = ((v[j].toInt() and BYTE_MASK ushr 1) or carried).toByte()
                }
                v[0] = (v[0].toInt() and BYTE_MASK ushr 1).toByte()
                if (carry == 1) v[0] = (v[0].toInt() xor R).toByte()
            }
            return z
        }

        /** The reduction polynomial's top byte, 11100001. */
        const val R = 0xE1
    }
}
