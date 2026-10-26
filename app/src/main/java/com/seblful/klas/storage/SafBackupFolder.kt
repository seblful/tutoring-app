package com.seblful.klas.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.net.toUri
import com.seblful.klas.data.backup.BackupFolder
import com.seblful.klas.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeping and giving up access to a picked folder, and naming it for the screen. */
interface FolderGrants {
    /** Keeps access to [folder] across restarts. Throws if the picker granted none to keep. */
    fun keep(folder: String)

    fun release(folder: String)

    /** The folder's own name, or null when the provider no longer answers for it. */
    suspend fun name(folder: String): String?
}

/**
 * [BackupFolder] over a SAF document tree — a folder on the phone, an SD card or a cloud provider's — through
 * `DocumentsContract`, so it needs no library and works back to API 24. Only folders kept with [keep]
 * survive a restart; a grant the tutor or the provider withdrew makes every call throw.
 */
@ContributesBinding(AppScope::class, binding = binding<BackupFolder>())
@ContributesBinding(AppScope::class, binding = binding<FolderGrants>())
@SingleIn(AppScope::class)
class SafBackupFolder @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BackupFolder, FolderGrants {

    private val resolver get() = context.contentResolver

    override suspend fun write(folder: String, name: String, text: String) = withContext(Dispatchers.IO) {
        val tree = folder.toUri()
        val file = DocumentsContract.createDocument(resolver, root(tree), MIME_TYPE, name)
            ?: error("Could not create $name")
        var written = false
        try {
            resolver.openOutputStream(file)?.bufferedWriter()?.use { it.write(text) }
                ?: error("Could not open $name")
            written = true
        } finally {
            // A copy cut short would count as one of the kept ones, and could push out a good one.
            if (!written) runCatching { DocumentsContract.deleteDocument(resolver, file) }
        }
    }

    override suspend fun list(folder: String): List<String> = withContext(Dispatchers.IO) {
        children(folder.toUri()).map { it.second }
    }

    override suspend fun delete(folder: String, name: String) {
        withContext(Dispatchers.IO) {
            val tree = folder.toUri()
            children(tree).filter { it.second == name }.forEach { (id, _) ->
                DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id))
            }
        }
    }

    override fun keep(folder: String) {
        resolver.takePersistableUriPermission(folder.toUri(), ACCESS)
    }

    override fun release(folder: String) {
        // Already gone is as good as released.
        runCatching { resolver.releasePersistableUriPermission(folder.toUri(), ACCESS) }
    }

    override suspend fun name(folder: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.query(root(folder.toUri()), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
    }

    private fun root(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    /** (document id, display name) of each file directly in the folder. */
    private fun children(tree: Uri): List<Pair<String, String>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
        return resolver.query(uri, columns, null, null, null)?.use { row ->
            buildList { while (row.moveToNext()) add(row.getString(0) to row.getString(1)) }
        } ?: error("Could not list $tree")
    }

    private companion object {
        const val MIME_TYPE = "application/json"
        const val ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
