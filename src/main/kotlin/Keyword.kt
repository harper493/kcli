class Keyword(
    val key: String,
    val value: String?=null,
    val attribute: AttributeMetadata?=null,
    val function: (()->Unit)? = null)

class KeywordFn(
    val key: String,
    val function: ()->Unit)

class KeywordList()
{
    private val keywords: MutableList<Keyword> = mutableListOf()

    fun addAttributes(attrs: Iterable<AttributeMetadata>,
                      pred: (AttributeMetadata)->Boolean={ true }): KeywordList {
        for (a in attrs) keywords.add(Keyword(a.name, attribute=a))
        return this
    }
    fun addKeys(keys: Iterable<String>): KeywordList {
        for (k in keys) keywords.add(Keyword(k, value=k))
        return this
    }
    fun addAttributes(vararg attrs: AttributeMetadata,
                      pred: (AttributeMetadata)->Boolean={ true }): KeywordList {
        addAttributes(attrs.asIterable(), pred)
        return this
    }
    fun addKeys(vararg keys: String): KeywordList {
        for (k in keys) keywords.add(Keyword(k, value = k))
        return this
    }
    fun addFns(vararg fns: KeywordFn): KeywordList
    {
        for (k in fns) keywords.add(Keyword(k.key, function=k.function))
        return this
    }
    fun add(keys: KeywordList): KeywordList {
        for (k in keys.keywords) keywords += k
        return this
    }
    fun add(vararg keys: Keyword): KeywordList {
        for (k in keys) keywords += k
        return this
    }
    fun remove(key: String) {
        keywords.remove(key)
    }
    fun match(key: String): List<Keyword> {
        val result: MutableList<Keyword> = mutableListOf()
        for (k in keywords) if (k.key.startsWith(key)) result.add(k)
        return result
    }
    fun exactMatch(key: String): Keyword? {
        for (k in keywords) if (k.key==key) return k
        return null
    }
    fun toStrings(keys: Iterable<Keyword>): List<String> {
        val result: MutableList<String> = mutableListOf()
        for (k in keys) result.add(k.key)
        return result
    }
    fun copy() =
        KeywordList().add(this)

    constructor(vararg fns: KeywordFn) : this() {
        addFns(*fns)
    }
    constructor(vararg keys: String) : this() {
        addKeys(*keys)
    }
    constructor(vararg attrs: AttributeMetadata,
                pred: (AttributeMetadata)->Boolean={ true }) : this() {
        addAttributes(*attrs)
    }
    constructor(attrs: Iterable<AttributeMetadata>,
                pred: (AttributeMetadata)->Boolean={ true }) : this() {
        addAttributes(attrs)
    }
}
