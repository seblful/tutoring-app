package com.seblful.klas.storage

/**
 * Hands a file the app has just produced to **another app** — the system share sheet.
 *
 * Separate from [DocumentStore], because the two answer different questions. `DocumentStore` writes
 * where the tutor *pointed*: they choose a folder, they can find the file again, and the app has said
 * "here is your copy". Sharing writes nowhere the tutor chose and everywhere they might want it — mail,
 * a messenger, Drive — and the file is a temporary means to that end.
 *
 * A port for the reason every Android edge in this app is one: a view model may not touch a `Context`,
 * a `Uri` or an `Intent`. What a report *is* stays in `domain/reports/ReportCsv` and what it is called stays in
 * `res/values`; this carries the bytes out.
 */
interface DocumentSharing {

    /**
     * Offers [text] to another app as a file called [filename].
     *
     * @param filename what the receiving app sees, extension included — the tutor's mail client shows
     *   this, so it is a string resource rather than a slug.
     * @param mimeType what the file is, which is what decides who is offered it. A CSV shared as
     *   `text/plain` gets a note-taking app; as `text/csv` it gets the spreadsheet.
     * @param subject a line for a receiver that has somewhere to put one (an email's subject). Ignored
     *   by the rest, which is why it is not part of the body.
     *
     * Throws if the file cannot be written or nothing can receive it — the caller decides what to say,
     * as with [DocumentStore].
     */
    suspend fun share(filename: String, mimeType: String, subject: String, text: String)
}
