package com.seblful.klas.testutil

/**
 * Reading the project's own Kotlin as text, for the tripwires that scan the tree ([ProjectFiles] finds
 * the files; this reads what is in them).
 */
object SourceText {

    /**
     * [text] with its comments stripped — **the form a source-scanning tripwire has to match against.**
     *
     * The things these tests scan for are exactly the things the KDoc beside them has to be able to name:
     * `KlasScreen`'s own doc explains the `Scaffold` it replaced, every screen that used to carry one says
     * so in a comment, and `DatabaseModule`'s doc explains why it no longer calls `addMigrations`. Matching
     * raw text makes writing that explanation fail the build — a tripwire that punishes documenting the rule
     * it enforces, which is how one of them failed the moment the rule it checks became worth explaining.
     *
     * Shared rather than private to one test because two now need it, and a rule two tests apply is a rule
     * they can apply differently.
     */
    fun code(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    /**
     * Every `callee(…)` argument list in [code], by paren depth — which stops at the closing paren, so a
     * trailing lambda is left out: what a call *passes* is a different question from what is drawn inside
     * it.
     *
     * Matches on the suffix, which is the useful part. `"Card("` finds `OutlinedCard(` and this app's own
     * `ContentCard(`/`SectionCard(`, because a modifier handed to a wrapper reaches the card inside it;
     * `"TimePickerState("` finds `rememberTimePickerState(`, because both take the same `is24Hour`.
     */
    fun argumentLists(code: String, callee: String): List<String> {
        val lists = mutableListOf<String>()
        var from = code.indexOf(callee)
        while (from >= 0) {
            val open = from + callee.length - 1
            var depth = 0
            var i = open
            while (i < code.length) {
                if (code[i] == '(') depth++
                if (code[i] == ')') {
                    depth--
                    if (depth == 0) break
                }
                i++
            }
            lists += code.substring(open + 1, i.coerceAtMost(code.length))
            from = code.indexOf(callee, i)
        }
        return lists
    }
}
