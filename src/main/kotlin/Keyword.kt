class Keyword(
    val key: String,
    val value: String?=null,
    val attribute: AttributeMetadata?=null,
    val function: (()->Unit)? = null) {
    operator fun invoke() = function?.invoke()
    fun asString() = value ?: ""
}

class KeywordFn(
    val key: String,
    val function: ()->Unit)

class KeywordList()
{
    val keywords: MutableList<Keyword> = mutableListOf()

    fun addAttributes(attrs: Iterable<AttributeMetadata>,
                      pred: (AttributeMetadata)->Boolean={ true }) =
        also {attrs.filter{ pred(it) }.map{ add(Keyword(it.name, attribute=it)) } }
    fun addAttributes(vararg attrs: AttributeMetadata,
                      pred: (AttributeMetadata)->Boolean={ true }) =
        also{ addAttributes(attrs.asIterable(), pred) }
    fun addKeys(keys: Iterable<String>) = also{ keys.map{ add(Keyword(it, value=it)) }}
    fun addKeys(vararg keys: String) = also{ keys.map{ add(Keyword(it, value=it)) }}
    fun add(vararg fns: KeywordFn) = also{ fns.map{ add(Keyword(it.key, function=it.function)) }}
    fun add(keys: KeywordList) = also{ keys.keywords.map{ add(it) }}
    fun add(vararg keys: Keyword) = also{ keys.map{ add(it) }}
    fun remove(key: String) = also{ keywords.find{ it.key==key }.also{ keywords.remove(it) } }
    fun present(key: String) = keywords.find{it.key==key}!=null
    fun add(key: Keyword) = also{ if (!present(key.key)) keywords.add(key) }
    fun match(key: String) = keywords.mapNotNull { if (it.key.startsWith(key)) it else null }
    fun exactMatch(key: String) = keywords.find{ it.key==key }
    fun toStrings(keys: Iterable<Keyword>?=null) = (keys ?: keywords).map{ it.key }
    fun copy() = KeywordList().add(this)
    operator fun contains(key: Keyword): Boolean =
        keywords.fold(false){b, p -> b || p.key==key.key }

    constructor(vararg fns: KeywordFn) : this() {
        add(*fns)
    }
    constructor(vararg keys: String) : this() {
        addKeys(*keys)
    }
    constructor(vararg attrs: AttributeMetadata,
                pred: (AttributeMetadata)->Boolean={ true }) : this() {
        addAttributes(*attrs, pred=pred)
    }
    constructor(attrs: Iterable<AttributeMetadata>,
                pred: (AttributeMetadata)->Boolean={ true }) : this() {
        addAttributes(attrs, pred=pred)
    }
}
