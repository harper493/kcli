class ColumnLayout (
        val columns: Int,
        val separator: String = "",
        val labelColumnWidth: Int? = null,
        val valueColumnWidth: Int = 0,
        val stripeColors: Iterable<String>? = null,
) {
        private val columnNames = (1..columns).cycle().iterator()
        private var maxWidth = 0
        val table = Table(columnSpacing=1,
                stripeColors=stripeColors,
                showHeadings = false,
                verticalPadAbove= false)

        fun append(label: String, value: String?=null) {
                val prefix = columnNames.next()
                maxWidth = maxOf(maxWidth, label.length)
                table.append("${prefix}L", label)
                if (separator.isNotEmpty()) {
                        table.append("${prefix}S", separator)
                }
                if (value!=null) {
                        table.append("${prefix}V", value)
                }
        }

        fun layoutText(): ColumnLayout {
                table.setColumns{colName, col ->
                        val suffix = colName.takeLast(1)
                        col.position = colName.dropLast(1).toInt() * 10 +
                                when (suffix) {
                                        "L" -> 0
                                        "S" -> 1
                                        else -> 2
                                }
                        col.maxWidth = when (suffix) {
                                "L" -> labelColumnWidth ?: maxWidth
                                "S" -> separator.length
                                else -> valueColumnWidth
                        }
                }.layoutText()
                return this
        }

        fun render() = table.render()

        fun renderStyled() = table.renderStyled()
}