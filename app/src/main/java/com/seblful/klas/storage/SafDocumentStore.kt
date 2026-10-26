package com.seblful.klas.storage

import android.content.Context
import androidx.core.net.toUri
import com.seblful.klas.di.ApplicationContext
import com.seblful.klas.di.Io
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [DocumentStore] over the Storage Access Framework — the device adapter. Holds the only
 * `contentResolver` use in the app outside the database, and the only place a [DocumentRef] becomes
 * a `Uri` again.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class SafDocumentStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Io private val io: CoroutineDispatcher,
) : DocumentStore {

    override suspend fun read(ref: DocumentRef): String = withContext(io) {
        context.contentResolver.openInputStream(ref.value.toUri())
            ?.use { it.readBytes().decodeToString() }
            ?: error("Cannot open input stream")
    }

    override suspend fun write(ref: DocumentRef, text: String) {
        withContext(io) {
            context.contentResolver.openOutputStream(ref.value.toUri())
                // Encoded as it is written rather than into a second whole copy first.
                ?.bufferedWriter()?.use { it.write(text) }
                ?: error("Cannot open output stream")
        }
    }

    override suspend fun write(ref: DocumentRef, bytes: ByteArray) {
        withContext(io) {
            context.contentResolver.openOutputStream(ref.value.toUri())
                ?.use { it.write(bytes) }
                ?: error("Cannot open output stream")
        }
    }
}
