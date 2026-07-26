package com.seblful.klas.diagnostics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CrashLogStore] over a single file in the app's private storage — the device adapter. No network
 * and no third-party SDK, in keeping with the app's local-only stance.
 */
@Singleton
class FileCrashLogStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CrashLogStore {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    override fun read(): String = if (file.exists()) file.readText() else ""

    override fun write(text: String) {
        file.writeText(text)
    }

    override fun delete() {
        file.delete()
    }

    override fun isEmpty(): Boolean = !file.exists() || file.length() == 0L

    private companion object {
        const val FILE_NAME = "crash_log.txt"
    }
}
