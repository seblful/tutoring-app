package com.seblful.klas.ui.settings

import com.seblful.klas.diagnostics.CrashLogStore

/** A crash log with nothing on record, for the settings view model. */
internal object NoCrashLogs : CrashLogStore {
    override fun read(): String = ""

    override fun write(text: String) = Unit

    override fun delete() = Unit

    override fun isEmpty(): Boolean = true
}
