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

class CliCommand(val line: String) {

    lateinit var parser: Parser

    init {
        if (line.isNotEmpty()) {
            parser = Parser(line)
            val extras = KeywordList(
                KeywordFn("show") { doShow() },
                KeywordFn("quit") { doQuit() }
            )
            val (objName, key) = parser.getObjectName(initialExtras = extras)
            if (objName.isEmpty) {
                key?.invoke()
            } else {
                doModify(objName)
            }
        }
    }

    private fun doShow() = ShowCommand(this).doShow()

    private fun doModify(obj: ObjectName) {
        val exists = try { Rest.get(obj.url, mapOf("select" to "name")); true }
        catch (exc: RestException) {
            if (HttpStatus.notFound(exc.status)) false else throw exc
        }
        if (!exists) {
            try { Rest.get(obj.dropLast(1).url) }
            catch (exc: RestException) {
                if (HttpStatus.notFound(exc.status)) {
                    throw CliException("parent object '${obj.dropLast(1).leafName}' does not exist")
                } else throw exc
            }
        }
        val classMd = obj.leafClass!!
        val keywords = KeywordList(if (exists) classMd.modifiableAttributes
                                   else classMd.settableAttributes)
        keywords.addKeys("no")
        val values = mutableMapOf<String,String>()
        while (true) {
            var k = parser.findKeyword(keywords)?: break
            val noSeen: Boolean = k.key=="no"
            if (noSeen) {
                keywords.remove("no")
                val kk = parser.findKeyword(keywords)
                CliException.throwIf("attribute expected after 'no'"){ kk==null }
                k = kk!!
            }
            val attrMd = k.attribute ?: break
            if (noSeen) {
                if (attrMd.type.name=="bool") {
                    values[attrMd.name] = "F"
                } else {
                    CliException.throwIf("'no' cannot be used with attribute '${attrMd.name}'")
                        { !attrMd.type.hasNull() }
                    values[attrMd.name] = ""
                }
            } else if (attrMd.type.name=="bool") {
                values[attrMd.name]= "T"
            } else {
                parser.nextToken(validator = attrMd.type.validator)
                CliException.throwIf("value expected for attribute '${attrMd.name}'") { parser.curToken == null }
                values[attrMd.name] = parser.curToken!!
            }
        }
        CliException.throwIf("unexpected text at end of line '${parser.curToken}'"){ parser.curToken!=null }
        val body = values.toJson()
        println(body)
        Rest.put(obj.url, body)
    }

    private fun doQuit() {
        throw CliException()
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
            stripeColors = listOfNotNull(Properties.get("color", "even_row"), Properties.get("color", "odd_row"))
        )
}