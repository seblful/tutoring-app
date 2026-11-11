package com.seblful.klas.gradle.strings

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

/** One `strings.xml`: its strings, plurals by CLDR quantity, and string arrays, in file order. */
data class StringFile(
    val strings: Map<String, String>,
    val plurals: Map<String, Map<String, String>>,
    val arrays: Map<String, List<String>>,
)

/** Reads Android string resources with aapt2's text rules, so a string reads as it did from `R.string`. */
object AndroidStrings {

    val QUANTITIES = listOf("zero", "one", "two", "few", "many", "other")

    fun parse(xml: String, source: String): StringFile {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val root = factory.newDocumentBuilder().parse(xml.byteInputStream()).documentElement
        val strings = LinkedHashMap<String, String>()
        val plurals = LinkedHashMap<String, Map<String, String>>()
        val arrays = LinkedHashMap<String, List<String>>()
        for (element in root.childElements()) {
            val name = element.getAttribute("name")
            val where = "$source: ${element.tagName} '$name'"
            require(name.isNotEmpty()) { "$source: <${element.tagName}> without a name" }
            when (element.tagName) {
                "string" -> strings.putUnique(name, decode(element.text(where), where), where)
                "plurals" -> plurals.putUnique(name, element.quantities(where), where)
                "string-array" -> arrays.putUnique(
                    name,
                    element.childElements().map { item ->
                        require(item.tagName == "item") { "$where: unexpected <${item.tagName}>" }
                        decode(item.text(where), where)
                    },
                    where,
                )
                else -> error("$where: unsupported resource type")
            }
        }
        return StringFile(strings, plurals, arrays)
    }

    /**
     * aapt2's reading of a resource's text: backslash escapes, `"…"` keeping its whitespace, runs of
     * whitespace elsewhere collapsing to one space and the ends trimmed. An unescaped apostrophe outside
     * quotes is refused, as aapt2 refuses it.
     */
    fun decode(raw: String, where: String): String {
        val out = StringBuilder()
        var quoted = false
        // Index of a space that came from collapsed whitespace and may still be trimmed at the end.
        var trimmable = -1
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            when {
                char == '\\' -> {
                    require(index + 1 < raw.length) { "$where: dangling backslash" }
                    val next = raw[index + 1]
                    index += 1
                    when (next) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'u' -> {
                            val hex = raw.substring(index + 1, minOf(index + 5, raw.length))
                            require(hex.length == 4 && hex.all { it.isHexDigit() }) { "$where: bad \\u escape" }
                            out.append(hex.toInt(16).toChar())
                            index += 4
                        }
                        else -> out.append(next)
                    }
                    trimmable = -1
                }
                char == '"' -> quoted = !quoted
                char == '\'' && !quoted -> error("$where: unescaped apostrophe")
                char in ASCII_WHITESPACE && !quoted -> if (out.isNotEmpty() && trimmable != out.length - 1) {
                    out.append(' ')
                    trimmable = out.length - 1
                }
                else -> {
                    out.append(char)
                    trimmable = -1
                }
            }
            index += 1
        }
        if (trimmable == out.length - 1 && trimmable >= 0) out.setLength(trimmable)
        return out.toString()
    }

    /** aapt2 collapses ASCII whitespace only; a no-break space in the text is meant. */
    private const val ASCII_WHITESPACE = " \t\n\r\u000B\u000C"

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun Element.quantities(where: String): Map<String, String> {
        val items = LinkedHashMap<String, String>()
        for (item in childElements()) {
            require(item.tagName == "item") { "$where: unexpected <${item.tagName}>" }
            val quantity = item.getAttribute("quantity")
            require(quantity in QUANTITIES) { "$where: unknown quantity '$quantity'" }
            items.putUnique(quantity, decode(item.text(where), where), "$where/$quantity")
        }
        require("other" in items) { "$where: no 'other' quantity" }
        return items
    }

    private fun Element.text(where: String): String = buildString {
        val children = childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> append(child.nodeValue)
                Node.COMMENT_NODE -> Unit
                else -> error("$where: markup inside text is not supported")
            }
        }
    }

    private fun Element.childElements(): List<Element> {
        val children = childNodes
        return (0 until children.length).map(children::item).filterIsInstance<Element>()
    }

    private fun <V> MutableMap<String, V>.putUnique(key: String, value: V, where: String) {
        require(put(key, value) == null) { "$where: defined twice" }
    }
}
