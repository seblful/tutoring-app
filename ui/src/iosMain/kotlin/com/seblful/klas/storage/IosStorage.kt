package com.seblful.klas.storage

import com.seblful.klas.data.backup.BackupCipher
import com.seblful.klas.data.backup.BackupFolder
import com.seblful.klas.data.backup.RestoreSnapshots
import com.seblful.klas.data.backup.SealedBackup
import com.seblful.klas.data.backup.WrongPasswordException
import com.seblful.klas.di.Computation
import com.seblful.klas.di.Io
import com.seblful.klas.ui.common.presentOnTop
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCOptionECBMode
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA256
import platform.CoreCrypto.kCCSuccess
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkCreationWithSecurityScope
import platform.Foundation.NSURLBookmarkResolutionWithSecurityScope
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.UIKit.UIActivityViewController
import kotlin.time.Instant

/** A picked document: a copy in the sandbox, or a security-scoped file elsewhere. */
@ContributesBinding(AppScope::class)
@Inject
class IosDocumentStore(@param:Io private val io: CoroutineDispatcher) : DocumentStore {

    override suspend fun read(ref: DocumentRef): String = withContext(io) {
        url(ref).accessing { requireNotNull(it.readBytes()) { "Could not open the file" } }.decodeToString()
    }

    override suspend fun write(ref: DocumentRef, text: String) = write(ref, text.encodeToByteArray())

    override suspend fun write(ref: DocumentRef, bytes: ByteArray) = withContext(io) {
        url(ref).accessing { it.writeBytes(bytes) }
    }

    private fun url(ref: DocumentRef): NSURL = requireNotNull(NSURL.URLWithString(ref.value)) { "Not a file" }
}

/** A file handed to the share sheet from a folder of its own, so two shares never collide. */
@ContributesBinding(AppScope::class)
@Inject
class IosDocumentSharing(@param:Io private val io: CoroutineDispatcher) : DocumentSharing {

    override suspend fun share(filename: String, mimeType: String, subject: String, bytes: ByteArray) {
        require(filename.none { it == '/' }) { "A share filename may not contain a path" }
        val file = withContext(io) {
            val directory = NSURL.fileURLWithPath(NSTemporaryDirectory())
                .URLByAppendingPathComponent(NSUUID().UUIDString)!!
            @OptIn(ExperimentalForeignApi::class)
            NSFileManager.defaultManager.createDirectoryAtURL(
                directory,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
            directory.URLByAppendingPathComponent(filename)!!.also { it.writeBytes(bytes) }
        }
        withContext(Dispatchers.Main) {
            presentOnTop(UIActivityViewController(activityItems = listOf(file), applicationActivities = null))
        }
    }
}

/** The data from before a restore, kept in Application Support until the tutor keeps the restore. */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosRestoreSnapshots(@param:Io private val io: CoroutineDispatcher) : RestoreSnapshots {

    private val file = appFile("restore-undo.json")
    private val stamp = appFile("restore-undo.taken")

    private val kept = MutableStateFlow(
        stamp.readBytes()?.decodeToString()?.toLongOrNull()
            ?.takeIf { file.exists() }
            ?.let(Instant::fromEpochMilliseconds),
    )
    override val keptAt: StateFlow<Instant?> = kept.asStateFlow()

    override suspend fun save(json: String, takenAt: Instant) = withContext(io) {
        file.writeBytes(json.encodeToByteArray())
        stamp.writeBytes(takenAt.toEpochMilliseconds().toString().encodeToByteArray())
        kept.value = takenAt
    }

    override suspend fun read(): String? = withContext(io) { file.readBytes()?.decodeToString() }

    override suspend fun clear() = withContext(io) {
        file.delete()
        stamp.delete()
        kept.value = null
    }
}

/**
 * [BackupCipher] in the [SealedBackup] format Android writes: PBKDF2-HMAC-SHA256 from CommonCrypto, and
 * AES-256-GCM as [AesGcm] over CommonCrypto's AES — CommonCrypto has no public GCM.
 */
@ContributesBinding(AppScope::class)
@Inject
class IosBackupCipher(@param:Computation private val computation: CoroutineDispatcher) : BackupCipher {

    override suspend fun seal(json: String, password: String): String = withContext(computation) {
        val salt = randomBytes(SALT_BYTES)
        val iv = randomBytes(IV_BYTES)
        val sealed = gcm(password, salt, ITERATIONS).seal(iv, json.encodeToByteArray())
        SealedBackup(
            iterations = ITERATIONS,
            salt = SealedBackup.field(salt),
            iv = SealedBackup.field(iv),
            data = SealedBackup.field(sealed),
        ).encode()
    }

    override suspend fun open(text: String, password: String): String = withContext(computation) {
        val sealed = SealedBackup.decode(text)
        val gcm = gcm(password, SealedBackup.bytes(sealed.salt), sealed.iterations)
        val plain = try {
            gcm.open(SealedBackup.bytes(sealed.iv), SealedBackup.bytes(sealed.data))
        } catch (e: IllegalArgumentException) {
            throw WrongPasswordException(e)
        }
        plain.decodeToString()
    }

    private fun gcm(password: String, salt: ByteArray, iterations: Int): AesGcm {
        require(password.isNotEmpty()) { "A sealed backup needs a password" }
        val key = pbkdf2(password.encodeToByteArray(), salt, iterations)
        return AesGcm { block -> aesBlock(key, block) }
    }

    internal companion object {
        /** Android's: the two must agree, or a backup sealed on one would take a different key on the other. */
        const val ITERATIONS = 310_000
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val KEY_BYTES = 32
        private const val BLOCK_BYTES = 16

        @OptIn(ExperimentalForeignApi::class)
        fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
            val key = ByteArray(KEY_BYTES)
            val status = password.usePinned { p ->
                salt.usePinned { s ->
                    key.usePinned { k ->
                        CCKeyDerivationPBKDF(
                            kCCPBKDF2,
                            p.addressOf(0).reinterpret<ByteVar>(),
                            password.size.convert(),
                            s.addressOf(0).reinterpret<UByteVar>(),
                            salt.size.convert(),
                            kCCPRFHmacAlgSHA256,
                            iterations.convert(),
                            k.addressOf(0).reinterpret<UByteVar>(),
                            KEY_BYTES.convert(),
                        )
                    }
                }
            }
            check(status == kCCSuccess) { "Could not derive the key" }
            return key
        }

        /** One AES block under [key], in ECB mode: what [AesGcm] builds its counter mode and tag from. */
        @OptIn(ExperimentalForeignApi::class)
        fun aesBlock(key: ByteArray, block: ByteArray): ByteArray {
            val out = ByteArray(BLOCK_BYTES)
            val status = key.usePinned { k ->
                block.usePinned { b ->
                    out.usePinned { o ->
                        CCCrypt(
                            kCCEncrypt,
                            kCCAlgorithmAES,
                            kCCOptionECBMode,
                            k.addressOf(0),
                            key.size.convert(),
                            null,
                            b.addressOf(0),
                            BLOCK_BYTES.convert(),
                            o.addressOf(0),
                            BLOCK_BYTES.convert(),
                            null,
                        )
                    }
                }
            }
            check(status == kCCSuccess) { "AES failed" }
            return out
        }

        @OptIn(ExperimentalForeignApi::class)
        private fun randomBytes(count: Int): ByteArray = ByteArray(count).also { bytes ->
            val status = bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, count.convert(), it.addressOf(0)) }
            check(status == errSecSuccess) { "No secure random bytes" }
        }
    }
}

/**
 * A backup folder the tutor picked, kept across launches as a security-scoped bookmark in the user
 * defaults: the folder string is the picked URL, the bookmark is what reopens it.
 */
@ContributesBinding(AppScope::class, binding = binding<BackupFolder>())
@ContributesBinding(AppScope::class, binding = binding<FolderGrants>())
@SingleIn(AppScope::class)
@Inject
class IosBackupFolder(@param:Io private val io: CoroutineDispatcher) : BackupFolder, FolderGrants {

    private val defaults get() = NSUserDefaults.standardUserDefaults

    @OptIn(ExperimentalForeignApi::class)
    override fun keep(folder: String) {
        val url = NSURL.URLWithString(folder) ?: return
        val bookmark = url.accessing {
            it.bookmarkDataWithOptions(NSURLBookmarkCreationWithSecurityScope, null, null, null)
        } ?: return
        defaults.setObject(bookmark, forKey = key(folder))
    }

    override fun release(folder: String) = defaults.removeObjectForKey(key(folder))

    override suspend fun name(folder: String): String? = NSURL.URLWithString(folder)?.lastPathComponent

    override suspend fun write(folder: String, name: String, text: String) = withContext(io) {
        open(folder).accessing { it.URLByAppendingPathComponent(name)!!.writeBytes(text.encodeToByteArray()) }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun list(folder: String): List<String> = withContext(io) {
        open(folder).accessing { directory ->
            NSFileManager.defaultManager.contentsOfDirectoryAtPath(directory.path.orEmpty(), error = null)
                .orEmpty().filterIsInstance<String>()
        }
    }

    override suspend fun delete(folder: String, name: String) = withContext(io) {
        open(folder).accessing { it.URLByAppendingPathComponent(name)!!.delete() }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun open(folder: String): NSURL {
        val bookmark = defaults.dataForKey(key(folder))
            ?: return requireNotNull(NSURL.URLWithString(folder)) { "Not a folder" }
        return requireNotNull(
            NSURL.URLByResolvingBookmarkData(
                bookmark,
                options = NSURLBookmarkResolutionWithSecurityScope,
                relativeToURL = null,
                bookmarkDataIsStale = null,
                error = null,
            ),
        ) { "The backup folder is no longer available" }
    }

    private fun key(folder: String) = "backup-folder:$folder"
}
