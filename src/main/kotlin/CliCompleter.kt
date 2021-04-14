open class CliCompleter(
    val helpText: String? = null
) {
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
    open fun subclassHelp(hctx: HelpContext): StyledText? =
        helpText?.let{ StyledText(helpText, color=Properties.getParameter("help_help_color"))}
}

class KeywordCompleter(
    private val keywords: KeywordList
): CliCompleter() {
    override fun subclassComplete(line: String, token: String): List<String> =
        keywords.toStrings().filter{ it.startsWith(token) }
    override fun subclassHelp(hctx: HelpContext) =
        HelpTable()
            .append(
                keywords.keywords
                    .map{ it.second }
                    .removeDuplicates{ a,b -> b.key.startsWith(a.key) && a.attribute==b.attribute }
                    .filter{ kw ->
                        kw.attribute==null
                                || (kw.attribute.level <= Cli.helpLevel
                                && Properties.get("suppress",
                            kw.attribute.myHelpClass.name,
                            kw.attribute.name)==null) }
                    .map{ Pair(it.key, it.getHelp(hctx)) }
                    .toMap())
            .renderStyled()

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
    override fun subclassHelp(hctx: HelpContext) =
        StyledText("Enter an object name of class ${objName.leafClass?.displayName}" +
                " or one of: ${extras.keywords.joinToString(", ") { it.first }}"
                    .orBlankIf{extras.isEmpty()},
            color=Properties.getParameter("help_color"))
}

class EnumCompleter(
    private val attrMd: AttributeMetadata
): CliCompleter() {
    override fun subclassComplete(line: String, token: String): List<String> =
        KeywordCompleter(KeywordList(*attrMd.range.split("|").toTypedArray()))
            .subclassComplete(line, token)
    override fun subclassHelp(hctx: HelpContext) =
        StyledText("Enter one of: ${attrMd.range.split("|").joinToString(", ")}",
            color=Properties.getParameter("help_color"))
}

