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

    fun getAttribute(aname: String) = attributes[aname]
    fun getCollections(): List<AttributeMetadata> = collections

    init {
        val attrMd = jsonMetadata["metadata"]?.get("collection")
        for (a in attrMd?.getArray()!!) {
            val aname = a["name"]?.getString()
            if (aname!=null) {
                val attr = AttributeMetadata(aname, this, a)
                attributes[aname] = attr
                if (attr.isCollection) {
                    collections.add(attr)
                }
            }
        }
    }
}
