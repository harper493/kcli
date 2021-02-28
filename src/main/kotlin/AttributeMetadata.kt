open class AttributeMetadata(
    val name: String,
    val myClass: ClassMetadata,
    private val md: JsonObject
) {
    val type by lazy( {Datatype.makeType(md["type_name"]?.asString() ?: "")} )
    private val nature = md["nature"]?.asArray()?.toList() ?: listOf()
    private val natures = nature.map {
                val nn = it.asString()?.split(":")
                Pair(nn[0], nn.getOrNull(1))
            }.filter{it.first.isNotEmpty()}.toMap()

    var displayName: String = (Properties.get("attribute", name) ?: makeNameHuman(name))
        .replace("&.*?;".toRegex(), "")
    var isSettable = "req" in natures || "mod" in natures || "set" in natures
    var isRequired = "req" in natures
    var isModifiable = "mod" in natures
    var isAlternate = "alternate" in natures
    val defaultValue: String? get() = natures["default"]
    val unit: String get() = (md["unit"]?.asString() ?: "")
    val filterType: Datatype get() = Datatype[natures["f"] ?: ""]
    val preference: Int get() = (md["preference"]?.asString() ?: "0").toIntOrNull() ?: 0
    val isCollection: Boolean = md["usage_type"]?.asString()?:"" == "collection"
    val isRelation: Boolean = md["usage_type"]?.asString()?:"" == "related"
    val relativeUrl: String = md["relative_url"]?.asString()?:""
    val typeName: String = md["type_name"]?.asString()?:""

    val containedClass get() = Metadata.getClass(typeName)
    val range get() = natures["range"]
    val default get() = natures["default"]
    fun getNature(n: String) = natures[n]
    fun convert(value: String) = type.convert(value)
}