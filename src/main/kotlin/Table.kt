import kotlin.math.absoluteValue
import kotlin.math.sign

class Table (
    private val maxColumnWidth: Int = 0,
    private val columnSpacing: Int = 2,
    private val squishHeadings: Boolean = true,
    private val headingColor: String? = null,
    private val headingBackground: String? = null,
    private val headingStyle: String? = null,
    private val underlineHeadings: Boolean = true,
    private val stripeColors: Iterable<String?>? = null,
        ) {
    class Column(
        var heading: String,
        var position: Int = 0,
        var maxWidth: Int = 0
    ) {
        val content: MutableList<StyledText> = mutableListOf()
        val size get() = content.size
        val width get() = content.map { it.length }.maxOrNull() ?: 0

        fun padTo(n: Int): Column {
            repeat(n - content.size) { content += StyledText("") }
            return this
        }

        fun append(
            text: String,
            color: String? = null,
            background: String? = null,
            style: String? = null
        ): Column {
            content += StyledText(text, color, background, style)
            return this
        }

        fun justify(text: String) = when {
            maxWidth < 0 -> text.padStart(-maxWidth)
            maxWidth > 0 -> text.padEnd(maxWidth)
            else -> text // ==0
        }
    }

    private val columns: MutableMap<String, Column> = mutableMapOf()

    val breadth get() = columns.size
    val depth get() = columns.values.map { it.size }.maxOrNull() ?: 0
    var heading: MutableList<List<StyledText>> = mutableListOf()
    var body: MutableList<List<StyledText>> = mutableListOf()

    fun append(
        columnName: String,
        text: String,
        color: String? = null,
        background: String? = null,
        style: String? = null
    ) = (columns[columnName] ?: Column(columnName, breadth, maxColumnWidth).also { columns[columnName] = it })
        .padTo(depth - 1)
        .append(text, color, background, style)

    fun orderColumns(fn: (String) -> Int) {
        for (col in columns.keys) columns[col]!!.position = fn(col)
    }

    fun setColumnWidths(fn: (String) -> Int) {
        for (col in columns.keys) columns[col]!!.maxWidth = fn(col)
    }

    fun setHeaders(fn: (String) -> String) {
        for (col in columns.keys) columns[col]!!.heading = fn(col)
    }

    fun setColumns(fn: (String, Column) -> Unit) {
        for ((name, col) in columns) fn(name, col)
    }


    private fun calculateColumnWidths(columns: Iterable<Column>) {
        for (c in columns) {
            var w = c.width
            if (c.maxWidth != 0) {
                w = minOf(w, c.maxWidth.absoluteValue)
            }
            w = if (squishHeadings) {
                maxOf(w, wrap(c.heading, w).map { it.length }.maxOrNull() ?: 0)
            } else {
                maxOf(w, c.heading.length)
            }
            c.maxWidth = w * c.maxWidth.sign
        }
    }

    fun layout(): Table {
        heading.clear()
        body.clear()
        val colorIterator = (stripeColors?.anyOrNull() ?: listOf("")).cycle().iterator()
        val sortedCols = columns.values.sortedBy { it.position }
        calculateColumnWidths(sortedCols)
        val headings = sortedCols.map { wrap(it.heading, it.maxWidth.absoluteValue).toMutableList() }
        val headingDepth = headings.maxSize()
        for (h in headings) repeat(headingDepth - h.size) { h.add(0, "") }
        val headingRows = headings.transpose().map { it ->
            zip(sortedCols, it).map {
                StyledText(
                    it.first.justify(it.second),
                    headingColor,
                    headingBackground,
                    headingStyle
                )
            }
        }
        if (underlineHeadings) for (h in headingRows.last()) h.addStyle("underline")
        headingRows.map { heading.add(it) }
        for (row in sortedCols.map { it.content }.transpose()) {
            val color = colorIterator.next()
            val rowCells = zip(sortedCols, row)
                .map { wrap(it.second.text, it.first.maxWidth, force = true).toMutableList() }
            val rowDepth = rowCells.maxSize()
            for (rc in rowCells) repeat(rowDepth - rc.size) { rc += "" }
            rowCells.transpose().map { it ->
                zip(sortedCols, row, it)
                    .map {
                        it.second.clone(it.first.justify(it.third))
                            .underride(color)
                    }
            }.map { it.also { body.add(it) } }
        }
        return this
    }

    fun renderISO6429(): String {
        val colSep = " ".repeat(columnSpacing)
        return heading.chain(body).map { it ->
            it.joinToString(colSep) {
                it.renderISO6429()
            }
        }.joinToString("\n")
    }
}