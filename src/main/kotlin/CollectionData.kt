class CollectionData(val classMd: ClassMetadata) {
    val objects: MutableMap<String, ObjectData> = mutableMapOf()

    operator fun get(objectName: String): ObjectData? = objects[objectName]
    fun add(obj: ObjectData) { objects[obj["name"].value] = obj}
    fun load(json: JsonObject): CollectionData {
        for (objJson in json.asArray()) {
            try {
                add(ObjectData(classMd).load(objJson))
            } catch (exc: Exception) { }  // just ignore object without name
        }
        return this
    }
}