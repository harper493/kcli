class ColumnOrder {

    data class Position(
        val where: String = "",
        val which: String = ""
    )

    data class FieldInfo(
        val preference: Int,
        val include: Boolean
    )

    data class FieldData(
        val name: String,
        val preference:Int = 0) {
        var position: Int = 0
    }

    private val fieldMap = mutableMapOf<String,FieldData>()
    var orderedFields = listOf<FieldData>(); private set

    val fields get() = fieldMap.keys
    val usedFields get() = orderedFields.map{ it.name }

    private fun makeField(name: String, preference:Int = 0) =
        fieldMap[name] ?: (FieldData(name, preference).also{ fieldMap[name] = it })

    fun setPreferences(fields: Map<String,FieldInfo>) =
        also {
            orderedFields = fields
                .map { info -> Pair(info, makeField(info.key, info.value.preference)) }
                .filter{ it.first.value.include }
                .map{ it.second }
                .sortedByDescending { it.preference }
                .also { fields -> fields.withIndex()
                    .forEach { it.value.position = it.index } }
        }

    fun setFields(position: Position,
                  fields: Iterable<String>) {
        val newFields = fields.map{ makeField(it) }
        val otherFields = orderedFields.filter{ it.name !in fields }
        orderedFields = when (position.where) {
            "start" -> {
                newFields.append(otherFields)
            }
            "end" -> {
                otherFields.append(newFields)
            }
            "after" -> {
                otherFields.splitAfter { it.name == position.which }.let {
                    listOf(it.first.append(newFields), it.second)
                }.flatten()
            }
            "before" -> {
                otherFields.splitBefore { it.name == position.which }.let {
                    listOf(it.first.append(newFields), it.second)
                }.flatten()
            }
            else -> orderedFields       // should never happen
        }
        orderedFields.withIndex().forEach{ it.value.position = it.index }
    }

    fun removeFields(fields: Iterable<String>) {
        orderedFields = orderedFields.filter{ it.name !in fields }
    }

    fun getPosition(name: String) = fieldMap[name]?.position ?: defaultPosition

    companion object {
        private val columns = mutableMapOf<String,ColumnOrder>()
        private const val defaultPosition = 10000
        val classes get() = columns.keys
        operator fun get(name: String) = columns[name]
        fun create(name: String, info: Map<String,FieldInfo>) {
            ColumnOrder().setPreferences(info).also{ columns[name] = it }
        }
        fun getPosition(className: String, attrName: String) =
            get(className)?.getPosition(attrName)  ?: defaultPosition
    }
}
