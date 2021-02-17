import AttributeMetadata
import Datatype
import JsonObject

data class ClassMetadata(
    val name: String,
    val jsonMetadata: JsonObject
) {
    lateinit var displayName: String
    private var attributes: MutableMap<String, AttributeMetadata> = mutableMapOf()
    private var collections: MutableList<AttributeMetadata> = mutableListOf()
    private var container_: AttributeMetadata? = null
    val container: AttributeMetadata get() { return container_!! }
    val parentClass: ClassMetadata get() { return container.myClass }

    fun getAttribute(aname: String) = attributes[aname]
    fun getCollections(): List<AttributeMetadata> = collections

    init {
        val attrMd = jsonMetadata["metadata"]?.get("collection")
        if (attrMd != null) {
            for (a in attrMd.asArray()) {
                val aname = a["name"]?.asString()
                if (aname != null) {
                    val attr = AttributeMetadata(aname, this, a)
                    attributes[aname] = attr
                    if (attr.isCollection) {
                        collections.add(attr)
                    }
                }
            }
        }
    }


}
