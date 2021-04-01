class Properties (
    private var filename: String? = null
) {
    private val myTrie = Trie<String, String>("*")
    fun addValue(value: String, keys: Iterable<String>) {
        myTrie.add(value, keys)
    }
    fun get(vararg keys: String) = myTrie.get(keys.toList())

    fun getInt(vararg keys: String, default: Int = 0) = get(*keys)?.toIntOrNull() ?: default
    fun getFloat(vararg keys: String, default: Double = 0.0) = get(*keys)?.toDoubleOrNull() ?: default
    fun load(fn: String? = null): Properties {
        if (fn != null) {
            filename = fn
        }
        try {
            java.io.File(filename!!).forEachLine {
                try {
                    val (key, value) = it.split(" #")[0].split("=")
                    addValue(value.trim(), key.trim().split("."))
                } catch (exc: Exception) {
                }   // ignore parsing error
            }
        } catch (exc: Exception) {
        }
        return this
    }

    fun write(fn: String? = null) {
        val writer = java.io.PrintWriter(fn ?: filename ?: "")
        myTrie.visit()
            { name, value -> writer.append("${name.joinToString(".")} = $value\n") }
        writer.flush()
        writer.close()
    }

    fun visit(vararg keys: String, fn: (Iterable<String>, String) -> Unit) =
        myTrie.visit(fn)

    init {
        if (filename != null) {
            load(filename!!)
        }
    }

    companion object {
        private var properties = Properties()
        fun load(fn: String): Properties {
            return properties.load(fn)
        }

        fun get(vararg keys: String) = properties.get(*keys)
        fun getInt(vararg keys: String, default: Int = 0) = properties.getInt(*keys, default = default)
        fun getFloat(vararg keys: String, default: Double = 0.0) = properties.getFloat(*keys, default = default)
        fun getColor(color: String) = properties.get("color", color)
        fun getColors(vararg colors: String) = colors.mapNotNull { getColor(it) }
    }
}

