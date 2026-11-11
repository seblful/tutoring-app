package com.seblful.klas.print

/**
 * A document as the app prints it: a title, a line under it, sections of label–amount rows, and a closing
 * line. Words and amounts arrive formatted — the sheet decides only where they go on a page.
 */
data class Sheet(
    val title: String,
    val sections: List<Section>,
    val subtitle: String = "",
    val closing: String = "",
) {
    /** Rows under [title]; a sheet of one list leaves the title empty and prints none. */
    data class Section(val title: String, val rows: List<Row>)

    /** [label] on the left, [value] against the right margin; [detail], when set, in small type under the label. */
    data class Row(val label: String, val value: String, val detail: String = "")

    /** One thing a page draws, [height] in lines of body text. */
    sealed interface Line {
        val height: Int

        data class Title(val text: String) : Line {
            override val height = 2
        }

        data class Subtitle(val text: String) : Line {
            override val height = 1
        }

        /** A section's title, with the gap above it. */
        data class Heading(val text: String) : Line {
            override val height = 2
        }

        data class Entry(val row: Row) : Line {
            override val height = if (row.detail.isEmpty()) 1 else 2
        }

        /** The closing line, with the gap above it. */
        data class Closing(val text: String) : Line {
            override val height = 2
        }
    }

    /**
     * The sheet cut into pages of at most [linesPerPage] lines. A heading never ends a page: it moves to the
     * next with its first row, so a section never starts on one page and lists on another.
     */
    fun pages(linesPerPage: Int): List<List<Line>> {
        require(linesPerPage >= MIN_LINES_PER_PAGE) { "A page must hold a heading and a row" }
        val lines = buildList {
            add(Line.Title(title))
            if (subtitle.isNotEmpty()) add(Line.Subtitle(subtitle))
            sections.filter { it.rows.isNotEmpty() }.forEach { section ->
                if (section.title.isNotEmpty()) add(Line.Heading(section.title))
                section.rows.forEach { add(Line.Entry(it)) }
            }
            if (closing.isNotEmpty()) add(Line.Closing(closing))
        }
        val pages = mutableListOf<MutableList<Line>>(mutableListOf())
        var used = 0
        lines.forEachIndexed { index, line ->
            val keepWith = (lines.getOrNull(index + 1)?.height ?: 0).takeIf { line is Line.Heading } ?: 0
            if (used > 0 && used + line.height + keepWith > linesPerPage) {
                pages += mutableListOf<Line>()
                used = 0
            }
            pages.last() += line
            used += line.height
        }
        return pages
    }

    private companion object {
        /** A heading and a row with its detail. */
        const val MIN_LINES_PER_PAGE = 4
    }
}

/** [name] with what a filename may not hold — a path separator, a colon — turned into dashes. */
fun fileSafe(name: String): String = name.replace(Regex("""[\\/:*?"<>|\n]"""), "-").trim()
