package com.seblful.klas.storage

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DocumentSharing] over `ACTION_SEND` and a [FileProvider] — the device adapter.
 *
 * ### Why the file goes in the cache
 *
 * A shared file is a **handover, not a copy the tutor keeps**: the receiving app reads it and the tutor
 * never looks for it again. The cache is where Android puts exactly that, and it is the only directory
 * the system will clean up on the app's behalf — which matters here more than usual, because the file is
 * a spreadsheet of a tutor's income. Each share clears the directory first, so at most one is ever on
 * disk however many are sent and whenever the OS gets round to the cache.
 *
 * ### Why a content URI and not a path
 *
 * `file://` URIs have been refused since Android 7 (`FileUriExposedException`). [FileProvider] hands out
 * a `content://` URI scoped to one file, and `FLAG_GRANT_READ_URI_PERMISSION` lends the receiving app
 * read access for as long as the chooser lives — so the CSV is readable by the app the tutor picked and
 * by nothing else. The provider is declared `exported="false"` with `grantUriPermissions="true"`, which
 * is what makes the grant the *only* way in.
 *
 * ### Why `NEW_TASK`
 *
 * The chooser is started from the application context, since a port must not hold an `Activity`. That
 * needs `FLAG_ACTIVITY_NEW_TASK`. The visible consequence is that the share sheet is its own task rather
 * than a child of the report screen, which for a fire-and-forget handover is exactly right: dismissing
 * the sheet returns to the report with nothing changed.
 */
@Singleton
class AndroidDocumentSharing @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DocumentSharing {

    override suspend fun share(filename: String, mimeType: String, subject: String, text: String) {
        // A name, not a path. `getUriForFile` would refuse a file outside the configured root — but only
        // *after* `writeText` had already put it there, so the check is here, before anything is written.
        require(filename == File(filename).name) { "A share filename may not contain a path" }

        val uri = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
            // **Emptied first, so at most one shared file is ever on disk.** These are a tutor's income,
            // and the cache is cleaned up on the OS's schedule rather than on ours — a share per month
            // would otherwise leave a year of them lying about waiting for it. Deleting the previous one
            // is safe by the time we are here: the receiving app's read grant died with its chooser.
            directory.listFiles()?.forEach { it.delete() }
            val file = File(directory, filename).apply { writeText(text) }
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
