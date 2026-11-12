package com.seblful.klas.data.backup

import dev.zacsweers.metro.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/**
 * A backup file sealed with a password: the plain file encrypted whole, beside what opening it needs. Its
 * own envelope, told apart from a plain backup by [klasSealed], so [BackupManager.parse] can ask for the
 * password instead of calling the file broken. There is no way back without the password — by design.
 *
 * Byte fields are Base64. The key is PBKDF2-HMAC-SHA256 over the password's UTF-8 with [salt] and
 * [iterations]; the data is AES-256-GCM under that key and [iv], with a 128-bit tag. [iterations] is kept
 * per file, so a later build can raise it and still open older files.
 */
@Serializable
data class SealedBackup(
    val klasSealed: Int = FORMAT,
    val iterations: Int,
    val salt: String,
    val iv: String,
    val data: String,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val FORMAT = 1

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** Far above any build's own; a file asking for more would hang the phone deriving its key. */
        const val MAX_ITERATIONS = 10_000_000

        /**
         * Throws [kotlinx.serialization.SerializationException] when [text] is not a sealed backup, and
         * [MalformedBackupException] when it asks for an unworkable key derivation.
         */
        fun decode(text: String): SealedBackup = json.decodeFromString(serializer(), text).also {
            if (it.iterations !in 1..MAX_ITERATIONS) throw MalformedBackupException("iterations ${it.iterations}")
        }

        fun bytes(field: String): ByteArray = Base64.decode(field)

        fun field(bytes: ByteArray): String = Base64.encode(bytes)
    }
}

/**
 * The device's cryptography under [BackupCipher] — a platform port; the format, its constants and its errors
 * stay common. Adapters run the slow calls off the main thread.
 */
interface SealCrypto {
    /** PBKDF2-HMAC-SHA256 over [password] with [salt] and [iterations]: a 32-byte AES-256 key. */
    suspend fun deriveKey(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray

    /** [count] bytes from a cryptographically secure source. */
    fun randomBytes(count: Int): ByteArray

    /** AES-256-GCM of [plain] under [key] and the 96-bit [iv], its 128-bit tag appended. */
    suspend fun seal(key: ByteArray, iv: ByteArray, plain: ByteArray): ByteArray

    /** [sealed] opened under [key] and [iv], or null when its tag does not match — the wrong key. */
    suspend fun open(key: ByteArray, iv: ByteArray, sealed: ByteArray): ByteArray?
}

/**
 * Seals a backup with a password and opens one, in the [SealedBackup] format on every platform: a file sealed
 * on one phone opens on the other because the envelope and its constants live here, not in each adapter.
 */
@Inject
class BackupCipher(private val crypto: SealCrypto) {
    suspend fun seal(json: String, password: String): String {
        val salt = crypto.randomBytes(SALT_BYTES)
        val iv = crypto.randomBytes(IV_BYTES)
        val data = crypto.seal(key(password, salt, ITERATIONS), iv, json.encodeToByteArray())
        return SealedBackup(
            iterations = ITERATIONS,
            salt = SealedBackup.field(salt),
            iv = SealedBackup.field(iv),
            data = SealedBackup.field(data),
        ).encode()
    }

    /**
     * The plain backup inside [text]. Throws [WrongPasswordException] when [password] does not open it, and
     * what [SealedBackup.decode] throws when [text] is not a workable sealed backup.
     */
    suspend fun open(text: String, password: String): String {
        val sealed = SealedBackup.decode(text)
        val key = key(password, SealedBackup.bytes(sealed.salt), sealed.iterations)
        // GCM checks the tag before giving anything back, so a wrong key fails here rather than as garbage.
        val plain = crypto.open(key, SealedBackup.bytes(sealed.iv), SealedBackup.bytes(sealed.data))
            ?: throw WrongPasswordException()
        return plain.decodeToString()
    }

    private suspend fun key(password: String, salt: ByteArray, iterations: Int): ByteArray {
        require(password.isNotEmpty()) { "A sealed backup needs a password" }
        return crypto.deriveKey(password.encodeToByteArray(), salt, iterations)
    }

    companion object {
        /** What this build seals with; each file keeps its own, so raising it still opens older files. */
        const val ITERATIONS = 310_000
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
    }
}

/** The file is sealed ([SealedBackup]); it can only be read once the tutor gives its password. */
class PasswordNeededException : IllegalStateException("This backup is sealed with a password")

class WrongPasswordException(cause: Throwable? = null) : IllegalArgumentException("Wrong password", cause)
