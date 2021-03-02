
class ShowCommand(val cli: Cli) {
    val parser get() = cli.parser
    var options = mutableMapOf<String, String>()
    var classMd: ClassMetadata = Metadata.getClass("configuration")!!
    val selections = mutableListOf<AttributeMetadata>()
    var onlySelect = false
    val filters = mutableListOf<String>()
    var filterConjunction = ""
    var order = ""
    var descending: Boolean? = null
    var limit = 100
    var level = ""
    val levels = listOf("brief", "full", "detail", "debug")
    val extras = KeywordList(
        KeywordFn("select", { doSelect() }),
        KeywordFn("with", { doWith() }),
        KeywordFn("top", { doTopBottom(true) }),
        KeywordFn("bottom", { doTopBottom(false) }),
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
        val (oname, terminator) = this.parser.getObjectName(extras = extras)
        if (oname.leafClass == null) {
            throw CliException("expected object name after 'show'")
        }
        classMd = oname.leafClass!!
        doShowOptions(terminator)
        addOption(
            "level", when {
                level.isNotEmpty() -> level
                oname.isWild -> "brief"
                else -> "full"
            }
        )
        classMd.getAttribute("color")?.let { selections.add(it) }
        addOption("select", "${if (onlySelect) "" else "+"}${selections.map { it.name }.joinToString(",")}")
        addOption("with", filters.joinToString(if (filterConjunction == "and") "," else "|"))
        addOption(
            "order", when (descending) {
                null -> ""
                true -> "<$order"
                else -> ">$order"
            }
        )
        addOption("limit", if (limit > 0) "$limit" else "")
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

    fun doShowOptions(key: Keyword?) {
        var myKey: Keyword? = key
        while (myKey != null) {
            if (myKey?.function != null) {
                (myKey.function!!)()
            }
            myKey = parser.lastKeyword ?: parser.findKeyword(extras)
        }
    }

    fun doSelect() {
        cli.checkRepeat({ selections.isNotEmpty() }, "select")
        val myExtras = extras
            .copy()
            .addKeys("only")
        parser.skipToken("select")
        while (true) {
            val kw = cli.readAttribute(classMd, extras = myExtras)
            if (kw?.attribute != null) {
                selections.add(kw.attribute)
            } else if (kw?.key == "only") {
                cli.checkRepeat({ onlySelect }, "only")
                onlySelect = true
            } else {
                break
            }
        }
        if (selections.isEmpty()) {
            throw CliException("no valid attributes found after 'select'")
        }
    }

    fun doWith() {
        cli.checkRepeat({ filters.isNotEmpty() }, "with")
        val relops = listOf("=", "!=", "<", ">", "<=", ">=", ">>", "<<", "!>>")
        parser.skipToken("with")
        while (true) {
            val negated = parser.skipToken("!")
            val kw = cli.readAttribute(classMd, extras = extras)
            if (kw?.attribute == null) break
            parser.useKeyword()
            val lhsAttr = kw.attribute
            var thisFilter = lhsAttr.name
            if (negated || parser.curToken !in relops) {
                if (!lhsAttr.type.hasNull()) {
                    throw CliException("cannot use boolean operator with '${lhsAttr.name}'")
                } else if (negated) {
                    thisFilter = "!$thisFilter"
                }
            } else {
                thisFilter += parser.curToken
                var rhs: String
                if (lhsAttr.type.isNumeric()) {
                    parser.nextToken()
                    val (str, attrMd, _) = cli.readComplexAttribute(classMd, extras = extras, missOK = true)
                    if (attrMd == null) {
                        rhs = parser.curToken ?: ""
                        lhsAttr.type.validateCheck(rhs)
                    } else {
                        rhs = str
                    }
                } else {
                    parser.nextToken(validator = lhsAttr.type.validator)
                    lhsAttr.type.validateCheck(parser.curToken ?: "")
                    rhs = parser.curToken ?: ""
                }
                thisFilter += rhs
                parser.nextToken(endOk = true)
                parser.useKeyword()
            }
            filters.add(thisFilter)
            var conj = ""
            for (c in listOf("and", "or")) {
                if (parser.skipToken(c)) {
                    conj = c
                    break
                }
            }
            if (conj.isNotEmpty()) {
                if (filterConjunction.isEmpty()) {
                    filterConjunction = conj
                } else if (filterConjunction != conj) {
                    throw CliException("cannot mix 'and' and 'or' in the same command")
                }
            }
        }
        if (filterConjunction.isEmpty()) {
            filterConjunction = "and"
        }
        if (filters.isEmpty()) {
            throw CliException("no valid filters found after 'with'")
        }
    }

    fun doTopBottom(desc: Boolean) {
        cli.checkRepeat({ descending != null }, msg = "cannot repeat 'top' or 'bottom'")
        limit = parser.getNumber()
        parser.skipToken("by")
        val (str, attrMd, _) = cli.readComplexAttribute(classMd)
        if (attrMd == null) {
            throw CliException("attribute name expected after 'top' or 'bottom'")
        }
        order = str
        descending = desc
        parser.useKeyword()
    }

    fun doLevel(l: String) {
        cli.checkRepeat({ level.isNotEmpty() }, msg = "duplicate level keyword '$l'")
        if (l !in levels) {
            throw CliException("invalid show level '$l'")
        }
        parser.useKeyword()
        parser.skipToken(l)
        level = l
    }

    fun showOne(obj: JsonObject): String {
        val display = ColumnLayout(
            columns = Properties.getInt("parameter", "show_columns"),
            separator = "=",
            labelColumnWidth = Properties.getInt("parameter", "label_column_width"),
            valueColumnWidth = Properties.getInt("parameter", "value_column_width"),
            stripeColors = listOfNotNull(Properties.get("color", "even_row"), Properties.get("color", "odd_row"))
        )
        val sortedValues = obj.asDict()
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
        return display.layoutText().renderISO6429()
    }

    fun showCollection(json: JsonObject): String {
        val table = Table(
            maxColumnWidth = Properties.getInt("parameter", "show_collection_max_field_width"),
            headingColor = Properties.get("parameter", "heading_color"),
            headingStyle = Properties.get("parameter", "heading_style"),
            stripeColors = listOfNotNull(Properties.get("color", "even_row"), Properties.get("color", "odd_row"))
        )
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
            return table.layoutText().renderISO6429()

        } else {
            return StyledText("no matching objects found", color = Properties.get("color", "error"))
                .renderISO6429()
        }
    }
}

fun Cli.doShow() = ShowCommand(this).doShow()

