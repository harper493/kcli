class ColumnLayout (
        val columns: Int,
        val separator: String,
        val labelColumnWidth: Int,
        val valueColumnWidth: Int,
        val stripeColors: Iterable<String>? = null,
) {
        val columnNames = (1..columns).cycle().iterator()
        val table = Table(columnSpacing=1,
                stripeColors=stripeColors,
                showHeadings = false,
                verticalPadAbove= true)

        fun append(label: String, value: String) {
                val prefix = columnNames.next()
                table.append("${prefix}L", label)
                table.append("${prefix}S", separator)
                table.append("${prefix}V", value)
        }

        fun layoutText(): ColumnLayout {
                table.setColumns{colName, col ->
                        val n = colName.dropLast(1).toInt()
                        val suffix = when (colName.takeLast(1)) {
                                "L" -> 0
                                "S" -> 1
                                else -> 2
                        }
                        col.position = n * 10 + suffix
                        col.maxWidth = when (colName.takeLast(1)) {
                                "L" -> labelColumnWidth
                                "S" -> separator.length
                                else -> valueColumnWidth
                        }
                }.layoutText()
                return this
        }

        fun renderISO6429() = table.renderISO6429()
}