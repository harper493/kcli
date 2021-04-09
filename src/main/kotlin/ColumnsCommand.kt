class ColumnsCommand(val cli: CliCommand) {

    private val parser get() = cli.parser
    private lateinit var position: ColumnOrder.Position
    private lateinit var className: String
    private lateinit var classMd: ClassMetadata

    fun doColumns() {
        val k = getClass(extras = KeywordList(KeywordFn("save") { doSave() }))
        if (k.function != null) {
            k()
        } else {
            val keywords = KeywordList(
                KeywordFn("add") { doAdd(endOk = true) },
                KeywordFn("remove") { doRemove() },
                KeywordFn("move") { doAdd(endOk = false) },
                KeywordFn("show") { cli.outputln(makeShow(needClass = false)) }
            )
            parser.findKeyword(keywords)!!.invoke()
        }
    }

    fun makeShow(needClass: Boolean): StyledText {
        if (needClass) getClass()
        val heading = StyledText("Columns for class '${CliMetadata.getClass(className)!!.displayName}'" +
                " at ${getDateTime()}\n",
            color=Properties.getParameter("heading_color"),
            style="underline")
        val table = Table()
        ColumnOrder[className]!!.usedFields
            .forEach{
                table.append("Name" to (classMd.getAttribute(it)?.displayName ?: ""),
                             "Rest Name" to it) }
        return StyledText(heading, table.layoutText().renderStyled())
    }

    fun getClass(extras: KeywordList = KeywordList()) =
        parser.findKeyword(
            KeywordList(*ColumnOrder.classes.toTypedArray())
                .add(extras)
        )!!.also {
            className = it.asString()
            classMd = CliMetadata.getClass(className)!!}

    private fun getRemainder(
        className: String,
        getPosition: Boolean = true,
        endOk: Boolean=false
    ): Pair<List<String>, ColumnOrder.Position> {
        position = ColumnOrder.Position(where="end")
        val positions = KeywordList(
            KeywordFn("start") { position = ColumnOrder.Position(where = "start") },
            KeywordFn("end") { position = ColumnOrder.Position(where = "end") },
            KeywordFn("before") { position = getPosition(where = "before", endOk = endOk) },
            KeywordFn("after") { position = getPosition(where = "after", endOk = endOk) },
        )
        val keywords = KeywordList(*ColumnOrder[className]!!.fields.toTypedArray())
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
        ColumnOrder.Position(where,
            parser.findKeyword(KeywordList(*ColumnOrder[className]!!.fields.toTypedArray()),
            endOk=endOk)!!.asString())

    private fun doAdd(endOk: Boolean) {
        val (attributes, position) = getRemainder(className, getPosition=true, endOk=endOk)
        ColumnOrder[className]?.setFields(position, attributes)
    }

    private fun doRemove() {
        val (attributes, _) = getRemainder(className, getPosition=false)
        ColumnOrder[className]?.removeFields(attributes)
    }

    private fun doSave() {

    }

    companion object {
        fun initialize() {
            CliMetadata.classes
                .filter { it.container != null }
                .forEach { classMd ->
                    ColumnOrder.create(classMd.name,
                        classMd.attributes
                            .map { attr ->
                                attr.name to
                                        ColumnOrder.FieldInfo(attr.preference,
                                            attr.isBrief) }
                            .toMap()
                    )
                }

        }
    }
}

