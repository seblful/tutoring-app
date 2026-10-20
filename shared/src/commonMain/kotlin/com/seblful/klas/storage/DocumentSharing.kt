package com.seblful.klas.storage

/** ands a file the app has just produced to **another app** — the system share sheet. */
interface DocumentSharing {

    /**
     * Offers [text] to another app as a file called [filename].
     *
     * @param filename what the receiving app sees, extension included — the tutor's mail client shows
     * this, so it is a string resource rather than a slug.
     * @param mimeType what the file is, which is what decides who is offered it. A CSV shared as
     * `text/plain` gets a note-taking app; as `text/csv` it gets the spreadsheet.
     * @param subject a line for a receiver that has somewhere to put one (an email's subject). Ignored
     * by the rest, which is why it is not part of the body.
     *
     * Throws if the file cannot be written or nothing can receive it — the caller decides what to say,
     * as with [DocumentStore].
     */
    suspend fun share(filename: String, mimeType: String, subject: String, text: String)
}
