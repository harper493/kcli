class Properties(
    private val content: String? = null
): Iterable<Pair<String,String>> {
    class PropertyIterator(val myIter: Trie.TrieIterator<String,String>):
        Iterator<Pair<String,String>> {
        override fun hasNext() = myIter.hasNext()
        override fun next() = myIter.next()
            .let{ Pair(it.first.joinToString("."), it.second) }

    }

    private val myTrie = Trie<String, String>("*")
    fun addValue(value: String, keys: Iterable<String>) {
        myTrie.add(value, keys)
    }
    fun get(vararg keys: String) = myTrie.get(keys.toList())

    fun getInt(vararg keys: String, default: Int = 0) = get(*keys)?.toIntOrNull() ?: default
    fun getFloat(vararg keys: String, default: Double = 0.0) = get(*keys)?.toDoubleOrNull() ?: default
    fun loadFile(filename: String): Properties =
        also {
            readFileOrEmpty(filename).split("\n")
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

    override operator fun iterator() = PropertyIterator(myTrie.iterator())

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

