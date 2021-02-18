data class ClassMetadata(
    val name: String,
    val jsonMetadata: JsonObject
) {
    lateinit var displayName: String
    private val attributeMap: Map<String, AttributeMetadata> =
        (jsonMetadata["metadata"]
        ?.get("collection")
        ?.asArray()
        ?.filter{it?.get("name")!=null}
        ?.map{Pair(it["name"]!!.asString()!!,
            AttributeMetadata(it["name"]!!.asString(), this, it!!))} ?: listOf())
        .toMap()
    val attributes get() = attributeMap.values
    val collections: MutableList<AttributeMetadata> =
        attributeMap.values.filter{it.isCollection}.toMutableList()
    var container: AttributeMetadata? = null
                            private set
    //val container: AttributeMetadata get() { return _container!! }
    val parentClass: ClassMetadata? get() { return container?.myClass }

    fun getAttribute(aname: String) = attributeMap[aname]
    //fun getCollections(): List<AttributeMetadata> = collections

    init {
   }

}
