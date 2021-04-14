class StringTrie<V>(val wildChar: Char? = null): Iterable<Pair<String,V>> {
    class StringTrieIterator<V>(val myIter: Trie.TrieIterator<Char,V>): Iterator<Pair<String,V>> {
        override fun hasNext() = myIter.hasNext()
        override fun next() =
            myIter.next()
                .let{ Pair(it.first.joinToString(""), it.second) }
    }

    private val myTrie  = Trie<Char,V>(wildChar)
    
    override fun iterator() = StringTrieIterator(myTrie.iterator())
    fun add(value: V, keys: String) = myTrie.add(value, keys.asIterable())
    fun remove(keys: String) = myTrie.remove(keys.asIterable())
    fun get(keys: String) = myTrie.get(keys.asIterable())
    fun getAll(keys: String) = myTrie.getAll(keys.asIterable())
    fun getShorter(keys: String) = myTrie.getShorter(keys.asIterable())
    fun getExact(keys: String) = myTrie.getExact(keys.asIterable())
    fun isEmpty() = myTrie.isEmpty()
    fun isNotEmpty() = myTrie.isNotEmpty()
    fun visit(keys: String, fn: (String, V)->Unit) =
        myTrie.visit(keys.asIterable()) { name, value -> fn(name.joinToString(""), value) }
}