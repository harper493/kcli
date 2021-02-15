import ClassMetadata
import AttributeMetadata
import Datatype
import Rest
import JsonObject

class Metadata (
    var rest: Rest,
    do_load: Boolean = true
) {
    private var classes: MutableMap<String, ClassMetadata> = mutableMapOf()

    fun load() {
        val json = rest.get("rest/top/metadata/", mapOf("level" to "full"))
        val classMd = json["metadata"]?.get("collection")
        for (c in classMd?.getArray()!!) {
            val className = c["name"]?.getString()!!
            classes[className] = ClassMetadata(className, c)
        }
    }

    fun getClass(className: String) = classes[className]
    fun getAttribute(className: String, aname: String) = getClass(className)?.getAttribute(aname)

    init {
        if (do_load) {
            load()
        }
    }
    companion object {
        private var md: Metadata? = null
        fun load(rest: Rest) {
            md = Metadata(rest)
        }
        fun getClass(className: String) = md?.getClass(className)
        fun getAttribute(className: String, aname: String) = md?.getAttribute(className, aname)
    }
}