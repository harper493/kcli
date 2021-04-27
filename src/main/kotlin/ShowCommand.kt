import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit
import java.util.*

class ShowCommand(val cli: CliCommand, val verb: String) {
    private val parser get() = cli.parser
    private var classMd: ClassMetadata = CliMetadata.getClass("configuration")!!
    private val selections = mutableListOf<AttributeMetadata>()
    private var onlySelect = false
    private val filters = mutableListOf<String>()
    private var filterConjunction = ""
    private var order = ""
    private var descending: Boolean? = null
    private var limit = 100
    private var level = ""
    private var from: LocalDateTime? = null
    private var until: LocalDateTime? = null
    private var every: DateInterval? = null
    private var forInterval: DateInterval? = null
    private val optionsMap = mutableMapOf<String, String>()
    private lateinit var objectName: ObjectName
    private val levels = listOf("brief", "full", "detail", "debug")
    private var result = StyledText("")
    private val pageSize = Properties.getInt("parameter", "page_size")
    private val finalExtras = KeywordList()
    private val initialExtras = KeywordList(
        KeywordFn("columns") { result = ColumnsCommand(cli).makeShow(needClass=true) },
        KeywordFn("health") { result = showHealth() },
        KeywordFn("license") { result = showLicense() },
        KeywordFn("metadata") { result = showCliMetadata() },
        KeywordFn("parameters") { result = showParameters() },
        KeywordFn("pointers") { result = showPointers() },
        KeywordFn("servers") { result = showServers() },
        KeywordFn("system") { result = showSystem() },
        KeywordFn("version") { result = showVersion() },
    )
    private val finalExtraTemplate = KeywordList(
        KeywordFn("select") { doSelect() },
        KeywordFn("with") { doWith() },
        KeywordFn("top") { doTopBottom(true) },
        KeywordFn("bottom") { doTopBottom(false) },
        KeywordFn("from") { doFrom() },
        KeywordFn("every") { doEvery() },
        KeywordFn("until") { doUntil() },
        KeywordFn("for") { doFor() },
    )

    fun doShow() {
        getShowInput()
        if (result.isNotEmpty()) {
            println(result.render())
            return
        }
        if (from ?: until ?: every ?: forInterval != null) {
            doShowHistory()
        } else {
            makeOptions()
            parser.checkFinished()
            var (envelope, coll) = Rest.get(objectName, options = optionsMap)
            if (coll.size == 0) {
                throw CliException("no matching objects found")
            } else if (objectName.isWild || coll.size > 1) {
                cli.outputln(showCollection(objectName, coll).render())
                var start = 0
                while ((envelope["size"]?.toInt() ?: 0) > (pageSize + start)
                    && run {
                        cli.output(StyledText().render())
                        true
                    }
                    && readYesNo("Show more", defaultNo = false, allowQuit = true)
                ) {
                    start += pageSize
                    optionsMap["start"] = "$start"
                    coll = Rest.getCollection(objectName, options = optionsMap)
                    cli.outputln(showCollection(objectName, coll).render())
                }
            } else {
                cli.outputln(showOne(coll.first()!!).render())
            }
        }
    }

    fun doCount() {
        getShowInput(exclude = listOf("select", "top", "bottom", "limit", "level"))
        optionsMap["limit"] = "1"
        optionsMap["level"] = "list"
        makeOptions()
        val (envelope, _) = Rest.get(objectName, options = optionsMap)
        val quantity = envelope["size"]?.toInt() ?: 0
        val result = StyledText(
            "$quantity matching ${classMd.name.makePlural(quantity)} found",
            color = "result"
        )
        cli.outputln(result.render())
    }

    fun doTotal() {
        getShowInput(exclude = listOf("level"))
        optionsMap["limit"] = "1"
        optionsMap["total"] = "post"
        CliException.throwIf("must use 'select' for attributes in total command") { selections.isEmpty() }
        makeOptions(doColor = false)
        val totals = Rest.getTotals(objectName, options = optionsMap)
        CliException.throwIf("no matching objects found")
        {
            totals.filter { it.key.startsWith("_count") }
                .values.map { it.toIntOrNull() ?: 0 }.sum() == 0
        }
        val table = cli.makeTable()
        selections
            .filter { it.name in totals }
            .sortedBy { it.displayName }
            .forEach { attr ->
                val count = maxOf(1, ((totals["_count_${attr.name}"] ?: "1").toIntOrNull() ?: 1))
                val total = attr.type.convert((totals[attr.name] ?: "0"))
                table.append(
                    "Attribute" to attr.displayName,
                    "Count" to "$count",
                    "Total" to (attr.total == "sum").ifElse("$total ${attr.unit}", ""),
                    "Average" to "${
                        when (attr.total) {
                            "average" -> "$total"
                            "sum" -> attr.type.formatter(total.toFloat() / count.toFloat())
                            else -> ""
                        }
                    } ${attr.unit}"
                )
            }
        cli.outputln(table.render())
    }

    private fun doFrom() {
        from = getHistoryTime(parser)
        parser.findKeyword(finalExtras, endOk = true)
    }

    private fun doUntil() {
        until = getHistoryTime(parser)
        parser.findKeyword(finalExtras, endOk = true)
    }

    private fun doEvery() {
        every = DateInterval.read(parser)
        parser.findKeyword(finalExtras, endOk = true)
    }

    private fun doFor() {
        forInterval = DateInterval.read(parser)
        parser.findKeyword(finalExtras, endOk = true)
    }

    private fun doShowHistory() {
        val now = LocalDateTime.now().atHour()
        CliException.throwIf("'every' option requires a single object"){
            objectName.isWild && every != null
        }
        when {
            from == null && until == null && forInterval == null ->
                throw CliException("history request requires at least one of 'from', 'until' and 'for'")
            from != null && until == null && forInterval == null ->
                until = now
            from != null && until != null && forInterval == null ->
                CliException.throwIf("'from' time must be earlier than 'until' time)")
                { from!! >= until!! }
            from != null && until == null && forInterval != null ->
                until = forInterval!!.addTo(from!!)
            from != null && until != null && forInterval != null ->
                throw CliException("only two of 'from', 'until' and 'for' permitted")
            from == null && until != null && forInterval == null ->
                throw CliException("must specify 'from' or 'for' with 'until'")
            from == null && until != null && forInterval != null ->
                from = forInterval!!.subtractFrom(until!!)
            from == null && until == null && forInterval != null -> {
                from = forInterval!!.subtractFrom(now)
                until = now
            }
        }
        selections.filter { !it.isHistory }
            .let {
                CliException
                    .throwIf("attributes ${it.joinToString(", ")} are not available in history")
                    { it.isNotEmpty() }
            }
        if (!onlySelect) {
            objectName.leafClass?.attributes
                ?.filter{ it.isBrief && it.isHistory && it.total!="none" }
                ?.forEach { selections.add(it) }
            onlySelect = true
        }
        parser.checkFinished()
        if (every!=null || !objectName.isWild) {
            doShowHistoryPoints()
        } else {
            makeOptions()
            var (_, coll) = Rest.get(objectName, options = optionsMap)
            cli.outputln(
                StyledText(
                    "History from ${from!!.toNiceString()} until ${until!!.toNiceString()}",
                    color = "heading"
                )
            )
            cli.outputln(showCollection(objectName, coll).render())
        }
    }

    private fun doShowHistoryPoints() {
        var time = from!!.toUnix()
        from = from?.minusMinutes(1)
        makeOptions()
        optionsMap["history_points"] = "1"
        var (_, coll) = Rest.get(objectName, options = optionsMap)
        if (every == null) {
            every =
                with(from!!.until(until!!, ChronoUnit.HOURS)) {
                    when {
                        this < 48 -> DateInterval(1, IntervalType.hour)
                        this <= 24 * 7 -> DateInterval(6, IntervalType.hour)
                        this <= 24 * 32 -> DateInterval(1, IntervalType.day)
                        else -> DateInterval(1, IntervalType.week)
                    }
                }
        }
        val header = StyledText("History for ${objectName.describe()} "
                + "from ${from!!.toNiceString()} until ${until!!.toNiceString()}\n", color = "heading")
        val table = cli.makeTable()
        if (coll.isEmpty()) {
            if (Rest.getObject(objectName.url) == null ) {
                throw CliException("no matching objects found for ${objectName.describe()}")
            } else {
                throw CliException("no data found for ${objectName.describe()} for period ${from!!.toNiceString()} to ${until!!.toNiceString()}")
            }
        }
        val untilUnix = until!!.toUnix()
        while (time < untilUnix) {
            val historyValue = coll.first()!!.getHistoryValue(time)
            if (!historyValue.isValid) {
                break
            }
            table.append("Time", unixToLocalDateTime(historyValue.actualTime).toNiceString())
            historyValue.attributes!!
                .map{ it.value }
                .filter{ !it.attributeMd.suppressed && it.name!="name" }
                .forEach{ table.append(it.name, it.value) }
            time = every!!.addTo(historyValue.actualTime)
        }
        val columnOrder = ColumnOrder[classMd.name]
        table.setColumns { name, col ->
            val attrMd = classMd.getAttribute(name)
            col.position =
                if (name=="Time") -1
                else columnOrder?.getPosition(attrMd?.name ?: "") ?: 0
            col.heading = cli.abbreviateHeader((attrMd?.displayName ?: makeNameHuman(name)))
        }
        val styled = table.renderStyled()
        cli.outputln(StyledText(header, StyledText(), styled))

    }

    private fun getShowInput(exclude: Iterable<String> = listOf()) {
        optionsMap["link"] = "name"
        finalExtraTemplate.keywords
            .forEach { if (it.second.key !in exclude) finalExtras.add(it.second) }
        if ("levels" !in exclude) {
            levels.map { finalExtras.addOne(Keyword(it, function = { doLevel(it) })) }
        }
        val (oname, terminator) = parser.getObjectName(initialExtras = initialExtras,
            helpContext = HelpContext(listOf("show"),
                { collName-> Properties.get("nav", collName)
                    ?.let { "Select objects from $it" }}),
            finalExtras = finalExtras,
            initialPred = { !it.name.startsWith("parameter") })
        classMd = oname.leafClass ?: CliMetadata.getClass("configuration")!!
        var myKey: Keyword? = terminator
        while (myKey != null) {
            myKey.function!!.invoke()
            if (result.isNotEmpty()) {
                break
            }
            myKey = finalExtras.exactMatch(parser.curToken ?: "")
        }
        objectName = oname
    }

    private fun makeOptions(doColor: Boolean = true) {
        fun addOption(option: String, value: String) {
            if (value.isNotEmpty()) {
                optionsMap[option] = value
            }
        }
        if (doColor && from==null) {
            classMd.getAttribute("color")?.let { selections.add(it) }
        }
        (ColumnOrder[classMd.name]?.usedFields ?: listOf())
            .map{ classMd.getAttribute(it)!! }
            .filter{ !it.isBrief }
            .forEach{ selections.add(it) }
        addOption("select",
            (if (onlySelect || selections.isEmpty()) "" else "+") +
                    selections.joinToString(",") { it.name })
        var with = filters.joinToString(
            (filterConjunction == "and")
                .ifElse(",", "|")
        )
        val converted = objectName.convertWild().joinToString(",")
        if (converted.isNotEmpty()) {
            with = if (with.isEmpty()) {
                converted
            } else {
                "$with,$converted"
            }
        }
        addOption("with", with)
        addOption(
            "level", when {
                level.isNotEmpty() -> level
                objectName.isWild -> "brief"
                else -> "full"
            }
        )
        addOption(
            "order", when (descending) {
                null -> ""
                true -> "<$order"
                else -> ">$order"
            }
        )
        addOption("from", from?.toString() ?: "")
        addOption("limit", "${minOf(limit, pageSize).takeIf { it > 0 } ?: pageSize}")
    }

    private fun doSelect() {
        cli.checkRepeat({ selections.isNotEmpty() }, "select")
        val myExtras = finalExtras
            .copy()
            .addKeys("only")
        while (true) {
            val kw = cli.readAttribute(classMd, extras = myExtras, endOk = selections.isNotEmpty())
            if (kw?.attribute != null) {
                selections.add(kw.attribute)
            } else if (kw?.key == "only") {
                cli.checkRepeat({ onlySelect }, "only")
                onlySelect = true
            } else {
                break
            }
        }
        CliException.throwIf("no attributes found after 'select'") { selections.isEmpty() }
    }

    private fun doWith() {
        cli.checkRepeat({ filters.isNotEmpty() }, "with")
        val relops = listOf("=", "!=", "<", ">", "<=", ">=", ">>", "<<", "!>>")
        val myExtras = finalExtras
        while (true) {
            val negated = parser.skipToken("!")
            val kw = cli.readAttribute(classMd, extras = myExtras, endOk = true)
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
            val (str, attrMd, kw) = cli.readComplexAttribute(classMd, extras = myExtras)
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
        parser.findKeyword(finalExtras, endOk = true)
    }

    private fun doLevel(l: String) {
        cli.checkRepeat({ level.isNotEmpty() }, msg = "duplicate level keyword '$l'")
        if (l !in levels) {
            throw CliException("invalid show level '$l'")
        }
        level = l
        parser.findKeyword(finalExtras, endOk = true)
    }

    private fun showOne(obj: ObjectData, header: String? = null): StyledText {
        val display = ColumnLayout(
            columns = Properties.getInt("parameter", "show_columns"),
            separator = "=",
            labelColumnWidth = Properties.getInt("parameter", "label_column_width"),
            valueColumnWidth = Properties.getInt("parameter", "value_column_width"),
            stripeColors = Properties.getColors("even_row", "odd_row")
        )
        val objClass = obj["class"].value
        val objName = obj["name"].value
        val heading = makeHeading(header ?: "${Properties.get("class", objClass)} '${objName}'")
        val sortedValues = obj.attributes
            .filter { entry ->
                Properties.getInt("suppress", classMd.name, entry.value.name) == 0
            }
            .mapKeys { entry -> entry.value.attributeMd.displayName }
        for ((name, attributeData) in sortedValues) {
            display.append(
                name,
                "${
                    cli.makeDisplayName(
                        classMd,
                        attributeData.name,
                        attributeData.displayValue
                    )
                } ${attributeData.attributeMd.unit}"
            )
        }
        return StyledText(heading, display.renderStyled())
    }

    private fun showCollection(oname: ObjectName, coll: CollectionData, header: StyledText?=null): StyledText {
        val table = cli.makeTable()
        var namePosition = -1000000
        val nameColumns = oname.elements.takeLast(oname.wildDepth)
            .map { Pair(it.attrMd.containedClass?.displayName ?: "", ++namePosition) }
            .toMap()
        CliException.throwIf("no matching objects found") { coll.isEmpty() }
        for (obj in coll) {
            val color = obj.getOr("color")?.value?.ifBlank { null }
            for (elem in obj.name.elements.takeLast(oname.wildDepth)) {
                table.append(elem.attrMd.containedClass?.displayName ?: "", elem.name, color)
            }
            for ((name, attributeData) in obj) {
                if (!attributeData.attributeMd.suppressed && name != "name") {
                    table.append(name, attributeData.displayValue, color = color)
                }
            }
        }
        val columnOrder = ColumnOrder[classMd.name]
        table.setColumns { name, col ->
            if (name in nameColumns) {
                col.position = nameColumns[name]!!
            } else {
                val attrMd = classMd.getAttribute(name)
                col.position = columnOrder?.getPosition(attrMd?.name ?: "") ?: 0
                col.heading = cli.abbreviateHeader((attrMd?.displayName ?: makeNameHuman(name)))
            }
        }
        val styled = table.renderStyled()
        return if (header==null) styled else StyledText(header, StyledText(), styled)
    }

    private fun showHealth(): StyledText {
        parser.checkFinished()
        return StyledText(
            makeHeading("System Health Information"),
            StyledText(
                Rest.getAttribute("rest/top", "last_log_entry")
                    ?: "No health information available",
                color = Properties.get("color", "even_row")
            )
        )
    }

    private fun showLicense(): StyledText {
        parser.checkFinished()
        val licenses = Rest.getCollection(
            "rest/top/licenses/",
            mapOf("order" to "<issue_time", "limit" to "1")
        )
        if (licenses.isNotEmpty()) {
            return showOne(licenses.first()!!, header = "License Information")
        } else {
            throw CliException("No license installed")
        }
    }

    private fun showCliMetadata(): StyledText {
        val classKw = parser.findKeyword(
            KeywordList(*CliMetadata.classes.map { it.name }.toTypedArray()),
            endOk = true
        )
        parser.checkFinished()
        if (classKw == null) {
            val layout = ColumnLayout(
                4,
                stripeColors = Properties.getColors("even_row", "odd_row")
            )
            CliMetadata.classes.map { layout.append(it.name) }
            return StyledText(
                makeHeading("Available Classes", includeTime = false),
                layout.renderStyled()
            )
        } else {
            val classMd = CliMetadata[classKw.asString()]!!
            val headings = ColumnLayout(
                1,
                valueColumnWidth = 80,
                stripeColors = Properties.getColors("heading")
            )
            val parent = classMd.parentClass
            if (parent == null) {
                headings.append("Parent", "None")
            } else {
                headings.append(
                    "Parent:",
                    "${parent.name}.${classMd.container?.name ?: ""}"
                )
            }
            if (classMd.derivedClasses.isNotEmpty()) {
                headings.append("Subclasses:",
                    classMd.derivedClasses.joinToString(", ") { it.name })
            }
            headings.append("Base Classes:",
                classMd.baseClasses.joinToString(", ") { it.name })
            headings.append("All Base Classes:",
                classMd.allBaseClasses.joinToString(", ") { it.name })
            val body = Table(
                maxColumnWidth = Properties.getInt("parameter", "metadata_column_width"),
                headingColor = "heading",
                stripeColors = listOf("even_row", "odd_row")
            )
            classMd.ownAttributes.map { attr ->
                body.append(
                    "Name" to attr.displayName,
                    "Rest Name" to attr.name,
                    "Class" to attr.myClass.name,
                    "Level" to attr.level.toString(),
                    "Nature" to attr.nature,
                    "Type" to attr.type.name,
                    "Filter Type" to attr.filterType.name,
                    "Unit" to attr.unit,
                    "Range" to attr.range
                )
            }
            return StyledText(
                makeHeading("CliMetadata for Class '${classMd.displayName}' (${classMd.name})", includeTime = false),
                headings.renderStyled(),
                StyledText("\n"),
                body.renderStyled()
            )
        }
    }

    private fun showParameters(): StyledText {
        val paramClass = CliMetadata.getClass("parameter_info")!!
        val param = parser.findKeyword(
            KeywordList(paramClass.attributes), endOk = true
        )
        parser.checkFinished()
        val raw = Rest.getJson("parameters")
        val params = raw.asDict()["collection"]?.asArray()?.get(0)
        if (param == null) {
            val display = ColumnLayout(
                columns = Properties.getInt("parameter", "parameter_columns"),
                separator = "=",
                labelColumnWidth = Properties.getInt("parameter", "parameter_label_width"),
                valueColumnWidth = Properties.getInt("parameter", "parameter_value_width"),
                stripeColors = Properties.getColors("even_row", "odd_row")
            )
            for ((name, value) in params!!.asDict()) {
                val displayName = paramClass.getAttribute(name)?.displayName ?: makeNameHuman(name)
                display.append(displayName, value.asString())
            }
            result = display.renderStyled()
        } else {
            val name = param.attribute!!.displayName
            val value = params?.asDict()?.get(param.attribute!!.name)?.asString()
            result = StyledText("$name = $value", color = "result")
        }
        return result
    }

    private fun showServers(): StyledText =
        cli.makeTable().also { table ->
            parser.checkFinished()
            for ((name, server) in Server) {
                if (name != Server.lastName) {
                    table.append("Server Name" to name, "Target" to "$server")
                }
            }
        }.also {
            CliException.throwIf("no servers configured"){ it.isEmpty() }
        }.renderStyled()

    private fun showSystem(): StyledText {
        parser.checkFinished()
        return showOne(Rest.getObject("rest/top",
            options=mapOf("level" to "full"))!!,
            header="System Information")
    }

    private fun showVersion(): StyledText {
        parser.checkFinished()
        return StyledText(Rest.getAttribute("configurations/running",
            "build_version") ?: "unknown",
            color = "result")
    }

    private fun showPointers(): StyledText {
        val (on, kw) = parser.getObjectName(initialExtras=KeywordList("url"), wildOk=false)
        var objName = on
        if (kw?.asString()=="url") {
            objName = ObjectName(parser.nextToken(tokenType=Parser.TokenType.ttNonBlank)!!)
        }
        parser.checkFinished()
        classMd = CliMetadata.getClass("pointer_class")!!
        val refCount = Rest.getAttribute(objName,"reference_count")
        CliException.throwIf("Object '${objName.shortUrl}' not found") {refCount==null}
        val coll = Rest.getCollection("pointers/", mapOf("level" to "brief",
            "with" to "pointer=${objName.url}"))
        val header1 = "Reference count for '${objName.leafName}' is $refCount"
        return if (coll.isNotEmpty()) {
            val header = makeHeading("$header1, ${coll.size} ${"reference".makePlural(coll.size)} found")
            showCollection(objName, coll, header=header)
        } else {
            StyledText("$header1, no references found",
                color="heading")
        }
    }


    private fun makeHeading(text: String, includeTime: Boolean=true): StyledText {
        val time = if (includeTime) " at ${getDateTime()}" else ""
        return StyledText(
            "$text$time\n",
            color="heading",
            style="underline")
    }

}
