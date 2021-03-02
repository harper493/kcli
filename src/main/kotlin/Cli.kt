class CliException(text: String="") : Exception(text)

class Cli {
    lateinit var parser: Parser

    fun oneLine(line: String) {
        parser = Parser(line)
        val commands = KeywordList(
            KeywordFn("show", { doShow() }),
            KeywordFn("quit", { doQuit() })
        )
        parser.nextToken()
        val cmd = parser.findKeyword(commands, missOk=true)
        if (cmd == null) {                // this may be an object name instead
            doModify(parser.getObjectName(missOk=true).first
                .also{ if (it.isEmpty) throw CliException("unknown command or object class")})
        } else {
            cmd.function?.invoke()
        }
    }

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
        val values = mutableMapOf<String,String>()
        while (true) {
            parser.nextToken()
            val attrMd = parser.findKeyword(keywords)?.attribute
            if (attrMd==null) break
            parser.reparse(validator=attrMd.type.validator)
            values[attrMd.name] = parser.curToken!!
        }
        val body = values.map{ (key, value) -> "\"$key\":\"$value\""}.joinToString(",")
        Rest.put(obj.url, "{$body}")
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
        if (pred()) {
            throw CliException(if (msg.isEmpty()) "keyword '$keyword' repeated" else msg)
        }
    }

    fun abbreviateHeader(header: String) =
        header.split(" ")
            .joinToString(" ") { Properties.get("replace", it.toLowerCase()) ?: it }

    fun readAttribute(
        classMd: ClassMetadata,
        pred: (AttributeMetadata) -> Boolean = { true },
        extras: KeywordList = KeywordList(),
        missOK: Boolean = false
    ): Keyword? =
        parser.findKeyword(KeywordList(classMd.attributes, pred).add(extras), missOK)

    fun readComplexAttribute(
        classMd: ClassMetadata,
        pred: (AttributeMetadata) -> Boolean = { true },
        extras: KeywordList = KeywordList(),
        missOK: Boolean = false,
        followLinks: Boolean = true,
        followOwners: Boolean = true,
    ): Triple<String, AttributeMetadata?, Keyword?> {
        var cmd = classMd
        val elements = mutableListOf<String>()
        while (true) {
            val thisExtra = extras
            val parentClassName = cmd.parentClass?.name
            if (followOwners and !cmd.isRoot) {
                thisExtra.addKeys(parentClassName!!)
            }
            val a = readAttribute(cmd, pred, thisExtra, missOK)
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

}