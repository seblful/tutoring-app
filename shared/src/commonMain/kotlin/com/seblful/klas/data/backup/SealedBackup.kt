package com.seblful.klas.data.backup

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
 * Seals a backup with a password and opens one — a platform port, since the key derivation and the cipher
 * are the platform's. See [SealedBackup] for the format.
 */
interface BackupCipher {
    suspend fun seal(json: String, password: String): String

    /** The plain backup inside [text]. Throws [WrongPasswordException] when [password] does not open it. */
    suspend fun open(text: String, password: String): String
}

/** The file is sealed ([SealedBackup]); it can only be read once the tutor gives its password. */
class PasswordNeededException : IllegalStateException("This backup is sealed with a password")

class WrongPasswordException(cause: Throwable? = null) : IllegalArgumentException("Wrong password", cause)
