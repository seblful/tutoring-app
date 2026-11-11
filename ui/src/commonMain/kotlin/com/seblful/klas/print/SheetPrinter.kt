package com.seblful.klas.print

/** Turns a [Sheet] into the bytes of a PDF — an interface, so view models stay testable on the JVM. */
fun interface SheetPrinter {
    fun print(sheet: Sheet): ByteArray

    companion object {
        /** What a printed sheet is, to the share sheet and the file picker. */
        const val PDF_MIME_TYPE = "application/pdf"
    }
}
