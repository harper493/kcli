class ShowCommand(val cli: CliCommand) {
    private val parser get() = cli.parser
    private var classMd: ClassMetadata = Metadata.getClass("configuration")!!
    private val selections = mutableListOf<AttributeMetadata>()
    private var onlySelect = false
    private val filters = mutableListOf<String>()
    private var filterConjunction = ""
    private var order = ""
    private var descending: Boolean? = null
    private var limit = 100
    private var level = ""
    private val levels = listOf("brief", "full", "detail", "debug")
    private val extras = KeywordList(
        KeywordFn("select") { doSelect() },
        KeywordFn("with") { doWith() },
        KeywordFn("top") { doTopBottom(true) },
        KeywordFn("bottom") { doTopBottom(false) },
    ).also { keywords -> levels.map { keywords.add(Keyword(it, function = { doLevel(it) })) } }


    fun doShow() {
        val optionsMap = mutableMapOf(
            "link" to "name",
        )
        fun addOption(option: String, value: String) {
            if (value.isNotEmpty()) {
                optionsMap[option] = value
            }
        }
        val (oname, terminator) = this.parser.getObjectName(finalExtras = extras)
        if (oname.leafClass == null) {
            throw CliException("expected object name after 'show'")
        }
        classMd = oname.leafClass!!
        var myKey: Keyword? = terminator
        while (myKey != null) {
            myKey.function!!.invoke()
            myKey = extras.exactMatch(parser.curToken ?: "")
        }
        addOption(
            "level", when {
                level.isNotEmpty() -> level
                oname.isWild -> "brief"
                else -> "full"
            }
        )
        addOption("select",
            "${if (onlySelect || selections.isEmpty()) "" else "+"}${selections.joinToString(",") { it.name }}")
        addOption("with", filters.joinToString(if (filterConjunction == "and") "," else "|"))
        addOption(
            "order", when (descending) {
                null -> ""
                true -> "<$order"
                else -> ">$order"
            }
        )
        addOption("limit", if (limit > 0) "$limit" else "")
        classMd.getAttribute("color")?.let { selections.add(it) }
        val json = Rest.getCollection(oname.url, options = optionsMap)
        if (json != null) {
            if (oname.isWild) {
                println(showCollection(json))
            } else {
                for (obj in json.asArray()) {
                    println(showOne(obj))
                }
            }
        }
    }

    private fun doSelect() {
        cli.checkRepeat({ selections.isNotEmpty() }, "select")
        val myExtras = extras
            .copy()
            .addKeys("only")
        while (true) {
            val kw = cli.readAttribute(classMd, extras = myExtras, endOk=selections.isNotEmpty())
            if (kw?.attribute != null) {
                selections.add(kw.attribute)
            } else if (kw?.key == "only") {
                cli.checkRepeat({ onlySelect }, "only")
                onlySelect = true
            } else {
                break
            }
        }
        CliException.throwIf("no attributes found after 'select'"){ selections.isEmpty() }
    }

    private fun doWith() {
        cli.checkRepeat({ filters.isNotEmpty() }, "with")
        val relops = listOf("=", "!=", "<", ">", "<=", ">=", ">>", "<<", "!>>")
        val myExtras = extras
        while (true) {
            val negated = parser.skipToken("!")
            val kw = cli.readAttribute(classMd, extras = myExtras, endOk=true)
            if (kw?.value in listOf("and", "or")) {
                if (filterConjunction.isNotEmpty() && filterConjunction != kw!!.asString()) {
                    throw CliException("cannot mix 'and' and 'or' in the same command")
                }
                filterConjunction = kw!!.asString()
                continue
            }
            if (kw?.attribute == null) break
            val lhsAttr = kw.attribute
            var thisFilter = lhsAttr.name
            if (negated || !parser.peekAnyOf(relops)) {
                if (!lhsAttr.type.hasNull()) {
                    throw CliException("cannot use boolean operator with '${lhsAttr.name}'")
                } else if (negated) {
                    thisFilter = "!$thisFilter"
                }
            } else {
                thisFilter += parser.nextToken() ?: ""
                val rhs: String = if (lhsAttr.type.isNumeric()) {
                    if (parser.peekRx("[-+0-9]")) {
                        lhsAttr.type.validateCheck(parser.nextToken(validator = lhsAttr.type.validator)!!)
                    } else {
                        val (str, _, _) = cli.readComplexAttribute(classMd, extras = myExtras)
                        str
                    }
                } else {
                    lhsAttr.type.validateCheck(parser.nextToken(validator = lhsAttr.type.validator)!!)
                }
                thisFilter += rhs
            }
            filters.add(thisFilter)
            myExtras.addKeys("and", "or")
            if (filters.isEmpty()) {
                throw CliException("no valid filters found after 'with'")
            }
        }
        if (filterConjunction.isEmpty()) {
            filterConjunction = "and"
        }
    }

    private fun doTopBottom(desc: Boolean) {
        cli.checkRepeat({ descending != null }, msg = "cannot repeat 'top' or 'bottom'")
        limit = parser.getInt()
        val myExtras = KeywordList("by")
        while (true) {
            val (str, attrMd, kw) = cli.readComplexAttribute(classMd, extras=myExtras)
            if (attrMd == null) {
                if (kw?.asString() == "by") {
                    extras.remove("by")
                    continue
                } else {
                    throw CliException("attribute name expected after 'top' or 'bottom'")
                }
            }
            order = str
            descending = desc
            break
        }
        parser.findKeyword(extras, endOk=true)
    }

    private fun doLevel(l: String) {
        cli.checkRepeat({ level.isNotEmpty() }, msg = "duplicate level keyword '$l'")
        if (l !in levels) {
            throw CliException("invalid show level '$l'")
        }
        level = l
        parser.findKeyword(extras, endOk=true)
    }

    private fun showOne(obj: JsonObject): String {
        val display = ColumnLayout(
            columns = Properties.getInt("parameter", "show_columns"),
            separator = "=",
            labelColumnWidth = Properties.getInt("parameter", "label_column_width"),
            valueColumnWidth = Properties.getInt("parameter", "value_column_width"),
            stripeColors = listOfNotNull(Properties.get("color", "even_row"), Properties.get("color", "odd_row"))
        )
        val objDict = obj.asDict()!!
        val objClass = objDict["class"]?.asString() ?: ""
        val objName = objDict["name"]?.asString() ?: ""
        val heading = StyledText("${Properties.get("class", objClass)} '${objName}' at ${getDateTime()}",
            color=Properties.get("parameter", "heading_color"),
            style="underline")
        val sortedValues = objDict
            .filter { entry ->
                classMd.getAttribute(entry.key) != null
                        && Properties.getInt("suppress", classMd.name, entry.key) == 0
            }
            .mapValues { entry -> Pair(classMd.getAttribute(entry.key)!!, entry.value) }
            .mapKeys { entry -> entry.value.first.displayName }
        for ((name, value) in sortedValues) {
            val attrMd = value.first
            display.append(
                name,
                "${cli.makeDisplayName(classMd, attrMd.name, value.second.asString())} ${attrMd.unit}"
            )
        }
        return "${heading.render()}\n${display.layoutText().render()}"
    }

    private fun showCollection(json: JsonObject): String {
        val table = cli.makeTable()
        if (json.asArray().isNotEmpty()) {
            for (obj in json.asArray().map { it.asDict() }) {
                val color = obj["color"]?.asString()
                for ((name, value) in obj) {
                    val attrMd = classMd.getAttribute(name)
                    if (attrMd != null && (Properties.get("suppress", classMd.name, name) == null || name == "name")) {
                        table.append(
                            name,
                            cli.makeDisplayName(classMd, name, value.asString()),
                            color = color?.ifBlank { null })
                    }
                }
            }
            table.setColumns { name, col ->
                col.position = -(if (name == "name") 1000000 else classMd.getAttribute(name)?.preference ?: 0)
                col.heading = cli.abbreviateHeader((classMd.getAttribute(name)?.displayName ?: makeNameHuman(name)))
            }
            return table.layoutText().render()

        } else {
            throw CliException("no matching objects found")
        }
    }
}


