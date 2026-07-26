package com.seblful.klas.storage

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DocumentStore] over the Storage Access Framework — the device adapter. Holds the only
 * `contentResolver` use in the app outside the database, and the only place a [DocumentRef] becomes
 * a `Uri` again.
 */
@Singleton
class SafDocumentStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DocumentStore {

    override suspend fun read(ref: DocumentRef): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(ref.value.toUri())
            ?.use { it.readBytes().decodeToString() }
            ?: error("Cannot open input stream")
    }

    override suspend fun write(ref: DocumentRef, text: String) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(ref.value.toUri())
                ?.use { it.write(text.toByteArray()) }
                ?: error("Cannot open output stream")
        }
    }
}
