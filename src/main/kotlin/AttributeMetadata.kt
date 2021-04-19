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
    val isBrief get() = level <= ShowLevel.brief
    val isNoShow get() = "noshow" in natures
    val isHistory get() = "history" in natures
    val relativeUrl: String = getMd("relative_url")
    val typeName: String = getMd("type_name")
    val containedClass get() = CliMetadata.getClass(typeName)
    val range get() = getMd("range")
    val suppressed by lazy { Properties.get("suppress", myClass.name, name)!=null }
    val level by lazy {
        ShowLevel.values().find{ it.name==getMd("level") } ?: ShowLevel.detail
    }
    val default get() = natures["default"]
    val total get() = natures["total"] ?: "none"
    val reformatter: ((AttributeMetadata, String)->String)? by lazy {
        makeReformatter(Properties.get("formatter", this.myClass.name, name))
    }
    val myHelpClass by lazy { myClass.containedClass ?: myClass }
    fun getMd(mname: String) = md[mname]?.asString() ?: ""
    fun getNature(n: String) = natures[n]
    fun convert(value: String) = type.convert(value)
    fun completer() =
        if (isEnum) EnumCompleter(this)
        else type.completer.clone().addHelp(if (range.isNullOrBlank()) "" else " in range $range")
    fun reformat(value: String): String =
        if (reformatter==null) type.reformat(value) else reformatter!!(this, value)
    fun getHelp(): String? =
        Properties.get("help", myHelpClass.name, name)
                ?: Properties.get("tip", myHelpClass.name, name)

    companion object {
        fun makeReformatter(what: String?): ((AttributeMetadata, String)->String)? =
            when (what) {
                "display_class" -> { _, value -> displayClass(value) }
                "display_attribute" -> { _, value -> displayAttribute(value) }
                "truncate_url" -> { _, value -> truncateUrl(value) }
                else -> null
            }
        fun displayClass(name: String?) =
            (name?.let{ CliMetadata.getClass(name) }?.displayName) ?: name ?: ""
        fun displayAttribute(name: String) =
            Properties.get("attribute", name) ?: name
        fun truncateUrl(url: String) =
            ObjectName(url).shortUrl

    }
}