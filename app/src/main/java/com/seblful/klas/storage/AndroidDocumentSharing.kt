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

/**
 * [DocumentSharing] over `ACTION_SEND` and a [FileProvider] — the device adapter.
 *
 * ### Why the file goes in the cache
 *
 * A shared file is a **handover, not a copy the tutor keeps**: the receiving app reads it and the tutor
 * never looks for it again. The cache is where Android puts exactly that, and it is the only directory
 * the system will clean up on the app's behalf — which matters here more than usual, because the file is
 * a spreadsheet of a tutor's income. Each share has a unique path, and files older than a week are
 * removed on the next share; a new handover must not replace a file another app is still reading.
 *
 * ### Why a content URI and not a path
 *
 * `file://` URIs have been refused since Android 7 (`FileUriExposedException`). [FileProvider] hands out
 * a `content://` URI scoped to one file, and `FLAG_GRANT_READ_URI_PERMISSION` lends the receiving app
 * read access while the receiving activity stack remains active — so the CSV is readable by that app and
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
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidDocumentSharing @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DocumentSharing {

    override suspend fun share(filename: String, mimeType: String, subject: String, text: String) {
        // A name, not a path. `getUriForFile` would refuse a file outside the configured root — but only
        // *after* `writeText` had already put it there, so the check is here, before anything is written.
        require(filename == File(filename).name) { "A share filename may not contain a path" }

        val uri = withContext(Dispatchers.IO) {
            val file = SharedDocuments(File(context.cacheDir, SHARE_DIRECTORY)).write(filename, text)
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
