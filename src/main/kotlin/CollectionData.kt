class CollectionData(val classMd: ClassMetadata) {
    val objects: MutableMap<String, ObjectData> = mutableMapOf()

    operator fun get(objectName: String): ObjectData? = objects[objectName]
    val size get() = objects.size
    fun isEmpty() = objects.isEmpty()
    fun isNotEmpty() = objects.isNotEmpty()
    fun first() = objects[objects.keys.first()]
    fun add(obj: ObjectData) {
        objects[obj["name"].value] = obj
    }

    fun load(json: JsonObject): CollectionData {
        for (objJson in json.asArray()) {
            try {
                add(ObjectData(classMd).load(objJson))
            } catch (exc: Exception) {
            }
        }
        return this
    }

    operator fun iterator() = objects.iterator()
}
