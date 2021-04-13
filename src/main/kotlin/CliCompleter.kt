open class CliCompleter {
    fun complete(line: String, token: String): List<String> =
        subclassComplete(line, token).removePrefixes()
    fun help(hctx: HelpContext, line: String) =
        run {
            Cli.outputln(
                StyledText(
                    StyledText("\n"),
                    StyledText(
                        subclassHelp(hctx)
                            ?: StyledText(Properties.get("help.no_help")!!,
                                color=Properties.getParameter("help_help_color")
                            )
                    ),
                    StyledText()
                )
            )
            print("${CommandReader.lastPrompt}$line")
            listOf<String>()
        }
    open fun subclassComplete(line: String, token: String): List<String> {
        return listOf()
    }
    open fun subclassHelp(hctx: HelpContext): StyledText? = null
}

class KeywordCompleter(
    private val keywords: KeywordList
): CliCompleter() {
    override fun subclassComplete(line: String, token: String): List<String> =
        keywords.toStrings().filter{ it.startsWith(token) }
    override fun subclassHelp(hctx: HelpContext) =
        keywords.keywords
            .map{ it.second }
            .removeDuplicates{ a,b -> b.key.startsWith(a.key) }
            .sortedBy{ it.key }
            .filter{ kw -> if (kw.attribute!=null) kw.attribute.level <= Cli.helpLevel else true }
            .let{ keywords ->
                val table = Table(showHeadings=false)
                keywords.forEach{
                    table.append("key", it.key,
                        color=Properties.getParameter("help_key_color"))
                    table.append("help", it.getHelp(hctx),
                        color=Properties.getParameter("help_help_color"))
                    }
                table
                    .setColumnWidths { when (it) {
                        "key" -> Properties.getParameterInt("help_key_width")
                        else ->  Properties.getParameterInt("help_help_width")
                    }}
                    .layoutText().renderStyled()
            }
}

class ObjectCompleter(
    private val objName: ObjectName,
    private val extras: KeywordList=KeywordList()
): CliCompleter() {
    override fun subclassComplete(line: String, token: String): List<String> {
        return try {
            val (envelope, collection) = Rest.get(
                objName.wipeLeafName(),
                mapOf("completion" to token, "limit" to "10")
            )
            val size = envelope["size"]?.toIntOrNull() ?: -1
            val completions = collection.getObjectNames()
            val completion = envelope["completion"] ?: ""
            val keywords = extras.keywords.map{it.second.key}
                .filter{it.startsWith(token, ignoreCase = true)}
            when(size) {
                0 -> keywords
                1 -> keywords.append(completion)
                else -> completions.append(keywords)
            }
        } catch (exc: RestException) { listOf() }
    }
}

class EnumCompleter(
    private val attrMd: AttributeMetadata
): CliCompleter() {
    override fun subclassComplete(line: String, token: String): List<String> =
        KeywordCompleter(KeywordList(*attrMd.range.split("|").toTypedArray()))
            .subclassComplete(line, token)
}

