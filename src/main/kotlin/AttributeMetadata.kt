open class AttributeMetadata(
    val name: String,
    val myClass: ClassMetadata,
    private val md: JsonObject
) {
    val type by lazy { Datatype.makeType(md["type_name"]?.asString() ?: "") }
    private val natures = (md["nature"]?.asArray()?.toList() ?: listOf())
        .map {
            val nn = it.asString().split(":")
            Pair(nn[0], nn.getOrNull(1))
        }.filter{it.first.isNotEmpty()}.toMap()

    val nature get() = md["nature"]?.asArray()?.joinToString(" ") { it.asString() } ?: ""
    var displayName: String = (Properties.get("attribute", name) ?: makeNameHuman(name))
        .replace("&.*?;".toRegex(), "")
    var isSettable = "req" in natures || "mod" in natures || "set" in natures
    var isRequired = "req" in natures
    var isModifiable = "mod" in natures
    var isAlternate = "alternate" in natures
    val defaultValue: String? get() = natures["default"]
    val unit: String get() = getMd("unit")
    val filterType: Datatype get() = Datatype[natures["f"] ?: ""]
    val preference: Int get() = getMd("preference").toIntOrNull() ?: 0
    val isCollection: Boolean = getMd("usage_type") == "collection"
    val isRelation: Boolean = getMd("usage_type") == "related"
    val isPseudonym: Boolean = "pseudonym" in natures
    val isEnum: Boolean get() = typeName=="enum"
    val isBrief get() = level=="brief" || level=="list"
    val isNoShow get() = "noshow" in natures
    val relativeUrl: String = getMd("relative_url")
    val typeName: String = getMd("type_name")
    val containedClass get() = CliMetadata.getClass(typeName)
    val range get() = getMd("range")
    val level get() = getMd("level")
    val default get() = natures["default"]
    val total get() = natures["total"] ?: "none"
    var reformatter: ((String)->String)? = null; private set
    fun getMd(mname: String) = md[mname]?.asString() ?: ""
    fun getNature(n: String) = natures[n]
    fun convert(value: String) = type.convert(value)
    fun completer() = if (isEnum) EnumCompleter(this) else CliCompleter()
    fun setReformatter(fn: (String)->String) { reformatter = fn }
    fun reformat(value: String): String =
        if (reformatter==null) type.reformat(value) else reformatter!!(value)
}