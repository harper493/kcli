class ColumnsCommand(val cli: CliCommand) {

    private val parser get() = cli.parser
    private lateinit var position: ColumnOrder.Position
    private lateinit var className: String

    fun doColumns() {
        val k = parser.findKeyword(
            KeywordList(*ColumnOrder.classes.toTypedArray())
                .add(KeywordFn("save",   { doSave() }))
        )!!
        if (k.function != null) {
            k()
        } else {
            className = k.asString()
            val keywords = KeywordList(
                KeywordFn("add", { doAdd(endOk = true) }),
                KeywordFn("remove", { doRemove() }),
                KeywordFn("move", { doAdd(endOk = false) }),
            )
            parser.findKeyword(keywords)!!.invoke()
        }
    }

    fun getRemainder(
        className: String,
        getPosition: Boolean = true,
        endOk: Boolean=false
    ): Pair<List<String>, ColumnOrder.Position> {
        position = ColumnOrder.Position(where="end")
        val positions = KeywordList(
            KeywordFn("start", { position = ColumnOrder.Position(where = "start") }),
            KeywordFn("end",   { position = ColumnOrder.Position(where = "end") }),
            KeywordFn("before",{ position = getPosition(where = "before", endOk=endOk) }),
            KeywordFn("after", { position = getPosition(where = "after", endOk=endOk) }),
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

    fun getPosition(where: String, endOk: Boolean) =
        ColumnOrder.Position(where,
            parser.findKeyword(KeywordList(*ColumnOrder[className]!!.fields.toTypedArray()),
            endOk=endOk)!!.asString())

    fun doAdd(endOk: Boolean) {
        val (attributes, position) = getRemainder(className, getPosition=true, endOk=endOk)
        ColumnOrder[className]?.setFields(position, attributes)
    }

    fun doRemove() {
        val (attributes, _) = getRemainder(className, getPosition=false)
        ColumnOrder[className]?.removeFields(attributes)
    }

    fun doSave() {

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

