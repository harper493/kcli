class AttributeData(val attributeMd: AttributeMetadata,
                    val value: String) {
    val name = attributeMd.name
    override fun toString() = value
}
