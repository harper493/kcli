class TrieNode<KE, V>(
    val key: KE?,
    val parent: TrieNode<KE, V>?,
    val wildKey: KE?,
) {
    var value: V? = null
    var children = mutableMapOf<KE, TrieNode<KE, V>>()
    private fun makeChild(key: KE): TrieNode<KE, V> {
        if (children[key] == null) {
            children[key] = TrieNode<KE, V>(key, this, wildKey)
        }
        return children[key]!!
    }

    val wild = key == wildKey
    val wildness: Int = (parent?.wildness ?: 0) + (if (wild) 1 else 0)
    val depth get(): Int = (parent?.depth?.plus(1)) ?: 0
    fun getName(): MutableList<KE> = parent?.getName()?.apply { add(key!!) } ?: mutableListOf()
    fun addValue(v: V, keys: Iterable<KE>) {
        when {
            keys.none() -> value = v
            else -> makeChild(keys.first()).addValue(v, keys.drop(1))
        }
    }

    fun getUnique(keys: Iterable<KE>): V? = when {
        keys.none() -> value
        else -> children[keys.first()]?.getUnique(keys.drop(1))
    }

    fun getShorter(keys: Iterable<KE>, best: V? = null): V? = when {
        keys.none() -> best
        else -> children[keys.first()]?.getShorter(keys.drop(1), value ?: best) ?: value ?: best
    }

    fun getExact(keys: Iterable<KE>): V? = when {
        keys.none() -> value
        else -> children[keys.first()]?.getExact(keys.drop(1))
    }

    fun getAll(keys: Iterable<KE>): List<TrieNode<KE, V>> =
        (if (keys.none()) {
            listOf(listOf(this), children.values.map { it.getAll(keys) }.flatten())
        } else {
            listOfNotNull(children[keys.first()], children[wildKey])
                .map { it.getAll(keys.drop(1)) }
        }).flatten()
            .filter { it.value != null }

    fun remove(keys: Iterable<KE>) {
        if (keys.none()) {
            value = null
        } else {
            children[keys.first()]?.remove(keys.drop(1))
        }
    }

    fun visit(visitor: (Iterable<KE>, V) -> Unit) {
        if (value != null) {
            visitor(getName(), value!!)
        }
        children.values.forEach { it.visit(visitor) }
    }
}

open class Trie<KE,V>(val wildKey: KE? = null) : Iterable<Pair<Iterable<KE>,V>> {
    class TrieIterator<KE, V>(var here: TrieNode<KE, V>) : Iterator<Pair<Iterable<KE>,V>> {
        val myList = here.getAll(listOf())
        val myIter = myList.iterator()

        override fun hasNext() = myIter.hasNext()
        override fun next() = myIter.next().let{ Pair(it.getName(), it.value!!) }
    }

    private val root = TrieNode<KE, V>(null, null, wildKey)

    fun add(value: V, keys: Iterable<KE>) = root.addValue(value, keys)
    fun remove(keys: Iterable<KE>) = root.remove(keys)
    fun get(keys: Iterable<KE>) = root.getAll(keys).minByOrNull { it.wildness }?.value
    fun getAll(keys: Iterable<KE>) = root.getAll(keys).map { it.value }
    fun getShorter(keys: Iterable<KE>) = root.getShorter(keys)
    fun getExact(keys: Iterable<KE>) = root.getExact(keys)
    fun getExactWild(keys: List<KE>) =
        root.getAll(keys)
            .filter{ it.depth==keys.size }
            .minByOrNull { it.wildness }?.value
    override fun iterator() = TrieIterator(root)
    fun visit(keys: Iterable<KE>, fn: (Iterable<KE>, V) -> Unit) =
        root.getAll(keys).filter { it.value != null }.forEach { fn(it.getName(), it.value!!) }
    fun visit(fn: (Iterable<KE>, V) -> Unit) = visit(listOf(), fn)
}