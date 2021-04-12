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
    )

    fun doShow() {
        getShowInput()
        if (result.isNotEmpty()) {
            println(result.render())
            return
        }
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

    fun doCount() {
        getShowInput(exclude = listOf("select", "top", "bottom", "limit", "level"))
        optionsMap["limit"] = "1"
        optionsMap["level"] = "list"
        makeOptions()
        val (envelope, _) = Rest.get(objectName, options = optionsMap)
        val quantity = envelope["size"]?.toInt() ?: 0
        val result = StyledText(
            "$quantity matching ${classMd.name.makePlural(quantity)} found",
            color = Properties.getParameter("result_color")
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
        cli.outputln(table.layoutText().render())
    }

    private fun getShowInput(exclude: Iterable<String> = listOf()) {
        optionsMap["link"] = "name"
        finalExtraTemplate.keywords
            .forEach { if (it.second.key !in exclude) finalExtras.add(it.second) }
        if ("levels" !in exclude) {
            levels.map { finalExtras.addOne(Keyword(it, function = { doLevel(it) })) }
        }
        val (oname, terminator) = parser.getObjectName(initialExtras = initialExtras,
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
        if (doColor) {
            classMd.getAttribute("color")?.let { selections.add(it) }
        }
        (ColumnOrder.get(classMd.name)?.usedFields ?: listOf())
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
        return StyledText(heading, display.layoutText().renderStyled())
    }

    private fun showCollection(oname: ObjectName, coll: CollectionData): StyledText {
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
                if (Properties.get("suppress", classMd.name, name) == null && name != "name") {
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
        return table.layoutText().renderStyled()
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
                layout.layoutText().renderStyled()
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
                headingColor = Properties.getParameter("heading_color"),
                stripeColors = Properties.getColors("even_row", "odd_row")
            )
            classMd.ownAttributes.map { attr ->
                body.append(
                    "Name" to attr.displayName,
                    "Rest Name" to attr.name,
                    "Level" to attr.level,
                    "Nature" to attr.nature,
                    "Type" to attr.type.name,
                    "Filter Type" to attr.filterType.name,
                    "Unit" to attr.unit,
                    "Range" to attr.range
                )
            }
            return StyledText(
                makeHeading("CliMetadata for Class '${classMd.displayName}' (${classMd.name})", includeTime = false),
                headings.layoutText().renderStyled(),
                StyledText("\n"),
                body.layoutText().renderStyled()
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
            result = display.layoutText().renderStyled()
        } else {
            val name = param.attribute!!.displayName
            val value = params?.asDict()?.get(param.attribute!!.name)?.asString()
            result = StyledText("$name = $value", color = Properties.getParameter("result_color"))
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
        }.layoutText().renderStyled()

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
            color = Properties.getParameter("result_color"))
    }

    private fun showPointers(): StyledText {
        val (on, kw) = parser.getObjectName(initialExtras=KeywordList("url"))
        var objName = on
        if (kw?.asString()=="url") {
            objName = ObjectName(parser.nextToken(tokenType=Parser.TokenType.ttNonBlank)!!)
        }
        parser.checkFinished()
        classMd = CliMetadata.getClass("pointer_class")!!
        val refCount = Rest.getAttribute(objName,"reference_count")
        CliException.throwIf("Object '${objName.url}' not found") {refCount==null}
        val coll = Rest.getCollection("pointers/", mapOf("level" to "brief",
            "with" to "pointer=${objName.url}"))
        val header1 = "Reference count for '${objName.leafName}' is $refCount"
        if (coll.isNotEmpty()) {
            return showCollection(objName, coll)
            /*
            val table = cli.makeTable()
            coll.objects.forEach{ (_, data) ->
                data.attributes.values.map{ attr ->
                    Pair(attr.name,
                        when (attr.name) {
                            "pointer_object_class" ->
                                (CliMetadata.getClass(attr.value)?.displayName ?: attr.value)
                            "attribute_name" ->
                                Properties.get("attribute", attr.value) ?: attr.value
                            "pointer_url" ->
                                ObjectName(attr.value).shortUrl
                            else ->
                                attr.value
                        })}.let{ (name, value) -> table.append(name, value) }


            }
            val pointerMd = CliMetadata.getClass("pointer_class")!!
            table.setHeaders{ pointerMd.getAttribute(it)?.displayName ?: it }
            val header = StyledText("$header1, ${coll.size} ${"reference".makePlural()} found",
                        color=Properties.getParameter("heading_color"))
            return StyledText(header, table.layoutText().renderStyled())

             */
        } else {
            return StyledText("$header1, no references found",
                color=Properties.getParameter("heading_color"))
        }
    }

    /*
        def show_pointers(self, reader) :

        def format_object(n, v) :
            if n=='pointer_object_class' :
                return self.api.get_class_metadata(v).display_name
            elif n=='attribute_name' :
                return self.api.translate_attribute(v)
            elif n=='pointer_url' :
                m = re.match(r'^.* /configurations/.*?/(.*)', v)
    return m.group(1)
             else :
                return v

        def format_collection(n, v) :
            v.update_values(format_object)

        def do_url() :          # dummy function used to recognise URL keyword
            pass

        obj_name = self._get_object_name(reader, leaf_name_required=True, base_extra={'url' : do_url})
        if obj_name==do_url :
            url = reader.take_all()
            obj_name = object_name(url)
        rc = self.api.get_attribute_metadata(obj_name.get_class_names(1)[0], 'reference_count')
        target = self.api.get_object(obj_name, attributes=[rc])
        if target :
            refcount = int(target['reference_count'])
            pointers = object_name('pointers/')
            opts = { 'with' : 'pointer=' + obj_name.url(),
                     'level' : 'brief' }
            coll = self.api.get_collection(pointers, options=opts)
            if coll :
                coll.visit_values(format_collection)
                title = "Reference count for %s is %d, %d %s found" \
                        % (obj_name.leaf_string(), refcount, len(coll), make_plural("reference", len(coll)))
                output(title, color='magenta')
                self.size_limit = None
                self.show_collection(coll, max_width=None, show_heading=False, show_names=False, reload=False,
                                     right_align=[False, True])
            elif refcount :
                error_output("Reference count for %s is %d but no references found" \
                             % (objname.leaf_string(), refcount))
            else :
                output("No references found for %s'" \
                       % (objname.leaf_string()))
        else :
            self._not_found_error(obj_name)



    */
    private fun makeHeading(text: String, includeTime: Boolean=true): StyledText {
        val time = if (includeTime) " at ${getDateTime()}" else ""
        return StyledText(
            "$text$time\n",
            color=Properties.getParameter("heading_color"),
            style="underline")
    }

}
