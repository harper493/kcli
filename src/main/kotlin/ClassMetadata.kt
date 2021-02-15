import AttributeMetadata
import Datatype
import JsonObject

data class ClassMetadata(
    val name: String,
    val jsonMetadata: JsonObject
) {
    lateinit var displayName: String
    var attributes: MutableMap<String, AttributeMetadata> = mutableMapOf()

    fun getAttribute(aname: String) = attributes[aname]

    init {
        val attrMd = jsonMetadata["metadata"]?.get("collection")
        for (a in attrMd?.getArray()!!) {
            val attrName = a["name"]?.getString()!!
            attributes[attrName] = AttributeMetadata(attrName, this, a)
        }
    }
}
