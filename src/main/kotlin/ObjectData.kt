class ObjectData(val classMd: ClassMetadata) {
    val attributes: MutableMap<String, AttributeData> = mutableMapOf()

    operator fun get(attributeName: String): AttributeData = attributes[attributeName]
        ?: throw CliException("unknown attribute '$attributeName' for class '${classMd.name}")
    fun add(attribute: AttributeData) { attributes[attribute.name] = attribute}

    fun load(json: JsonObject): ObjectData {
        for ( (name, value) in json.asDict()) {
            val attrMd = classMd.getAttribute(name)
            if (attrMd != null) {
                if (value.isString()) {
                    add(AttributeData(attrMd, value.asString()))
                } else if (value.isDict()) {
                    val innerValue = value.asDict().get("name")
                    if (innerValue?.isString() ?: false) {
                        add(AttributeData(attrMd, innerValue!!.asString()))
                    }
                }
            }
        }
        return this
    }
}