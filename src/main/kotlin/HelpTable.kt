class HelpTable(values: Map<String,String> = mapOf()) {
    val table = Table(showHeadings=false)
    val content = mutableMapOf<String,String>()

    fun append(values: Map<String, String?>) =
        also {
            values.forEach{ append(it.key, it.value) }
        }

    fun append(vararg values: Pair<String, String?>) =
        also {
            values.forEach{ append(it.first, it.second) }
        }

    fun append(key: String, help: String?) =
        also {
            if (help != null) {
                content[key] = help
            }
        }

    fun render() = complete().table.render()

    fun renderStyled() = complete().table.renderStyled()

    private fun complete() =
        also {
            content
                .toSortedMap()
                .forEach {
                    table.append(
                        "key", it.key,
                        color = Properties.getParameter("help_key_color")
                    ).append(
                        "help", it.value,
                        color = Properties.getParameter("help_help_color")
                    )
                }
            table
                .setColumnWidths {
                    when (it) {
                        "key" -> Properties.getParameterInt("help_key_width")
                        else -> Properties.getParameterInt("help_help_width")
                    }
                }
        }

    init {
        append(values)
    }
}

class HelpContext(val prefix: Iterable<String> = listOf()) {
    fun nestedHelp(next: String) =
        HelpContext(prefix.append(next))
    fun helpFor(key: String, useDefault: Boolean = false) =
        Properties.get(listOf("help").append(prefix).append(key))
            ?: (if (useDefault) Properties.get("help", "no_help")!! else "")

    constructor(term: String): this(listOf(term))
}

