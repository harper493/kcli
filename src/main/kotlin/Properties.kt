class Properties (
    private val content: String? = null
) {
    private val myTrie = Trie<String, String>("*")
    fun addValue(value: String, keys: Iterable<String>) {
        myTrie.add(value, keys)
    }
    fun get(vararg keys: String) = myTrie.get(keys.toList())

    fun getInt(vararg keys: String, default: Int = 0) = get(*keys)?.toIntOrNull() ?: default
    fun getFloat(vararg keys: String, default: Double = 0.0) = get(*keys)?.toDoubleOrNull() ?: default
    fun loadFile(filename: String): Properties =
        also {
            java.io.File(filename).readText().split("\n")
                .forEach { loadOneLine(it) }
        }
    fun load(content: String) =
        also {
            content.split("\n")
                .forEach { loadOneLine(it) }
        }

    fun write(filename: String) =
        myTrie.map { "${it.first.joinToString(".")} = ${it.second}" }
            .joinToString("\n")
            .writeToFile(filename)

    private fun loadOneLine(line: String) {
        try {
            val (key, value) = line.split(" #")[0].split("=")
            addValue(value.trim(), key.trim().split("."))
        } catch (exc: Exception) {
        }   // ignore parsing error
    }

    init {
        if (content != null) {
            load(content)
        }
    }

    companion object {
        var properties = Properties()
        fun loadFile(filename: String) = properties.loadFile(filename)
        fun load(content: String) = properties.load(content)

        fun get(vararg keys: String) = properties.get(*keys)
        fun getInt(vararg keys: String, default: Int = 0) = properties.getInt(*keys, default = default)
        fun getFloat(vararg keys: String, default: Double = 0.0) = properties.getFloat(*keys, default = default)
    }
}

