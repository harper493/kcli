class Properties (
    private var filename: String? = null
) {
    private class Property (
        val name: String,
        val parent: Property?
    ) {
        private var leafValue: String? = null
        val value get() = leafValue
        private var children: MutableMap<String, Property> = mutableMapOf()
        private fun getChild(key: String) : Property {
            if (children[key] == null) {
                children[key] = Property(key, this)
            }
            return children[key]!!
        }
        val wild = name=="*"
        val wildness: Int = (parent?.wildness ?: 0) + (if (wild) 1 else 0)
        fun getName() : MutableList<String> = parent?.getName()?.apply{add(name)} ?: mutableListOf()
        fun addValue(value: String, keys: Iterable<String>) {
            when {
                keys.none() -> leafValue = value
                else ->getChild(keys.first()).addValue(value, keys.drop(1))
            }
        }
        fun getUnique(keys: Iterable<String>) : String? = when {
            keys.none() -> leafValue
            else -> children[keys.first()]?.getUnique(keys.drop(1))
        }
        fun getWild(keys: Iterable<String>): List<Property> = when {
            keys.none() -> listOf(this)
            else -> listOfNotNull(children[keys.first()], children["*"]).map{it.getWild(keys.drop(1))}.flatten()
        }
    }
    private val root = Property("", null)
    fun addValue(value: String, keys: Iterable<String>) { root.addValue(value, keys) }
    fun getInt(vararg keys: String, default: Int=0) = get(*keys)?.toIntOrNull() ?: default
    fun getFloat(vararg keys: String, default: Double=0.0) = get(*keys)?.toDoubleOrNull() ?: default
    fun get(vararg keys: String) = root.getWild(keys.toList()).sortedBy { it.wildness }.firstOrNull()?.value
    fun load(fn: String): Properties {
        filename = fn
        java.io.File(filename!!).forEachLine {
            try {
                val (key, value) = it.split("#")[0].split("=")
                addValue(value.trim(), key.trim().split("."))
            } catch (exc: Exception) { }
        }
        return this
    }
    init {
        if (filename!=null) {
            load(filename!!)
        }
    }
    companion object {
        private var properties = Properties()
        fun load(fn: String): Properties {
            return properties.load(fn)
        }
        fun get(vararg keys: String) = properties.get(*keys)
        fun getInt(vararg keys: String, default: Int=0) = properties.getInt(*keys, default=default)
        fun getFloat(vararg keys: String, default: Double=0.0) = properties.getFloat(*keys, default=default)
    }
}