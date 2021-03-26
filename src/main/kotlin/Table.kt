import kotlin.math.absoluteValue

class Table (
    private val maxColumnWidth: Int = 0,
    private val columnSpacing: Int = 2,
    private val squishHeadings: Boolean = true,
    private val headingColor: String? = null,
    private val headingBackground: String? = null,
    private val headingStyle: String? = null,
    private val underlineHeadings: Boolean = true,
    private val stripeColors: Iterable<String?>? = null,
    private val showHeadings: Boolean = true,
    private val verticalPadAbove: Boolean = false,
        ) {
    class Column(
        var heading: String,
        var position: Int = --defaultPosition,
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

        companion object {
            private var defaultPosition = 10000
        }
    }

    private val columns = mutableMapOf<String, Column>()
    private var sortedCols: List<Column> = listOf()

    private val breadth get() = columns.size
    private val depth get() = columns.values.map { it.size }.maxOrNull() ?: 0
    private var headings = listOf<StyledText>()
    private var wrappedHeadings = listOf<List<StyledText>>()
    private var body = listOf<List<StyledText>>()

    fun append(
        columnName: String,
        text: String,
        color: String? = null,
        background: String? = null,
        style: String? = null
    ) = (columns[columnName]
        ?: Column(columnName, breadth, maxColumnWidth)
            .also { columns[columnName] = it })
        .padTo(depth - 1)
        .append(text, color, background, style)

    fun append(vararg values: Pair<String,String>,
               color: String? = null,
               background: String? = null,
               style: String? = null) {
        values.map{ append(it.first, it.second,
            color=color, background=background, style=style) }
    }

    fun orderColumns(fn: (String) -> Int) {
        for (col in columns.keys) columns[col]!!.position = fn(col)
    }

    fun setColumnWidths(fn: (String) -> Int) {
        for (col in columns.keys) columns[col]!!.maxWidth = fn(col)
    }

    fun setHeaders(fn: (String) -> String) {
        for (col in columns.keys) columns[col]!!.heading = fn(col)
    }

    fun setColumns(fn: (String, Column) -> Unit): Table {
        for ((name, col) in columns) fn(name, col)
        return this
    }

    private fun finalize(): Table {
        columns.values.map{it.padTo(depth)}
        sortedCols = columns.values.sortedBy { it.position }
        sortedCols.map { col ->
            val w = if (col.maxWidth != 0) minOf(col.maxWidth.absoluteValue, col.width)
            else col.width
            val s = if (col.maxWidth<0) -1 else 1
            col.maxWidth = s * maxOf(w,
                if (squishHeadings) wrap(col.heading, w).map { it.length }.maxOrNull() ?: 0
                else col.heading.length) }
        headings = sortedCols.map{
            StyledText(it.heading, headingColor, headingBackground, headingStyle)
        }
        sortedCols.map{ col->
            val colorIterator = (stripeColors?.anyOrNull() ?: listOf(null)).cycle().iterator()
            col.content.map{ it.underride(newColor=colorIterator.next()) }
        }
        return this
    }

    private fun splitCells(row: Iterable<StyledText>, padAtEnd: Boolean): List<List<StyledText>> {
        val splitRows = zip(sortedCols, row).map { colCell ->
            wrap(colCell.second.getText(), colCell.first.maxWidth.absoluteValue, force=true).toMutableList()
        }
        val depth = splitRows.maxSize()
        for (subCol in splitRows) {
            repeat(depth - subCol.size) { subCol.add((if (padAtEnd) subCol.size else 0), "") }
        }
        return splitRows.transpose().map { subRow ->
            zip(row, subRow)
                .map { rowSub ->
                    rowSub.first.clone(rowSub.second)
                }
        }
    }

    fun layoutText(): Table {
        finalize()
        if (showHeadings) {
            wrappedHeadings = splitCells(headings, padAtEnd = false)
            if (underlineHeadings && wrappedHeadings.isNotEmpty()) {
                for (h in wrappedHeadings.last()) h.addStyle("underline")
            }
        } else {
            wrappedHeadings = listOf()
        }
        body = sortedCols.map {
            it.content
        }.transpose().map {
            splitCells(it, padAtEnd=!verticalPadAbove)
        }.flatten()
        return this
    }

    fun render() =
        listOf(wrappedHeadings, body).flatMap { rows ->
            rows.map { row ->
                zip(sortedCols, row).joinToString(" ".repeat(columnSpacing)) { colRow ->
                    colRow.second.justify(colRow.first.maxWidth).render()
                }
            }
        }.joinToString("\n")

    fun renderStyled(): StyledText {
        val spacer = StyledText(" ".repeat(columnSpacing))
        return listOf(wrappedHeadings, body).flatMap { rows ->
            rows.map { row ->
                zip(sortedCols, row).map { colRow ->
                    colRow.second.justify(colRow.first.maxWidth)
                }
            }
        }.map{it.join(spacer)}
            .join("\n")
    }

}