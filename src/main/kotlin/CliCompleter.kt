open class CliCompleter {
    fun complete(line: String, token: String): List<String> {
        return if (token.isEmpty()) {
            Cli.outputln(StyledText(StyledText("\n${subclassHelp(line)}",
                color=Properties.getParameter("help_color")),StyledText()))
            print("${CommandReader.lastPrompt}$line")
            listOf()
        }
        else subclassComplete(line, token).removePrefixes()
    }
    open fun subclassComplete(line: String, token: String): List<String> {
        return listOf()
    }
    open fun subclassHelp(line: String): String {
        return "Sorry, no help available here"
    }
}

class KeywordCompleter(
    private val keywords: KeywordList
): CliCompleter() {
    override fun subclassComplete(line: String, token: String): List<String> =
        keywords.toStrings().filter{ it.startsWith(token) }.map{ "$line$it" }
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

