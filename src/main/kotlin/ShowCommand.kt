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
    private var result: String = ""
    private val initialExtras = KeywordList(
        KeywordFn("health"){ result = showHealth() },
        KeywordFn("license"){ result = showLicense() },
        KeywordFn("metadata"){ result = showMetadata() },
        KeywordFn("parameters"){ result = showParameters() },
        KeywordFn("system"){ result = showSystem() },
        KeywordFn("version"){ result = showVersion() },
    )
    private val finalExtras = KeywordList(
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
        val (oname, terminator) = this.parser.getObjectName(initialExtras=initialExtras, finalExtras=finalExtras)
        classMd = oname.leafClass ?: Metadata.getClass("configuration")!!
        var myKey: Keyword? = terminator
        while (myKey != null) {
            myKey.function!!.invoke()
            if (result.isNotEmpty()) {
                println(StyledText(result).render())
                return
            }
            if (oname.leafClass == null) {
                throw CliException("expected object name after 'show'")
            }
            myKey = finalExtras.exactMatch(parser.curToken ?: "")
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
        val coll = Rest.getCollection(oname, options = optionsMap)
        if (coll.size == 0) {
            throw CliException("no matching objects found")
        } else if (oname.isWild || coll.size>1) {
            println(showCollection(coll))
        } else {
            println(showOne(coll.first()!!))
        }
    }

    private fun doSelect() {
        cli.checkRepeat({ selections.isNotEmpty() }, "select")
        val myExtras = finalExtras
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
        val myExtras = finalExtras
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
                    finalExtras.remove("by")
                    continue
                } else {
                    throw CliException("attribute name expected after 'top' or 'bottom'")
                }
            }
            order = str
            descending = desc
            break
        }
        parser.findKeyword(finalExtras, endOk=true)
    }

    private fun doLevel(l: String) {
        cli.checkRepeat({ level.isNotEmpty() }, msg = "duplicate level keyword '$l'")
        if (l !in levels) {
            throw CliException("invalid show level '$l'")
        }
        level = l
        parser.findKeyword(finalExtras, endOk=true)
    }

    private fun showOne(obj: ObjectData): String {
        val display = ColumnLayout(
            columns = Properties.getInt("parameter", "show_columns"),
            separator = "=",
            labelColumnWidth = Properties.getInt("parameter", "label_column_width"),
            valueColumnWidth = Properties.getInt("parameter", "value_column_width"),
            stripeColors = listOfNotNull(Properties.get("color", "even_row"), Properties.get("color", "odd_row"))
        )
        val objClass = obj["class"].value
        val objName = obj["name"].value
        val heading = StyledText("${Properties.get("class", objClass)} '${objName}' at ${getDateTime()}",
            color=Properties.get("parameter", "heading_color"),
            style="underline")
        val sortedValues = obj.attributes
            .filter { entry ->
                Properties.getInt("suppress", classMd.name, entry.value.name) == 0
            }
            .mapKeys { entry -> entry.value.attributeMd.displayName }
        for ((name, attributeData) in sortedValues) {
            display.append(
                name,
                "${cli.makeDisplayName(classMd, 
                    attributeData.name, 
                    attributeData.value)} ${attributeData.attributeMd.unit}"
            )
        }
        return "${heading.render()}\n${display.layoutText().render()}"
    }

    private fun showCollection(coll: CollectionData): String {
        val table = cli.makeTable()
        if (coll.isNotEmpty()) {
            for (obj in coll) {
                val color = obj.getOr("color")?.value
                for ((name, attributeData) in obj) {
                    if (Properties.get("suppress", classMd.name, name) == null || name == "name") {
                        table.append(
                            name,
                            attributeData.value,
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

    private fun showHealth(): String {
        return ""
    }

    private fun showLicense(): String {
        return ""
    }

    private fun showMetadata(): String {
        return ""
    }

    private fun showParameters(): String {
        return ""
    }

    private fun showSystem(): String {
        return ""
    }

    private fun showVersion(): String {
        return Rest.getObject("configurations/running",
            options=mapOf("select" to "build_version"))
            ?.asDict()?.get("build_version")?.asString() ?: "unknown"
    }
}

