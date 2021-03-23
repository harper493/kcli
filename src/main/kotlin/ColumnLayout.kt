class ColumnLayout (
        val columns: Int,
        val separator: String,
        val labelColumnWidth: Int,
        val valueColumnWidth: Int,
        val stripeColors: Iterable<String>? = null,
) {
        private val columnNames = (1..columns).cycle().iterator()
        val table = Table(columnSpacing=1,
                stripeColors=stripeColors,
                showHeadings = false,
                verticalPadAbove= false)

        fun append(label: String, value: String) {
                val prefix = columnNames.next()
                table.append("${prefix}L", label)
                table.append("${prefix}S", separator)
                table.append("${prefix}V", value)
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
                                "L" -> labelColumnWidth
                                "S" -> separator.length
                                else -> valueColumnWidth
                        }
                }.layoutText()
                return this
        }

        fun render() = table.render()
}