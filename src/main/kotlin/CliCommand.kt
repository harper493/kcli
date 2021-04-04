class CliException(text: String="") : Exception(text)
{
    companion object {
        fun throwIf(text: String, pred: ()->Boolean) {
            if (pred()) {
                throw CliException(text)
            }
        }
    }
}

class CliCommand(line: String) {

    lateinit var parser: Parser

    init {
        if (line.isNotEmpty()) {
            parser = Parser(line)
            val extras = KeywordList(
                KeywordFn("count")    { ShowCommand(this, "count").doCount() },
                KeywordFn("dump")     { doDump() },
                KeywordFn("ping")     { doPing() },
                KeywordFn("quit")     { doQuit() },
                KeywordFn("reboot")   { doReboot() },
                KeywordFn("save")     { doSave() },
                KeywordFn("set")      { SetCommand(this).doSet() },
                KeywordFn("show")     { ShowCommand(this, "show").doShow() },
                KeywordFn("shutdown") { doShutdown() },
                KeywordFn("total",    { ShowCommand(this, "total").doTotal()})
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
        val exists = try { Rest.getRaw(obj.url, mapOf("select" to "name")); true }
        catch (exc: RestException) {
            if (HttpStatus.notFound(exc.status)) false else throw exc
        }
        if (!exists) {
            try { Rest.getRaw(obj.dropLast(1).url) }
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
                    values.put(missing[0].name, Cli.getPassword())
                } else {
                    throw CliException("the following attributes must be specified: ${missing.map{ it.name }
                        .joinToString(", ")}")
                }
            }
        }
        parser.checkFinished()
        Rest.put(obj.url, values)
    }

    private fun doQuit() {
        throw CliException()
    }

    private fun doReboot() {
        val values = readPartitionConfig(getConfig=true)
        if (readYesNo("Reboot system immediately")) {
            values["reload_system"] = "1"
            Rest.put("configurations/running", values)
        }
    }

    private fun doShutdown() {
        if (readYesNo("Shut system down immediately")) {
            Rest.put("configurations/running", mapOf("shutdown_system" to "1"))
        }
    }

    private fun doSave() {
        val values = readPartitionConfig(allowBoth=true)
        values["save_config"] = "1"
        Rest.put("configurations/running", values)
    }

    private fun doDump() {
        val keywords = KeywordList(*Metadata.getConfigMd()
            .settableAttributes
            .filter{ it.name.startsWith("dump_")}
            .map{ it.name.split("_").drop(1).joinToString("_")}
            .toTypedArray())
        val kw = parser.findKeyword(keywords)!!
        Rest.put("configurations/running", mapOf("dump_${kw.asString()}" to "!"))
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
                       extras:KeywordList = KeywordList()): MutableMap<String, String> {
        val keywords = KeywordList(classMd.settableAttributes)
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
                        values["boot_config_name"] =
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
            headingColor = Properties.get("parameter", "heading_color"),
            headingStyle = Properties.get("parameter", "heading_style"),
            stripeColors = Properties.getColors("even_row", "odd_row"))

    fun output(text: String) = Cli.output(text)
    fun outputln(text: String) = Cli.outputln(text)
}