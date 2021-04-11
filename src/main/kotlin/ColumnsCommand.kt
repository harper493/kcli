class ColumnsCommand(val cli: CliCommand) {

    private val parser get() = cli.parser
    private lateinit var position: ColumnOrder.Position
    private lateinit var className: String
    private lateinit var classMd: ClassMetadata

    fun doColumns() {
        val k = getClass(
            extras = KeywordList(
                KeywordFn("save") { save() },
                KeywordFn("restore") { restore() })
        )
        if (k.function != null) {
            k()
        } else {
            val keywords = KeywordList(
                KeywordFn("add") { doAdd(endOk = true) },
                KeywordFn("move") { doAdd(endOk = false) },
                KeywordFn("remove") { doRemove() },
                KeywordFn("show") { cli.outputln(makeShow(needClass = false)) }
            )
            parser.findKeyword(keywords)!!.invoke()
        }
    }

    fun makeShow(needClass: Boolean): StyledText {
        if (needClass) getClass()
        val heading = StyledText(
            "Columns for class '${CliMetadata.getClass(className)!!.displayName}'" +
                    " at ${getDateTime()}\n",
            color = Properties.getParameter("heading_color"),
            style = "underline"
        )
        val table = Table()
        ColumnOrder[className]!!.usedFields
            .forEach {
                table.append(
                    "Name" to (classMd.getAttribute(it)?.displayName ?: ""),
                    "Rest Name" to it
                )
            }
        return StyledText(heading, table.layoutText().renderStyled())
    }

    fun getClass(extras: KeywordList = KeywordList()) =
        parser.findKeyword(
            KeywordList(*ColumnOrder.classes.toTypedArray())
                .add(extras)
        )!!.also {
            if (it.asString().isNotEmpty()) {
                className = it.asString()
                classMd = CliMetadata.getClass(className)!!
            }
        }

    private fun getRemainder(
        className: String,
        getPosition: Boolean = true,
        onlyExisting: Boolean = false,
        endOk: Boolean = false
    ): Pair<List<String>, ColumnOrder.Position> {
        position = ColumnOrder.Position(where = "end")
        val positions = KeywordList(
            KeywordFn("start") { position = ColumnOrder.Position(where = "start") },
            KeywordFn("end") { position = ColumnOrder.Position(where = "end") },
            KeywordFn("before") { position = getPosition(where = "before", endOk = endOk) },
            KeywordFn("after") { position = getPosition(where = "after", endOk = endOk) },
        )
        val keywords = KeywordList(*ColumnOrder[className]!!.let{
            if (onlyExisting) it.usedFields else it.fields }.toTypedArray())
        if (getPosition) {
            keywords.add(positions)
        }
        val attributes = mutableListOf<String>()
        while (true) {
            val k = parser.findKeyword(keywords, endOk = attributes.isNotEmpty())
            if (k == null) {
                break
            } else if (k.asString().isNotEmpty()) {
                attributes.add(k.asString())
            } else {               // must be a function
                k()
                break
            }
        }
        parser.checkFinished()
        return Pair(attributes, position)
    }

    private fun getPosition(where: String, endOk: Boolean) =
        ColumnOrder.Position(
            where,
            parser.findKeyword(
                KeywordList(*ColumnOrder[className]!!.fields.toTypedArray()),
                endOk = endOk
            )!!.asString()
        )

    private fun doAdd(endOk: Boolean) {
        val (attributes, position) = getRemainder(className, getPosition = true, endOk = endOk)
        ColumnOrder[className]?.setFields(position, attributes)
    }

    private fun doRemove() {
        val (attributes, _) = getRemainder(className, getPosition = false, onlyExisting=true)
        ColumnOrder[className]?.removeFields(attributes)
    }

    private fun save() {
        val keywords = KeywordList("shared")
        if (!Args.remote) {
            keywords.addKeys("local")
        }
        val where = parser.findKeyword((keywords), endOk = true)?.asString()
            ?: if (Args.remote) "shared" else "local"
        if (where == "local") {
            Properties("$ColumnOrder").write(filename)
        } else {
            Rest.put(preferenceUrl, mapOf("preferences" to "$ColumnOrder"))
        }
    }

    private fun restore() {
        val keywords = KeywordList(
            KeywordFn("shared") { restoreShared() },
            KeywordFn("default") { restoreDefault() })
        if (!Args.remote) {
            keywords.add(KeywordFn("local") { restoreLocal() })
        }
        parser.findKeyword((keywords), endOk = true).let { fn ->
            when (fn) {
                null -> if (Args.remote) restoreShared() else restoreLocal()
                else -> fn.invoke()
            }
        }
    }

    companion object {
        private val filename = "${Cli.kcliDir}/columns"
        private val preferenceUrl = "administrators/${Cli.username}/client_preferences/_cli_columns"

        fun initialize() {
            restoreDefault()
            try {
                restoreShared()
            } catch (exc: RestException) {
            }
            if (!Args.remote) restoreLocal()
        }

        private fun restoreDefault() {
            CliMetadata.classes
                .filter { it.container != null }
                .forEach { classMd ->
                    ColumnOrder.create(classMd.name,
                        classMd.attributes
                            .map { attr ->
                                attr.name to
                                        ColumnOrder.FieldInfo(
                                            attr.preference,
                                            attr.isBrief && !attr.isNoShow
                                        )
                            }
                            .toMap()
                    )
                }
        }

        private fun restoreLocal() {
            restore(readFileOrEmpty(filename))
        }

        private fun restoreShared() {
            restore((Rest.getAttribute(preferenceUrl, "preferences") ?: "")
                .split(";")
                .joinToString("\n"))
        }

        private fun restore(props: String) {
            Properties(props)
                .forEach { (key, value) ->
                    ColumnOrder.update(key,
                        value.split(",")
                            .mapNotNull{ aname ->
                                CliMetadata.getAttribute(key, aname)?.name }
                            .joinToString( "," )
                    )
                }

        }
    }
}

