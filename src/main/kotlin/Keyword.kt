class Keyword(
    val key: String,
    val value: String?=null,
    val attribute: AttributeMetadata?=null,
    val function: (()->Unit)? = null) {
    operator fun invoke() = function?.invoke()
    fun sameReferent(other: Keyword) =
        value==other.value && attribute==other.attribute && function==other.function
    fun asString() = value ?: ""
}

class KeywordFn(
    val key: String,
    val function: ()->Unit)

class KeywordList()
{
    val keywords = StringTrie<Keyword>()
    fun addOne(key: Keyword): KeywordList {
        keywords.add(key, key.key)
        return this
    }
    fun remove(key: String) = keywords.remove(key)
    fun match(key: String) = keywords.getAll(key)
    fun exactMatch(key: String) = keywords.getExact(key)
    fun matchShorter(key: String) = keywords.getShorter(key)
    operator fun contains(key: Keyword): Boolean = keywords.getExact(key.key) != null
    fun toStrings() = keywords.map{ it.second.key }
    fun copy(): KeywordList = KeywordList().add(this)
    fun addAbbreviations() {
        keywords.map{ it.second }
            .map{ Pair(it.function, Properties.get("abbreviate", it.key)) }
            .filter{ it.second!=null }
            .forEach{ addOne(Keyword(it.second!!, function=it.first)) }
    }

    /*
    From here on are "convenience functions" to simplify usage
     */

    fun addAttributes(attrs: Iterable<AttributeMetadata>,
                      pred: (AttributeMetadata)->Boolean={ true }) =
        attrs.filter{ pred(it) }
            .forEach{ addOne(Keyword(it.name, attribute=it))
                      if (it.isCollection) addOne(Keyword(it.typeName, attribute=it))
        }
    fun addAttributes(vararg attrs: AttributeMetadata,
                      pred: (AttributeMetadata)->Boolean={ true }) =
        addAttributes(attrs.asIterable(), pred)
    fun addKeys(keys: Iterable<String>) = keys.map{ addOne(Keyword(it, value=it)) }
    fun addKeys(vararg keys: String): KeywordList {
        keys.forEach { addOne(Keyword(it, value = it)) }
        return this
    }
    fun add(vararg fns: KeywordFn) = fns.map{ addOne(Keyword(it.key, function=it.function)) }
    fun add(keys: KeywordList): KeywordList {
        keys.keywords.map{ addOne(it.second) }
        return this
    }
    fun add(vararg keys: Keyword) = keys.map{ addOne(it) }

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
