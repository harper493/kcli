open class CliCompleter {
    open fun complete(line: String, token: String): List<String> {
        return listOf()
    }
}

class KeywordCompleter(
    val keywords: KeywordList
): CliCompleter() {
    override fun complete(line: String, token: String): List<String> =
        keywords.keywords.filter{ it.key.startsWith(token) }.map{ "$line $it" }
}