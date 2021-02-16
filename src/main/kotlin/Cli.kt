class CliExitException() : Exception("")

class Cli (
    private val rest: Rest,
) {
    private var parser = Parser("")

    fun oneLine(line: String) {
        parser = Parser(line)
        var commands = KeywordList(Pair("show", { doShow() }),
                                   Pair("quit", { doQuit() }))
        parser.nextToken()
        var cmd = parser.findKeyword(commands)
        cmd?.function?.invoke()
    }

    private fun doShow() {
        val configMd = Metadata.getClass("configuration")
        var oname = parser.getObjectName().first
        val json = rest.getCollection("${oname.url}", options = mapOf("level" to "brief", "link" to "name"))
        if (json != null) {
            for (obj in json.asArray()) {
                val leafClassMd = oname.leafClass
                showOne(leafClassMd!!, obj)
            }
        }
    }

    private fun doQuit() {
        throw CliExitException()
    }

    private fun showOne(classMd: ClassMetadata, obj: JsonObject) {
        for ((name, value) in obj.asDict()) {
            val attrMd = classMd.getAttribute(name)
            var display = value.asString()
            if (attrMd!=null) {
                try {
                    val converted = attrMd.convert(value.asString())
                    display = converted.toString()
                } catch (exc: Exception) { }
                println("%30s = %s %s".format(attrMd.displayName, display, attrMd.unit))
            }
        }
    }
}