class AttributeData(val attributeMd: AttributeMetadata,
                    val value: String) {
    val name = attributeMd.name
    override fun toString() = value
    fun toInt(): Int = attributeMd.type.convert(value).toInt()
    fun toFloat(): Double = attributeMd.type.convert(value).toFloat()
}
