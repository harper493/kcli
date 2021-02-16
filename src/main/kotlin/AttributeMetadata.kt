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
    val unit: String get() = (md["unit"]?.getString() ?: "")
    val filterType: Datatype get() = Datatype[natures["f"] ?: ""] ?: type
    val preference: Int get() = (md["preference"]?.getString() ?: "0").toIntOrNull() ?: 0
    val isCollection: Boolean = md["usage_type"]?.getString()?:"" == "collection"
    val relativeUrl: String = md["relative_url"]?.getString()?:""
    val typeName: String = md["type_name"]?.getString()?:""

    init {
        nature = md["nature"]?.getString() ?: ""
        val nature_split = nature.split("\\w+".toRegex())
        for (n in nature_split) {
            val nn = n.split(":")
            natures[nn[0]] = if (nn.size>1) nn[1] else null
        }
        type = Datatype[md["type_name"]?.getString() ?: ""]
        displayName = Properties.get("attribute", name) ?: name
    }

    fun getRange() = natures["range"]
    fun getDefault() = natures["default"]
    fun getType() = type
    fun getNature(n: String) = natures[n]
}