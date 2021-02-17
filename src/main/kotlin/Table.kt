import kotlin.math.absoluteValue
import kotlin.math.sign

class Table (
    val maxColumnWidth: Int = 0,
    val columnSpacing: Int = 2,
    val squishHeadings: Boolean = true,
    val headingColor: String? = null,
    val headingBackground: String? = null,
    val headingStyle: String? = null,
    val color_stripe: Iterable<String>? = null,
        )
{
    class Column(
        var heading: String,
        var position: Int = 0,
        var maxWidth: Int = 0
    ) {
        val content: MutableList<StyledText> = mutableListOf()
        val size get() = content.size
        val width get() = content.map{it.length}.maxOrNull() ?: 0

        fun padTo(n: Int) : Column {
            repeat(n - content.size) { content += StyledText("") }
            return this
        }

        fun append(text: String,
                   color: String? = null,
                   background: String? = null,
                   style: String? = null) : Column {
            content += StyledText(text, color, background, style)
            return this
        }
        fun justify(text: String) = when {
            maxWidth<0  -> text.padStart(-maxWidth)
            maxWidth>0  -> text.padEnd(maxWidth)
            else        -> text // ==0
        }
    }

    val columns: MutableMap<String, Column> = mutableMapOf()

    val breadth get() = columns.size
    val depth get() = columns.values.map{it.size}.maxOrNull() ?: 0

    fun append(columnName: String,
               text: String,
               color: String? = null,
               background: String? = null,
               style: String? = null) {
        if (!columns.contains(columnName)) columns[columnName] = Column(columnName, breadth, maxColumnWidth)
        columns[columnName]!!.padTo(depth - 1)
                             .append(text, color, background, style)
    }

    fun orderColumns(fn: (String)->Int) { for (col in columns.keys) columns[col]!!.position = fn(col) }
    fun setColumnWidths(fn: (String)->Int) { for (col in columns.keys) columns[col]!!.maxWidth = fn(col) }
    fun setHeaders(fn: (String)->String) { for (col in columns.keys) columns[col]!!.heading = fn(col) }
    fun setColumns(fn: (String, Column)->Unit) { for ((name,col) in columns) fn(name, col) }


    private fun calculateColumnWidths(columns: Iterable<Column>) {
        for (c in columns) {
            var w = c.width
            if (c.maxWidth!=0) {
                w = minOf(w, c.maxWidth.absoluteValue)
            }
            w = if (squishHeadings) {
                maxOf(w, wrap(c.heading, w).map{ it.length }.maxOrNull() ?: 0)
            } else {
                maxOf(w, c.heading.length)
            }
            c.maxWidth = w * c.maxWidth.sign
        }
    }

    fun render(): String {
        val result: MutableList<String> = mutableListOf()
        val colSep = " ".repeat(columnSpacing)
        val sortedCols = columns.values.sortedBy{ it.position }
        calculateColumnWidths(sortedCols)
        val headings = sortedCols.map{wrap(it.heading, it.maxWidth.absoluteValue).toMutableList()}
        val headingDepth = headings.map{it.size}.maxOrNull() ?: 0
        for (h in headings) repeat(headingDepth - h.size){ h.add(0, "")}
        for (i in 0 until headingDepth) {
            val style = if (i==headingDepth-1) StyledText.addStyle(headingStyle, "underline") else headingStyle
            result.add(
                zip(sortedCols, headings)
                .map{ StyledText(it.first.justify(it.second[i]), headingColor, headingBackground, style).render() }
                .joinToString(colSep))
        }
        for (i in 0 until depth) {
            val rowCells = zip(sortedCols, sortedCols.map{it.content[i]})
                    .map{ wrap(it.second.text, it.first.maxWidth)
                    .toMutableList() }
            val rowDepth = rowCells.map{ it.size }.maxOrNull() ?: 0
            for (rc in rowCells) repeat(rowDepth - rc.size){ rc += "" }
            for (j in 0 until rowDepth) {
                result.add(
                    zip(
                        sortedCols,
                        rowCells.map { it[j] })
                        .joinToString(colSep) { it.first.content[0].clone(it.first.justify(it.second)).render() })
            }
        }
        return result.joinToString("\n")
    }
}