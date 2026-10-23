package com.seblful.klas.storage

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.seblful.klas.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** [DocumentSharing] over `ACTION_SEND` and a [FileProvider] — the device adapter. */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidDocumentSharing @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DocumentSharing {

    override suspend fun share(filename: String, mimeType: String, subject: String, bytes: ByteArray) {
        // A name, not a path. `getUriForFile` would refuse a file outside the configured root — but only
        // *after* the bytes had already put it there, so the check is here, before anything is written.
        require(filename == File(filename).name) { "A share filename may not contain a path" }

        val uri = withContext(Dispatchers.IO) {
            val file = SharedDocuments(File(context.cacheDir, SHARE_DIRECTORY)).write(filename, bytes)
            FileProvider.getUriForFile(context, "${context.packageName}.$AUTHORITY_SUFFIX", file)
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // `createChooser` rather than the bare intent: without it Android may remember a default and
        // silently send every future report to whatever the tutor picked once. No title — the subject is
        // the *file's* line and belongs to the receiving app, not over a list of app icons, where recent
        // Androids ignore it anyway.
        context.startActivity(
            Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private companion object {
        /** Must match the `<cache-path>` name and path in `res/xml/file_paths.xml`. */
        const val SHARE_DIRECTORY = "shared"

        /** Must match the provider's `android:authorities` in the manifest. */
        const val AUTHORITY_SUFFIX = "fileprovider"
    }
}
