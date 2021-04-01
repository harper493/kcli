class StringTrie<V>(val wildChar: Char? = null): Trie<Char,V>(wildChar) {
    
    fun add(value: V, keys: String) = add(value, keys.asIterable())
    fun remove(keys: String) = remove(keys.asIterable())
    fun get(keys: String) = get(keys.asIterable())
    fun getAll(keys: String) = getAll(keys.asIterable())
    fun getShorter(keys: String) = getShorter(keys.asIterable())
    fun getExact(keys: String) = getExact(keys.asIterable())
    fun visit(keys: String, fn: (String, V)->Unit) =
        visit(keys.asIterable()) { name, value -> fn(name.joinToString(""), value) }
}