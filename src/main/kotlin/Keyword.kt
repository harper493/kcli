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
    val keywords = Trie<Char, Keyword>()
    fun stringToSequence(str: String): List<Char> = str.map{ it }
    fun addOne(key: Keyword):KeywordList {
        keywords.add(key, stringToSequence(key.key))
        return this
    }
    fun remove(key: String) = keywords.remove(stringToSequence(key))
    fun match(key: String) = keywords.getAll(stringToSequence(key))
    fun exactMatch(key: String) = keywords.find{ it.key==key }
    fun matchShorter(key: String) = keywords.getShorter(stringToSequence(key))
    operator fun contains(key: Keyword): Boolean = keywords.getExact(stringToSequence(key.key)) != null
    fun toStrings(keys: Iterable<Keyword>?=null) = (keys ?: keywords).map{ it.key }
    fun copy(): KeywordList = KeywordList().add(this)

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
        keys.keywords.map{ addOne(it) }
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
