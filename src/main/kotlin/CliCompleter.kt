open class CliCompleter {
    open fun complete(line: String, token: String): List<String> {
        return listOf()
    }
}

class KeywordCompleter(
    private val keywords: KeywordList
): CliCompleter() {
    override fun complete(line: String, token: String): List<String> =
        keywords.toStrings().filter{ it.startsWith(token) }.map{ "$line$it" }
}

class ObjectCompleter(
    private val objName: ObjectName,
    private val extras: KeywordList
): CliCompleter() {
    override fun complete(line: String, token: String): List<String> {
        return try {
            val (envelope, collection) = Rest.get(
                objName.wipeLeafName(),
                mapOf("completion" to token, "limit" to "10")
            )
            val size = envelope["size"]?.toIntOrNull() ?: -1
            val completions = collection.objects.keys.toList()
            val completion = envelope["completion"] ?: ""
            val keywords = extras.keywords.map{it.key}.filter{it.startsWith(token)}
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
    override fun complete(line: String, token: String): List<String> =
        KeywordCompleter(KeywordList(*attrMd.range.split("|").toTypedArray()))
            .complete(line, token)
}

