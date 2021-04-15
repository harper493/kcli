class HelpTable(values: Map<String,String> = mapOf(),
                val header: String? = null) {
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

    fun renderStyled() =
        if (header==null) complete().table.renderStyled()
        else StyledText(
            StyledText(header, color="help"),
            complete().table.renderStyled())

    fun render() = renderStyled().render()

    private fun complete() =
        also {
            content
                .toSortedMap()
                .forEach {
                    table.append(
                        "key", it.key,
                        color = "help_key"
                    ).append(
                        "help", it.value,
                        color = "help_help"
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

class HelpContext(val prefix: Iterable<String> = listOf(),
                  val fn: (String)->String?={ null }) {
    fun nestedHelp(next: String) =
        HelpContext(prefix.append(next))
    fun helpFor(key: String, useDefault: Boolean = false) =
        fn(key)
            ?: Properties.get(listOf("help").append(prefix).append(key))
            ?: (if (useDefault) Properties.get("help", "no_help")!! else "")

    constructor(term: String): this(listOf(term))
    constructor(fn: (String)->String?): this(prefix=listOf(), fn=fn)
}

