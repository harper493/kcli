class Trie<KE,V>(val wildKey: KE? = null) {
    private class Node<KE,V>(
        val owner: Trie<KE,V>,
        val key: KE?,
        val parent: Node<KE,V>?
    ) {
        var value: V? = null
        var children = mutableMapOf<KE,Node<KE,V>>()
        private fun makeChild(key: KE) : Node<KE,V> {
            if (children[key] == null) {
                children[key] = Node<KE,V>(owner, key, this)
            }
            return children[key]!!
        }
        val wildKey get() = owner.wildKey
        val wild = key==wildKey
        val wildness: Int = (parent?.wildness ?: 0) + (if (wild) 1 else 0)
        fun getName() : MutableList<KE> = parent?.getName()?.apply{add(key!!)} ?: mutableListOf()
        fun addValue(v: V, keys: Iterable<KE>) {
            when {
                keys.none() -> value = v
                else ->makeChild(keys.first()).addValue(v, keys.drop(1))
            }
        }
        fun getUnique(keys: Iterable<KE>) : V? = when {
            keys.none() -> value
            else -> children[keys.first()]?.getUnique(keys.drop(1))
        }
        fun getShorter(keys: Iterable<KE>, best: V?=null) : V? = when {
            keys.none() -> best
            else -> children[keys.first()]?.getShorter(keys.drop(1), value ?: best) ?: value ?: best
        }
        fun getAll(keys: Iterable<KE>): List<Node<KE,V>> =
            if (keys.none()) listOf(this)
            else listOfNotNull(children[keys.first()], children[wildKey])
                .map{it.getAll(keys.drop(1))}
                .flatten()
        fun visit(visitor: (Iterable<KE>,V)->Unit) {
            if (value!=null) {
                visitor(getName(), value!!)
            }
            children.values.forEach{ it.visit(visitor) }
        }
    }
    private val root = Node<KE,V>(this, null, null)

    fun addValue(value: V, keys: Iterable<KE>) { root.addValue(value, keys) }
    fun get(keys: Iterable<KE>) = root.getAll(keys).minBy { it.wildness }?.value
    fun getShorter(keys: Iterable<KE>) = root.getShorter(keys)
    fun visit(keys: Iterable<KE>, fn: (Iterable<KE>, V)->Unit) =
        root.getAll(keys).filter{ it.value!=null }.forEach{fn(it.getName(), it.value!!)}
}