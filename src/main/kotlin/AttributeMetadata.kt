import ShowLevel
import Datatype
import ClassMetadata
import Properties

open class AttributeMetadata(
    val name: String,
    val myClass: ClassMetadata,
    val md: JsonObject
) {
    private val natures: MutableMap<String,String?> = mutableMapOf()
    private var type: Datatype
    private var nature: String
    var displayName: String
    val defaultValue: String? get() = natures["default"]
    val unit: String get() = (md["unit"]?.asString() ?: "")
    val filterType: Datatype get() = Datatype[natures["f"] ?: ""] ?: type
    val preference: Int get() = (md["preference"]?.asString() ?: "0").toIntOrNull() ?: 0
    val isCollection: Boolean = md["usage_type"]?.asString()?:"" == "collection"
    val relativeUrl: String = md["relative_url"]?.asString()?:""
    val typeName: String = md["type_name"]?.asString()?:""

    init {
        nature = md["nature"]?.asString() ?: ""
        val nature_split = nature.split("\\w+".toRegex())
        for (n in nature_split) {
            val nn = n.split(":")
            natures[nn[0]] = if (nn.size>1) nn[1] else null
        }
        type = Datatype[md["type_name"]?.asString() ?: ""]
        displayName = Properties.get("attribute", name) ?: name
    }

    fun getContainedClass() = Metadata.getClass(typeName)
    fun getRange() = natures["range"]
    fun getDefault() = natures["default"]
    fun getType() = type
    fun getNature(n: String) = natures[n]
    fun convert(value: String) = type.convert(value)
}