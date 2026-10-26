package com.seblful.klas.storage

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
class SafBackupFolderTest {

    /** A folder whose provider can be told to drop a write. */
    class FakeDocuments : DocumentsProvider() {
        val names = linkedMapOf<String, String>()
        var failOpen = false

        override fun onCreate() = true

        override fun queryRoots(projection: Array<String>?): Cursor =
            MatrixCursor(projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID))

        override fun isChildDocument(parentDocumentId: String, documentId: String) = true

        override fun queryDocument(documentId: String, projection: Array<String>?): Cursor =
            MatrixCursor(arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)).apply {
                addRow(arrayOf(documentId, names[documentId] ?: "folder"))
            }

        override fun queryChildDocuments(
            parentDocumentId: String,
            projection: Array<String>?,
            sortOrder: String?,
        ): Cursor =
            MatrixCursor(arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)).apply {
                names.forEach { (id, name) -> addRow(arrayOf(id, name)) }
            }

        override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
            if (failOpen) throw FileNotFoundException("the provider dropped the connection")
            return ParcelFileDescriptor.open(File.createTempFile("saf", ".json"), ParcelFileDescriptor.MODE_READ_WRITE)
        }

        override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
            val id = "doc-${names.size + 1}"
            names[id] = displayName
            return id
        }

        override fun deleteDocument(documentId: String) {
            names.remove(documentId)
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var documents: FakeDocuments
    private lateinit var folder: String

    @Before
    fun registerProvider() {
        val info = ProviderInfo().apply {
            authority = AUTHORITY
            exported = true
            grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
        }
        documents = Robolectric.buildContentProvider(FakeDocuments::class.java).create(info).get()
        folder = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root").toString()
    }

    @Test
    fun `a copy is written into the folder`() = runTest {
        SafBackupFolder(context).write(folder, "klas-auto-backup-2026-10-26.json", "{}")

        assertEquals(listOf("klas-auto-backup-2026-10-26.json"), documents.names.values.toList())
    }

    @Test
    fun `a write the provider drops leaves no truncated copy to be counted among the kept ones`() = runTest {
        documents.failOpen = true

        val failure = runCatching { SafBackupFolder(context).write(folder, "klas-auto-backup-2026-10-26.json", "{}") }

        assertTrue("the caller has to hear about it", failure.isFailure)
        assertEquals(emptyList<String>(), documents.names.values.toList())
    }

    private companion object {
        const val AUTHORITY = "com.seblful.klas.test.documents"
    }
}
