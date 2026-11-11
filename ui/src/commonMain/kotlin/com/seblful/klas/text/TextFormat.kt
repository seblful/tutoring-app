package com.seblful.klas.text

/**
 * Fills `%1$s`, `%1$d` and `%%` the way `String.format` does for a resource — the only specifiers the build
 * plugin lets into the catalog. Integers take [tag]'s own digits, as Android's formatter gives them.
 */
internal fun formatText(template: String, tag: String, args: Array<out Any?>): String =
    SPECIFIER.replace(template) { match ->
        val position = match.groupValues[1].toIntOrNull() ?: return@replace "%"
        require(position <= args.size) { "'$template' needs argument $position of ${args.size}" }
        val arg = args[position - 1]
        if (match.groupValues[2] == "s") arg.toString() else formatInteger(integer(arg, template), tag)
    }

private val SPECIFIER = Regex("""%(?:%|(\d+)\$([sd]))""")

private fun integer(arg: Any?, template: String): Long = when (arg) {
    is Int -> arg.toLong()
    is Long -> arg
    is Short -> arg.toLong()
    is Byte -> arg.toLong()
    else -> throw IllegalArgumentException("'$template' formats $arg as an integer")
}

/** [value] as `%d` writes it in [tag]: no grouping, the language's digits. */
internal expect fun formatInteger(value: Long, tag: String): String
