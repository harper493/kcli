data class ClassMetadata(
    val name: String,
    val jsonMetadata: JsonObject
) {
    lateinit var displayName: String
    private val attributeMap: Map<String, AttributeMetadata> =
        (jsonMetadata["metadata"]
        ?.get("collection")
        ?.asArray()
        ?.filter{ it.get("name") !=null }
        ?.map{Pair(
            it["name"]!!.asString(),
            AttributeMetadata(it["name"]!!.asString(), this, it))} ?: listOf())
        .toMap()
    val attributes get() = attributeMap.values
    val collections = attributeMap.values.filter{it.isCollection}.toList()
    var container: AttributeMetadata? = null
                            private set
    val parentClass: ClassMetadata? get() { return container?.myClass }

    fun getAttribute(aname: String) = attributeMap[aname]
}
