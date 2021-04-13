open class CliException(text: String="") : Exception(text)
{
    companion object {
        fun throwIf(text: String, pred: ()->Boolean) {
            if (pred()) {
                throw CliException(text)
            }
        }
    }
}

class CommandException(val terminate: Boolean=false) : CliException("")

class CliCommand(line: String) {

    lateinit var parser: Parser

    init {
        if (line.isNotEmpty()) {
            parser = Parser(line)
            val extras = KeywordList(
                KeywordFn("capture")  { doCapture() },
                KeywordFn("columns")  { ColumnsCommand(this).doColumns() },
                KeywordFn("count")    { ShowCommand(this, "count").doCount() },
                KeywordFn("dump")     { doDump() },
                KeywordFn("no")       { doNo() },
                KeywordFn("ping")     { doPing() },
                KeywordFn("quit")     { doQuit() },
                KeywordFn("reboot")   { doReboot() },
                KeywordFn("save")     { doSave() },
                KeywordFn("server")   { doServer() },
                KeywordFn("set")      { SetCommand(this).doSet() },
                KeywordFn("show")     { ShowCommand(this, "show").doShow() },
                KeywordFn("shutdown") { doShutdown() },
                KeywordFn("total")    { ShowCommand(this, "total").doTotal()},
                KeywordFn("traceroute"){ doTraceroute() },
            )
            val (objName, key) = parser.getObjectName(initialExtras = extras,
                keywordAdder={ classMd, keywords ->
                    if (classMd.name != "configuration")
                        classMd.settableAttributes.forEach{ keywords.addAttributes(it) } },
                missOk=true)
            if (objName.isEmpty) {
                if (key==null) {
                    throw CliException("unknown command or collection '${parser.curToken}'")
                }
                key?.invoke()
            } else {
                if (!parser.isFinished()) {
                    parser.backup()
                }
                doModify(objName)
            }
        }
    }

    private fun doModify(obj: ObjectName) {
        CliException.throwIf("object name cannot use wildcards in modify command"){ obj.isWild }
        val exists = try { Rest.getJson(obj.url, mapOf("select" to "name")); true }
        catch (exc: RestException) {
            if (HttpStatus.notFound(exc.status)) false else throw exc
        }
        if (!exists) {
            try { Rest.getJson(obj.dropLast(1).url) }
            catch (exc: RestException) {
                if (HttpStatus.notFound(exc.status)) {
                    throw CliException("parent object '${obj.dropLast(1).leafName}' does not exist")
                } else throw exc
            }
        }
        val classMd = obj.leafClass!!
        val values = readAttributes(classMd, exists)
        if (!exists) {
            val missing = classMd.requiredAttributes.filter{ it !in values && it.name!="name" }
            if (missing.isNotEmpty()) {
                if (missing.size==1 && missing[0].type.name=="password") {
                    values[missing[0].name] = Cli.getPassword()
                } else {
                    throw CliException("the following attributes must be specified: " +
                            missing.joinToString(", ") { it.name })
                }
            }
        }
        parser.checkFinished()
        Rest.put(obj.url, values)
    }

    private fun doQuit() {
        throw CommandException(terminate=true)
    }

    private fun doReboot() {
        val values = readPartitionConfig(getConfig=true)
        parser.checkFinished()
        if (readYesNo("Reboot system immediately")) {
            values["reload_system"] = "1"
            Rest.put("configurations/running", values)
        }
    }

    private fun doShutdown() {
        parser.checkFinished()
        if (readYesNo("Shut system down immediately")) {
            Rest.put("configurations/running", mapOf("shutdown_system" to "1"))
        }
    }

    private fun doSave() {
        val values = readPartitionConfig(allowBoth=true)
        parser.checkFinished()
        values["save_config"] = "1"
        Rest.put("configurations/running", values)
    }

    private fun doDump() {
        val keywords = KeywordList(*CliMetadata.getConfigMd()
            .settableAttributes
            .filter{ it.name.startsWith("dump_")}
            .map{ it.name.split("_").drop(1).joinToString("_")}
            .toTypedArray())
        val kw = parser.findKeyword(keywords)!!
        parser.checkFinished()
        Rest.put("configurations/running", mapOf("dump_${kw.asString()}" to "1"))
    }

    private fun doNo() {
        val extras = KeywordList(KeywordFn("server", { doNoServer() }))
        val (objName, key) = parser.getObjectName(initialExtras = extras, missOk=true)
        if (objName.isEmpty) {
            if (key==null) {
                throw CliException("unknown command or collection '${parser.curToken}'")
            }
            key.invoke()
        } else {
            CliException.throwIf("cannot delete a wildcard object") { objName.isWild }
            parser.checkFinished()
            Rest.delete(objName.url)
        }
    }

    private fun doServer() {
        val serverName = parser.nextToken(tokenType=Parser.TokenType.ttName)!!
        val target = parser.nextToken(tokenType = Parser.TokenType.ttNonBlank, endOk = true)
        parser.checkFinished()
        if (target == null) {
            Server.getLast()?.saveAs(serverName)
        } else {
            Server(target).saveAs(serverName)
        }
    }

    private fun doNoServer() {
        val serverName = parser.nextToken(tokenType=Parser.TokenType.ttName)!!
        parser.checkFinished()
        Server.remove(serverName)
    }

    fun makeDisplayName(classMd: ClassMetadata, name: String, value: String): String {
        val attrMd = classMd.getAttribute(name)
        var result = value
        if (attrMd != null) {
            try {
                val converted = attrMd.convert(value)
                result = converted.toString()
            } catch (exc: Exception) {
            }
        }
        return result
    }

    fun checkRepeat(pred: ()->Boolean, keyword: String="", msg: String="") {
            CliException.throwIf(if (msg.isEmpty()) "keyword '$keyword' repeated" else msg, pred)
    }

    fun readAttributes(classMd: ClassMetadata,
                       exists: Boolean,
                       extras:KeywordList = KeywordList(),
                       exclude: (String)->Boolean = { false }): MutableMap<String, String> {
        val keywords = KeywordList(
            classMd.settableAttributes
                .filter{!exclude(it.name)})
            .add(extras)
            .addKeys("no")
        val values = mutableMapOf<String,String>()
        while (true) {
            var k = parser.findKeyword(keywords, endOk=true)?: break
            if (k.function!=null) {
                k()
            } else {
                val noSeen: Boolean = k.key == "no"
                if (noSeen) {
                    keywords.remove("no")
                    val kk = parser.findKeyword(keywords)
                    CliException.throwIf("attribute expected after 'no'") { kk == null }
                    k = kk!!
                }
                val attrMd = k.attribute ?: break
                CliException.throwIf(
                    "attribute '${attrMd.name}' can only be set when an object is created"
                ) { exists && !attrMd.isModifiable }
                if (noSeen) {
                    if (attrMd.type.name == "bool") {
                        values[attrMd.name] = "F"
                    } else {
                        CliException.throwIf("'no' cannot be used with attribute '${attrMd.name}'")
                        { !attrMd.type.hasNull() }
                        values[attrMd.name] = ""
                    }
                } else if (attrMd.type.name == "bool") {
                    values[attrMd.name] = "T"
                } else {
                    parser.nextToken(completer = attrMd.completer(), validator = attrMd.type.validator)
                    CliException.throwIf("value expected for attribute '${attrMd.name}'") { parser.curToken == null }
                    values[attrMd.name] = parser.curToken!!
                }
            }
        }
        return values
    }

    fun readPartitionConfig(getConfig:Boolean = false, allowBoth: Boolean = false): MutableMap<String,String> {
        val values = mutableMapOf<String,String>()
        val keywords = KeywordList("partition")
        if (getConfig) {
            keywords.addKeys("configuration")
        }
        val partitions = KeywordList("current", "alternate")
        if (allowBoth) {
            partitions.addKeys("both")
        }
        val seen = mutableSetOf<String>()
        while (!parser.isFinished()) {
            val kw = parser.findKeyword(keywords, endOk=true)?.asString()
            if (kw!=null) {
                checkRepeat({ kw in seen}, kw)
                seen.add(kw)
                when (kw) {
                    "partition" ->
                        values["boot_partition"] =
                            parser.findKeyword(partitions)!!.asString()
                    "configuration" ->
                        values["boot_config"] =
                            parser.nextToken(completer=ObjectCompleter(ObjectName("configurations/")))!!
                    else -> { }
                }
            }
        }
        return values
    }

    fun abbreviateHeader(header: String) =
        header.split(" ")
            .joinToString(" ") { Properties.get("replace", it.toLowerCase()) ?: it }

    fun readAttribute(
        classMd: ClassMetadata,
        pred: (AttributeMetadata) -> Boolean = { true },
        extras: KeywordList = KeywordList(),
        missOk: Boolean = false,
        endOk: Boolean = false

    ): Keyword? =
        parser.findKeyword(KeywordList(classMd.attributes, pred).add(extras), missOk=missOk, endOk=endOk)

    fun readComplexAttribute(
        classMd: ClassMetadata,
        pred: (AttributeMetadata) -> Boolean = { true },
        extras: KeywordList = KeywordList(),
        missOk: Boolean = false,
        followLinks: Boolean = true,
        followOwners: Boolean = true,
    ): Triple<String, AttributeMetadata?, Keyword?> {
        var cmd = classMd
        val elements = mutableListOf<String>()
        while (true) {
            val parentClassName = cmd.parentClass?.name
            if (followOwners and !cmd.isRoot) {
                extras.addKeys(parentClassName!!)
            }
            val a = readAttribute(cmd, pred, extras, missOk)
            val attrMd = a?.attribute
            cmd = if (attrMd != null) {
                elements.add(attrMd.name)
                if (attrMd.isRelation && followLinks && parser.skipToken(".")) {
                    attrMd.type.getClass()!!
                } else {
                    return Triple(elements.joinToString("."), attrMd, null)
                }
            } else if (a?.value == parentClassName && followOwners && parser.skipToken(".")) {
                elements.add(parentClassName!!)
                cmd.parentClass!!
            } else {
                return Triple(elements.joinToString("."), attrMd, a)
            }
        }
    }

    fun makeTable() = Table(
            maxColumnWidth = Properties.getInt("parameter", "show_collection_max_field_width"),
            headingColor = Properties.getParameter("heading_color"),
            headingStyle = Properties.getParameter("heading_style"),
            stripeColors = Properties.getColors("even_row", "odd_row"))

    fun output(text: String) = Cli.output(text)
    fun outputln(text: String) = Cli.outputln(text)
    fun output(text: StyledText) = Cli.output(text.render())
    fun outputln(text: StyledText) = Cli.outputln(text.render())
}

/*
Extension functions for other classes
 */

fun Properties.Companion.getColor(color: String) = properties.get("color", color)
fun Properties.Companion.getColors(vararg colors: String) = colors.mapNotNull { getColor(it) }
fun Properties.Companion.getParameter(name: String) = properties.get("parameter", name)
fun Properties.Companion.getParameterInt(name: String, default: Int = 0) =
    properties.get("parameter", name)?.toIntOrNull() ?: default
