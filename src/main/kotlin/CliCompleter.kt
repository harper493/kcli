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
            val envelope = Rest.getRaw(
                objName.wipeLeafName().url,
                options = mapOf("completion" to token, "limit" to "10")
            )?.asDict()
            val size = envelope?.get("size")?.asInt() ?: -1
            val completions = envelope?.get("collection")?.asArray()
                ?.map{it.asDict()?.get("name")?.asString()}?.filterNotNull() ?: listOf()
            val completion = envelope?.get("completion")?.asString() ?: ""
            val keywords = extras.keywords.map{it.key}.filter{it.startsWith(token)}
            when(size) {
                0 -> keywords
                1 -> keywords.append(completion)
                else -> completions.append(keywords)
            }
        } catch (exc: RestException) { listOf() }
    }
}

