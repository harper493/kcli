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
        val levels = listOf("brief", "full", "detail", "debug")
        val extras = KeywordList("select").addKeys(levels)
        val configMd = Metadata.getClass("configuration")
        val (oname, terminator) = parser.getObjectName(extras=extras)
        val level = if (terminator?.value ?: "" in levels) (terminator!!.value ?: "") else "brief"
        val json = rest.getCollection("${oname.url}", options = mapOf("level" to level, "link" to "name"))
        val leafClassMd = oname.leafClass!!
        if (json != null) {
            if (oname.isWild) {
                println(showCollection(leafClassMd, json))
            } else {
                for (obj in json.asArray()) {
                    println(showOne(leafClassMd, obj))
                }
            }
        }
    }

    private fun doQuit() {
        throw CliExitException()
    }

    private fun showOne(classMd: ClassMetadata, obj: JsonObject): String {
        val result: MutableList<String> = mutableListOf()
        for ((name, value) in obj.asDict()) {
            val attrMd = classMd.getAttribute(name)
            if (attrMd!=null) {
                result.add(
                    "%30s = %s %s".format(
                        attrMd.displayName,
                        makeDisplay(classMd, name, value.asString()),
                        attrMd.unit
                    )
                )
            }
        }
        return result.joinToString("\n")
    }

    private fun showCollection(classMd: ClassMetadata, json: JsonObject): String {
        val table = Table(maxColumnWidth=Properties.getInt("parameter", "show_collection_max_field_width"),
            headingColor=Properties.get("parameter", "heading_color"),
            headingStyle=Properties.get("parameter", "heading_style")
        )
        for (obj in json.asArray().map{it.asDict()}) {
            val color = obj["color"]?.asString()
            for ((name, value) in obj) {
                table.append(name, makeDisplay(classMd, name, value.asString()), color=color)
            }
        }
        table.setColumns{ name: String, col: Table.Column ->
            col.position = if (name=="name") 0 else classMd.getAttribute(name)?.preference ?: 10000
            col.heading = classMd.getAttribute(name)?.displayName ?: makeNameHuman(name)
        }
        return table.render()
    }

    private fun makeDisplay(classMd: ClassMetadata, name: String, value: String): String {
        val attrMd = classMd.getAttribute(name)
        var result = value
        if (attrMd!=null) {
            try {
                val converted = attrMd.convert(value)
                result = converted.toString()
            } catch (exc: Exception) {
            }
        }
        return result
    }
}