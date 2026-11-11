package com.seblful.klas.gradle.strings

/**
 * Writes the text catalog: `Strings`, `Plurals` and `StringArrays` keys in the default file's order, and
 * one table object per language that the runtime in `com.seblful.klas.text` indexes by those keys. A key
 * a translation lacks is null there, and the runtime falls back to the default language.
 */
class CatalogWriter(private val packageName: String, private val defaultTag: String) {

    /** File name to Kotlin source. [languages] maps BCP 47 tags to their files and holds [defaultTag]. */
    fun write(languages: Map<String, StringFile>): Map<String, String> {
        val default = requireNotNull(languages[defaultTag]) { "No strings for the default language '$defaultTag'" }
        languages.forEach { (tag, file) -> check(tag, file, default) }
        val files = LinkedHashMap<String, String>()
        files["TextKeys.kt"] = keys(default)
        languages.toSortedMap().forEach { (tag, file) -> files["${tableName(tag)}.kt"] = table(tag, file, default) }
        files["TextTables.kt"] = index(languages.keys.sorted())
        return files
    }

    private fun check(tag: String, file: StringFile, default: StringFile) {
        fun extra(kind: String, keys: Set<String>, known: Set<String>) {
            val unknown = keys - known
            require(unknown.isEmpty()) { "$tag has $kind the default language lacks: ${unknown.sorted()}" }
        }
        extra("strings", file.strings.keys, default.strings.keys)
        extra("plurals", file.plurals.keys, default.plurals.keys)
        extra("string arrays", file.arrays.keys, default.arrays.keys)
        val texts = file.strings.mapValues { listOf(it.value) } +
            file.plurals.mapValues { it.value.values.toList() } +
            file.arrays
        texts.forEach { (key, values) -> values.forEach { checkFormat(it, "$tag/$key") } }
    }

    /** Only what every platform's formatter reads alike: `%1$s`, `%1$d` and `%%`. */
    private fun checkFormat(text: String, where: String) {
        var index = text.indexOf('%')
        while (index >= 0) {
            val match = SPECIFIER.matchAt(text, index)
            require(match != null) { "$where: unsupported format specifier at ${text.substring(index).take(8)}" }
            index = text.indexOf('%', match.range.last + 1)
        }
    }

    private fun keys(default: StringFile) = source {
        appendLine("public object Strings {")
        default.strings.keys.forEachIndexed { i, key ->
            appendLine("    public val ${identifier(key)}: StringResource = StringResource($i, \"$key\")")
        }
        appendLine("}")
        appendLine()
        appendLine("public object Plurals {")
        default.plurals.keys.forEachIndexed { i, key ->
            appendLine("    public val ${identifier(key)}: PluralResource = PluralResource($i, \"$key\")")
        }
        appendLine("}")
        appendLine()
        appendLine("public object StringArrays {")
        default.arrays.keys.forEachIndexed { i, key ->
            appendLine("    public val ${identifier(key)}: StringArrayResource = StringArrayResource($i, \"$key\")")
        }
        appendLine("}")
    }

    private fun table(tag: String, file: StringFile, default: StringFile) = source {
        appendLine("internal object ${tableName(tag)} : TextTable(\"$tag\") {")
        appendLine("    override val strings: Array<String?> = arrayOf(")
        default.strings.keys.forEach { key -> appendLine("        ${literal(file.strings[key])},") }
        appendLine("    )")
        appendLine()
        appendLine("    override val plurals: Array<PluralText?> = arrayOf(")
        default.plurals.keys.forEach { key ->
            val items = file.plurals[key]
            if (items == null) {
                appendLine("        null,")
            } else {
                val args = AndroidStrings.QUANTITIES.filter { it in items }
                    .joinToString { quantity -> "$quantity = ${literal(items.getValue(quantity))}" }
                appendLine("        PluralText($args),")
            }
        }
        appendLine("    )")
        appendLine()
        appendLine("    override val arrays: Array<List<String>?> = arrayOf(")
        default.arrays.keys.forEach { key ->
            val items = file.arrays[key]
            appendLine(if (items == null) "        null," else "        listOf(${items.joinToString(transform = ::literal)}),")
        }
        appendLine("    )")
        appendLine("}")
    }

    private fun index(tags: List<String>) = source {
        appendLine("internal const val DEFAULT_TEXT_TAG: String = \"$defaultTag\"")
        appendLine()
        appendLine("internal val textTags: Set<String> = setOf(${tags.joinToString { "\"$it\"" }})")
        appendLine()
        appendLine("internal fun textTable(tag: String): TextTable? = when (tag) {")
        tags.forEach { appendLine("    \"$it\" -> ${tableName(it)}") }
        appendLine("    else -> null")
        appendLine("}")
    }

    private fun source(body: StringBuilder.() -> Unit): String = buildString {
        appendLine("// Generated by the klas.strings build plugin from `src/commonMain/strings`. Do not edit.")
        appendLine("@file:Suppress(\"ktlint\", \"detekt:all\")")
        appendLine()
        appendLine("package $packageName")
        appendLine()
        body()
    }

    companion object {
        private val SPECIFIER = Regex("""%(%|[1-9]\d*\$[sd])""")

        private val KEYWORDS = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
            "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
            "typeof", "val", "var", "when", "while",
        )

        fun tableName(tag: String): String =
            "TextTable" + tag.split('-').joinToString("") { part -> part.lowercase().replaceFirstChar(Char::uppercase) }

        private fun identifier(key: String): String {
            require(Regex("[A-Za-z_][A-Za-z0-9_]*").matches(key)) { "'$key' is not a Kotlin identifier" }
            return if (key in KEYWORDS) "`$key`" else key
        }

        fun literal(text: String?): String = if (text == null) {
            "null"
        } else {
            buildString {
                append('"')
                for (char in text) {
                    when {
                        char == '\\' -> append("\\\\")
                        char == '"' -> append("\\\"")
                        char == '$' -> append("\\$")
                        char == '\n' -> append("\\n")
                        char == '\t' -> append("\\t")
                        char == '\r' -> append("\\r")
                        char.code < 0x20 || char.isSurrogate() || Character.isISOControl(char) ||
                            Character.getType(char) == Character.FORMAT.toInt() ||
                            Character.isSpaceChar(char) && char != ' ' ->
                            append("\\u").append(char.code.toString(16).padStart(4, '0'))
                        else -> append(char)
                    }
                }
                append('"')
            }
        }
    }
}
