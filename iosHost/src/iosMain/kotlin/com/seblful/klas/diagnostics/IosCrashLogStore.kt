package com.seblful.klas.diagnostics

import com.seblful.klas.storage.appFile
import com.seblful.klas.storage.delete
import com.seblful.klas.storage.exists
import com.seblful.klas.storage.readBytes
import com.seblful.klas.storage.writeBytes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** [CrashLogStore] over one file in Application Support, as Android keeps it in the app's own files. */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosCrashLogStore : CrashLogStore {

    private val file = appFile("crash_log.txt")

    override fun read(): String = file.readBytes()?.decodeToString().orEmpty()

    override fun write(text: String) = file.writeBytes(text.encodeToByteArray())

    override fun delete() = file.delete()

    override fun isEmpty(): Boolean = !file.exists() || read().isEmpty()
}
