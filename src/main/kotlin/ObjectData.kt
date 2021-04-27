class ObjectData(val classMd: ClassMetadata) {
    val attributes: MutableMap<String, AttributeData> = mutableMapOf()
    lateinit var name: ObjectName; private set
    var hasHistory = false; private set
    val historyValues = mutableListOf<AttributeData.HistoryReader>()
    val url get() = name.url

    operator fun get(attributeName: String): AttributeData = attributes[attributeName]
        ?: throw CliException("unknown attribute '$attributeName' for class '${classMd.name}")
    fun getOr(attributeName: String) = attributes[attributeName]
    fun add(attribute: AttributeData) { attributes[attribute.name] = attribute}
    fun getValue(attributeName: String) = attributes[attributeName]?.value
    fun getInt(attributeName: String) = attributes[attributeName]?.toInt()
    fun getFloat(attributeName: String) = attributes[attributeName]?.toFloat()
    operator fun iterator() = attributes.iterator()
    fun filter(pred: (AttributeData)->Boolean) = attributes.values.filter(pred)

    fun load(json: JsonObject): ObjectData {
        val histories = mutableMapOf<String, JsonObject>()
        for ( (name, value) in json.asDict()) {
            val attrMd = classMd.getAttribute(name)
            if (name=="link") {
                this.name = ObjectName(value.asDict()["href"]?.asString() ?: "")
            } else if (attrMd != null) {
                if (value.isString()) {
                    add(AttributeData(attrMd, value.asString()))
                } else if (value.isDict()) {
                    val innerValue = value.asDict()["name"]
                    if (innerValue?.isString() == true) {
                        add(AttributeData(attrMd, innerValue.asString()))
                    }
                }
            } else if (name.startsWith("_history_")) {
                histories[name.removePrefix("_history_")] = value
                hasHistory = true
            }
        }
        histories
            .map{ attributes[it.key] to it.value }
            .filter{ it.first!=null }
            .forEach { (attr, value) ->
                attr!!.loadHistory(value)
                historyValues.add(attr.historyReader())
            }
        return this
    }

    class HistoryValue(val obj: ObjectData,
                       val requestedTime: Long) {
        var attributes: Map<String, AttributeData>? = null; private set
        var actualTime: Long = 0L; private set
        val isValid get() = attributes!=null

        init {
            var rawValues = obj.historyValues.map { it.getAt(requestedTime) }.filterNotNull()
            if (rawValues.isEmpty()) {
                obj.historyValues.firstOrNull()?.nextTime()?.let { goodTime ->
                    rawValues = obj.historyValues
                        .map { it.getAt(goodTime) }
                        .filterNotNull()
                }
            }
            if (rawValues.isNotEmpty()) {
                actualTime = rawValues.firstOrNull()?.time ?: 0
                attributes = rawValues.map {
                    it.attributeMd.name to
                            AttributeData(
                                it.attributeMd,
                                it.attributeMd.reformat(it.value.toString())
                            )
                }.toMap()
            }
        }
    }

    fun getHistoryValue(requestedTime: Long) = HistoryValue(this, requestedTime)

}
