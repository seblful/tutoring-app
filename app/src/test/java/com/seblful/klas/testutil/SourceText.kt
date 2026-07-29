package com.seblful.klas.testutil

/**
 * Reading the project's own Kotlin as text, for the tripwires that scan the tree ([ProjectFiles] finds
 * the files; this reads what is in them).
 */
object SourceText {

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
