package com.seblful.klas.testutil

/**
 * Removes comments for artifact and compatibility checks.
 */
object SourceText {

    /**
     * Strips comments so source checks match code without failing on explanatory KDoc.
     */
    fun code(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")
}
