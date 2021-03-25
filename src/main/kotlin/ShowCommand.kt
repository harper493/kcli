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
    private var result = StyledText("")
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
        val (oname, terminator) = this.parser.getObjectName(initialExtras=initialExtras,
            finalExtras=finalExtras,
        initialPred={ !it.name.startsWith("parameter")})
        classMd = oname.leafClass ?: Metadata.getClass("configuration")!!
        var myKey: Keyword? = terminator
        while (myKey != null) {
            myKey.function!!.invoke()
            if (result.isNotEmpty()) {
                println(result.render())
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
            cli.outputln(showCollection(coll))
        } else {
            cli.outputln(showOne(coll.first()!!))
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

    private fun showHealth(): StyledText {
        return StyledText("")
    }

    private fun showLicense(): StyledText {
        return StyledText("")
    }

    private fun showMetadata(): StyledText {
        return StyledText("")
    }

    private fun showParameters(): StyledText {
        val paramClass = Metadata.getClass("parameter_info")!!
        val param = parser.findKeyword(
            KeywordList(paramClass.attributes), endOk=true)
        val raw = Rest.getRaw("parameters")
        val params = raw.asDict()["collection"]?.asArray()?.get(0)
        if (param==null) {
            val display = ColumnLayout(
                columns = Properties.getInt("parameter", "parameter_columns"),
                separator = "=",
                labelColumnWidth = Properties.getInt("parameter", "parameter_label_width"),
                valueColumnWidth = Properties.getInt("parameter", "parameter_value_width"),
                stripeColors = listOfNotNull(Properties.get("color", "even_row"), Properties.get("color", "odd_row"))
            )
            for((name, value) in params!!.asDict()) {
                val displayName = paramClass.getAttribute(name)?.displayName ?:
                                    makeNameHuman(name)
                display.append(displayName, value.asString())
            }
            result = display.layoutText().renderStyled()
        } else {
            val name = param.attribute!!.displayName
            val value = params?.asDict()?.get(name)?.asString()
            result = StyledText("$name = $value", color=Properties.get("color", "even_row"))
        }
        return result
    }

    private fun showSystem(): StyledText {
        return StyledText()
    }

    private fun showVersion(): StyledText {
        return StyledText(Rest.getObject("configurations/running",
            options=mapOf("select" to "build_version"))
            ?.get("build_version")?.value ?: "unknown",
            color = Properties.get("color", "even_row"))
    }
}

/*
#
# show_system - create an object corresponding to the top level rest item,
# and show its attributes
#
    def show_system(self, reader) :
        obj = self.api.metadata.get_object(object_name(), name_list=['top'],
                                          options={"level":"full"})
        self.show_one(obj)
#
# show_parameters - implement show parameters {parameter}
#

    def show_parameters(self, reader) :
        params = self.api.rest_get_object(url='parameters')
        names, values = [], []
        if reader.is_finished() :
            for p in sorted(params.keys()) :
                names += [p]
                values += [params[p]]
            names = ['name'] + names
            values = ['value'] + values
            print format_columns([names, values])
        else :
            commands = { str(p) : str(p) for p in params.keys() }
            param = reader.next_keyword(commands)
            print '%s = %s' % (param, params[param])

#
# show_health - show the health report
#

    def show_health(self, reader) :
        health = self.api.rest_get_object(url='rest/top',
                                          options={"select":"last_log_entry"})[u'last_log_entry']
        print health

#
# show_version - show the version string
#

    def show_version(self, reader) :
        version = self.api.get_attribute_value(object_name('configurations/running'), 'build_version')
        print version
#
# show_license - show the currently installed license, if there is one
#

    def show_license(self, reader) :
        licenses = self.api.rest_get_collection(url='rest/top/licenses/',
                                                options={'order':'<issue_time', 'limit':'1'})
        if licenses :
            license = self.api.get_object('rest/top/licenses/' + licenses[0][u'name'],
                                          options={'level':'full'})
            self.show_one(license)
        else :
            print "No license installed"

#
# show_metadata - show the metadata for a class
#
    def show_metadata(self, reader) :
        class_name = reader.next_keyword({ c:c for c in self.api.metadata.get_classes()}, missOK=True, endOK=True) or \
            reader.get_curtoken()
        if class_name :
            try :
                the_class = self.api.get_class_metadata(class_name)
            except NameError :
                raise SemanticException, "no such class '%s'" % (class_name,)
            print format_columns([["Metadata for class '%s'" % (class_name,)]], underline=True)
            parent, parent_coll = the_class.parent_class, the_class.parent_collection
            if parent is None :
                for b in the_class.all_base_classes :
                    bc = self.api.get_class_metadata(b)
                    if bc.parent_class :
                        parent,parent_coll = bc.parent_class, bc.parent_collection
                        break
            if parent :
                print "Parent: %s.%s" % (parent.class_name, parent_coll.name)
            else :
                print "Parent: <None>"
            if the_class.subclasses :
                print "Subclasses:", ', '.join([ "'%s'" % (str(c),) for c in the_class.subclasses])
            print "Base Classes:", ', '.join([ "'%s'" % (str(c),) for c in the_class.base_classes])
            print "All Base Classes:", ', '.join([ "'%s'" % (str(c),) for c in the_class.all_base_classes])
            if the_class.parent_class :
                print "Container: %s.%s" % (str(the_class.parent_class), str(the_class.parent_collection))
            rows = [['name', 'kind', 'level', 'nature', 'type', 'filter_type', 'unit', 'range']]
            values = {}
            for a in the_class.attributes.itervalues() :
                a_or_c = 'coll' if a.is_collection() else 'attr'
                values[a.name] = [a.name, a_or_c, a.level, ','.join(a.nature), \
                                  a.type.type_name if a.type else a.type_name, \
                                  a.filter_type or '', a.unit or '', \
                                  a.range or '']
            rows += [values[k] for k in sorted(values.keys())]
            cols = [[e for e in r] for r in zip(*rows)]
            print format_columns(cols, underline=True)
        else :
            print format_columns([["Known Object Classes"]], underline=True)
            print column_layout(sorted(self.api.metadata.get_classes()), columns=4)

 */