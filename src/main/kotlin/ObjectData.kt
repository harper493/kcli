class ObjectData(val classMd: ClassMetadata) {
    val attributes: MutableMap<String, AttributeData> = mutableMapOf()
    lateinit var name: ObjectName; private set
    val url get() = name.url

    operator fun get(attributeName: String): AttributeData = attributes[attributeName]
        ?: throw CliException("unknown attribute '$attributeName' for class '${classMd.name}")
    fun getOr(attributeName: String) = attributes[attributeName]
    fun add(attribute: AttributeData) { attributes[attribute.name] = attribute}
    fun getValue(attributeName: String) = attributes[attributeName]?.value
    fun getInt(attributeName: String) = attributes[attributeName]?.toInt()
    fun getFloat(attributeName: String) = attributes[attributeName]?.toFloat()

    fun load(json: JsonObject): ObjectData {
        for ( (name, value) in json.asDict()) {
            val attrMd = classMd.getAttribute(name)
            if (name=="link") {
                this.name = ObjectName(value.asDict()["href"]?.asString() ?: "")
            } else if (attrMd != null) {
                if (value.isString()) {
                    add(AttributeData(attrMd, value.asString()))
                } else if (value.isDict()) {
                    val innerValue = value.asDict()["name"]
                    if (innerValue?.isString() ?: false) {
                        add(AttributeData(attrMd, innerValue!!.asString()))
                    }
                }
            }
        }
        return this
    }

    operator fun iterator() = attributes.iterator()
    fun filter(pred: (AttributeData)->Boolean) = attributes.values.filter(pred)
}
