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
        val classes = KeywordList().addAttributes(configMd!!.getCollections())
        var thisClass = parser.findKeyword(classes)?.attribute!!
        val url = thisClass?.relativeUrl
        val json = rest.getCollection("${url}", options=mapOf("level" to "brief", "link" to "name"))
        for (obj in json?.getArray()!!) {
            showOne(thisClass.typeName, obj)
        }
    }

    private fun doQuit() {
        throw CliExitException()
    }

    private fun showOne(typeName: String, obj: JsonObject) {
        val classMd = Metadata.getClass(typeName)!!
        for ((name, value) in obj.getDict()!!) {
            val attrMd = classMd.getAttribute(name)
            if (attrMd!=null) {
                println("${attrMd.displayName} = ${value}")
            }
        }
    }
}