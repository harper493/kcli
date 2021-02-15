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
    private var displayName_: String
    val defaultValue: String? get() = natures["default"]
    val unit: String get() = (md["unit"]?.getString() ?: "")
    val filterType: Datatype get() = Datatype[natures["f"] ?: ""] ?: type
    val preference: Int get() = (md["preference"]?.getString() ?: "0").toIntOrNull() ?: 0
    val displayName: String get() = displayName_
    val typeName: String get() = type.name

    init {
        nature = md["nature"]?.getString() ?: ""
        val nature_split = nature.split("\\w+".toRegex())
        for (n in nature_split) {
            val nn = n.split(":")
            natures[nn[0]] = if (nn.size>1) nn[1] else null
        }
        type = Datatype[md["type_name"]?.getString() ?: ""]
        displayName_ = Properties.get("attribute", name) ?: name
    }

    fun getRange() = natures["range"]
    fun getDefault() = natures["default"]
    fun getType() = type
    fun getNature(n: String) = natures[n]
}