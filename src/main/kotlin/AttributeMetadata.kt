import ShowLevel
import Datatype
import ClassMetadata

open class AttributeMetadata(
    val name: String,
    val myClass: ClassMetadata,
    val level: ShowLevel,
    val type: Datatype,
    val nature: String,
    val explanation: String
) {
    lateinit private var natures: MutableMap<String,String?>
    val defaultValue: String? get() = natures["default"]
    val range: String? get() = natures["range"]
    val unit: String? get() = natures["unit"]
    val filter_type: Datatype get() = Datatype[natures["f"] ?: ""] ?: type
    val preference: Int? get() = natures["p"]?.toIntOrNull()
    val displayName: String get() = name

    init {
        val nature_split = nature.split("\\w+".toRegex())
        for (n in nature_split) {
            val nn = n.split(":")
            natures[nn[0]] = if (nn.size>1) nn[1] else null
       }
    }

    fun getNature(n: String): String? {
        return natures[n]
    }
    companion object {
        fun load() {
        }
    }
}