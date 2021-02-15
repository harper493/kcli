class Properties (
    var filename: String? = null
) {
    class Property (
        name: String,
    ) {
        var leaf: String? = null
        var children: MutableMap<String, Property> = mutableMapOf()
        fun getChild(key: String) : Property {
            if (children[key] == null) {
                children[key] = Property(key)
            }
            return children[key]!!
        }
        fun addValue(value: String, keys: Array<String>) {
            if (keys.isEmpty()) {
                leaf = value
            } else {
                getChild(keys[0]).addValue(value, keys.copyOfRange(1, keys.size))
            }
        }
        fun get(keys: Array<String>) : String? {
            if (keys.isEmpty()) {
                return leaf
            } else {
                val c = children[keys[0]]
                return c?.get(keys.copyOfRange(1, keys.size))
            }
        }
    }
    val root = Property("")
    fun addValue(value: String, keys: Array<String>) {
        root.addValue(value, keys)
    }
    fun get(vararg keys: String) = root.get(keys as Array<String>)
    fun load_(fn: String) {
        filename = fn
        java.io.File(filename!!).forEachLine {
            try {
                val (key, value) = it.split("#")[0].split("=")
                addValue(value.trim(), key.trim().split(".").toTypedArray())
            } catch (exc: Exception) { }
        }
    }
    init {
        if (filename!=null) {
            load(filename!!)
        }
    }
    companion object {
        private var properties = Properties()
        fun load(fn: String) {
            properties.load_(fn)
        }
        fun get(vararg keys: String) = properties.get(*keys)
    }
}