data class ClassMetadata(
    val name: String,
    val jsonMetadata: JsonObject
) {
    lateinit var displayName: String
    private val attributeMap: Map<String, AttributeMetadata> =
        (jsonMetadata["metadata"]
            ?.get("collection")
            ?.asArray()
            ?.filter{ it["name"] !=null }
            ?.map{
                val name = it["name"]!!.asString()
                Pair(name,
                    AttributeMetadata(name, this, it))} ?: listOf())
            .toMap()
    val baseClassNames = jsonMetadata["metadata"]?.asDict()?.get("base_classes")
        ?.asArray()
        ?.map{it.asString()} ?: listOf()

    val attributes get() = attributeMap.values
    val collections = attributeMap.values.filter{it.isCollection}.toList()
    var container: AttributeMetadata? = null; private set
    var baseClasses: List<ClassMetadata> = listOf(); private set
    val parentClass: ClassMetadata? get() { return container?.myClass }
    var allBaseClasses: Set<ClassMetadata> = setOf(); private set
    var derivedClasses: MutableSet<ClassMetadata> = mutableSetOf(); private set
    var derivedAttributes: Set<AttributeMetadata> = setOf(); private set
    var settableAttributes: List<AttributeMetadata> = listOf(); private set
    var modifiableAttributes: List<AttributeMetadata> = listOf(); private set

    fun getAttribute(aname: String) = attributeMap[aname]

    fun setContainer(parentMd: AttributeMetadata?) {
        container = parentMd
        for (a in collections.filter{!it.isAlternate}) {
            Metadata.getClass(a.typeName)?.setContainer(a)
        }
    }
    fun completeClassData(): Boolean {
        if (baseClassNames.isNotEmpty() && baseClasses.isEmpty()) {
            baseClasses = baseClassNames.map{ Metadata.getClass(it) }.filterNotNull()
            allBaseClasses = setOf(*baseClasses.toTypedArray())
        }
        val newBases: Set<ClassMetadata> = mutableSetOf(*allBaseClasses.map{it.allBaseClasses}
                .flatten().toTypedArray())
            .append(allBaseClasses)
        val result = newBases.size > allBaseClasses.size || newBases.map{it.addDerived(this)}.any{it}
        allBaseClasses = newBases
        return result
    }
    fun finalizeClassData() {
        if (container!=null) {
            derivedAttributes = setOf(*derivedClasses.chain(setOf(this))
                .map{it.attributes}
                .flatten().toList().toTypedArray())
            settableAttributes = derivedAttributes.filter{it.isSettable}
            modifiableAttributes = derivedAttributes.filter{it.isModifiable}
        }
    }
    private fun addDerived(classMd: ClassMetadata): Boolean = derivedClasses.add(classMd)
}
