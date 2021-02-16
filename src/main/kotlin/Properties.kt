class Properties (
    private var filename: String? = null
) {
    class Property (
        name: String,
    ) {
        private var leaf: String? = null
        private var children: MutableMap<String, Property> = mutableMapOf()
        private fun getChild(key: String) : Property {
            if (children[key] == null) {
                children[key] = Property(key)
            }
            return children[key]!!
        }
        fun addValue(value: String, keys: Iterable<String>) {
            if (keys.none()) {
                leaf = value
            } else {
                getChild(keys.first()).addValue(value, keys.drop(1))
            }
        }
        fun get(keys: Iterable<String>) : String? {
            return if (keys.none()) {
                leaf
            } else {
               children[keys.first()]?.get(keys.drop(1) )
            }
        }
    }
    private val root = Property("")
    fun addValue(value: String, keys: Iterable<String>) {
        root.addValue(value, keys)
    }
    fun get(vararg keys: String) = root.get(keys.toList())
    fun loadFile(fn: String) {
        filename = fn
        java.io.File(filename!!).forEachLine {
            try {
                val (key, value) = it.split("#")[0].split("=")
                addValue(value.trim(), key.trim().split("."))
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
            properties.loadFile(fn)
        }
        fun get(vararg keys: String) = properties.get(*keys)
    }
}