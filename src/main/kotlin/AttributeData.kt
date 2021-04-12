class AttributeData(val attributeMd: AttributeMetadata,
                    val value: String) {
    val name = attributeMd.name
    val displayName = attributeMd.displayName
    val displayValue = attributeMd.reformat(value)
    override fun toString() = value
    fun toInt(): Int = attributeMd.type.convert(value).toInt()
    fun toFloat(): Double = attributeMd.type.convert(value).toFloat()
}
