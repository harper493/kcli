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

    operator fun iterator() = objects.values.iterator()

    fun<T,U> map(tfm: (Pair<String,ObjectData>) -> Pair<T,U>): Map<T,U> =
        objects.map{it -> tfm(Pair(it.key, it.value))}.toMap()

    fun<T> mapKeys(tfm: (String) -> T): Map<T,ObjectData> =
        objects.map{it -> Pair(tfm(it.key), it.value)}.toMap()

    fun<T> mapValues(tfm: (ObjectData) -> T): Map<String,T> =
        objects.map{it -> Pair(it.key, tfm(it.value))}.toMap()

}
