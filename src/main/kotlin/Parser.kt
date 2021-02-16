class SyntaxException(val why: String) : Exception(why)

class AmbiguityException(val values: Iterable<String>) : Exception("")

class Keyword(
    val key: String,
    val value: String?=null,
    val attribute: AttributeMetadata?=null,
    val function: (()->Unit)? = null)
{ }

class KeywordList()
{
    private val keywords: MutableList<Keyword> = mutableListOf()

    fun addAttributes(attrs: Iterable<AttributeMetadata>): KeywordList {
        for (a in attrs) {
            keywords.add(Keyword(a.name, attribute=a))
        }
        return this
    }
    fun addAttributes(vararg attrs: AttributeMetadata): KeywordList {
        addAttributes(*attrs)
        return this
    }
    fun addKeys(vararg keys: String) {
        for (k in keys) keywords.add(Keyword(k, value = k))
    }
    fun addFns(vararg fns: Pair<String, ()->Unit>)
    {
        for (f in fns) keywords.add(Keyword(f.first, function=f.second))
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
    constructor(vararg fns: Pair<String, ()->Unit>) : this() {
        addFns(*fns)
    }
    constructor(vararg keys: String) : this() {
        addKeys(*keys)
    }
    constructor(vararg attrs: AttributeMetadata) : this() {
        addAttributes(*attrs)
    }
}

class Parser (
    private var line: String
    ) {
    private var tokens: MutableList<String> = mutableListOf()
    var tokenStarts: MutableList<Int> = mutableListOf()
    var helpText: String = ""
    private val tokenStart: Int get() = if (tokenStarts.isEmpty()) 0 else tokenStarts.last()
    private var index = 0
    val curtoken: String get() = if (tokens.isNotEmpty()) tokens.last() else ""

    private val digraphs = listOf( ">=", "<=", "!=", ">>", "!>>", "<<", "!<<" )
    private val nameChars = listOf( '_' )
    private val completerChars = listOf( '~', '?' )
    private val numberChars = listOf( '+', '-', '.' )
    private val escChars = mapOf( 'n' to '\n', 'r' to '\r', 't' to '\t' )
    private val quotes = listOf( '\"', '\'' )
    private val whitespace = listOf( ' ', '\t' )
    private val nullCh = 0.toChar()
    val numberRx = Regex("[+-]?.d+(?:\\.\\d+)(?:[Ee]-?\\d+)|0[xX][0-9a-fA-F]+")

    fun nextToken(help: String="", endOK: Boolean=false, extra: String="") : String {
        var escape = false
        var quote = nullCh
        var token: String? = null
        var isName = false
        var isNumber = false

        fun isExtra(ch: Char) = if (extra.isNotEmpty() && extra[0]=='^')
                                    !(ch in extra.drop(1)) and (ch !in quotes)
                                    else ch in extra

        fun isNameChar(ch: Char) = ch.toInt()>=128 || ch.isLetterOrDigit() || ch in nameChars || isExtra(ch)

        fun isNumberChar(ch: Char) = ch.isDigit() || ch in numberChars || isExtra(ch)

        fun good() = index < line.length

        val startIndex = index
        while (good()) {
            var ch = line[index]
            if (escape) {
                ch = escChars[ch] ?: ch
            } else if (ch == '\\') {
                escape = true
                ++index
                ch = nullCh
            } else if (ch == quote) {
                ++index
            } else if (ch in whitespace) {
                break
            } else if (isName) {
                if (!isNameChar(ch)) {
                    break
                }
            } else if (isNumber) {
                if (!isNumberChar(ch)) {
                    break
                }
            } else if (token!=null && !((token + ch) in digraphs)) {
                break
            }
            token = (token ?: "") + ch
            ++index
            if (token.length==1) {
                if (isNumberChar(ch)) {
                    isNumber = true
                } else if (isNameChar(ch)) {
                    isName = true
                } else if (ch in quotes) {
                    quote = ch
                    token = null
                }
            }
        }
        if (token != null) {
            tokens.add(token)
            tokenStarts.add(startIndex)
            while (good() && line[index] in whitespace) {
                ++index
            }
        } else if (!endOK) {
            throw SyntaxException("line ends unexpectedly")
        }
        return token ?: ""
    }

    fun backup() {
        if (tokens.isNotEmpty()) {
            tokens.removeLast()
            index = tokenStarts.last()
            tokenStarts.removeLast()
        }
    }

    fun isFinished() = (tokenStarts.lastOrNull() ?: 0) < line.length

    fun peek() = if (!isFinished()) line[tokenStarts.last()] else nullCh

    fun takeAll() : String {
        var result = ""
        if (!isFinished()) {
            result = line.drop(tokenStart)
            tokenStarts.add(line.length)
            tokens.add(result)
        }
        return result
    }

    fun findKeyword(keys: KeywordList,
                     missOK: Boolean=false,
                     errFn: ((String)->Unit)?=null): Keyword? {
        val token = curtoken
        if (token.isNotEmpty()) {
            val exact = keys.exactMatch(token)
            if (exact!=null) {
                nextToken(endOK=true)
                return exact
            }
            val matches = keys.match(token)
            when (matches.size) {
                0 -> when {
                    missOK -> return null
                    errFn!=null -> errFn(token)
                    else -> throw SyntaxException("unknown keyword'${token}'")
                }
                1 -> {
                    nextToken(endOK=true)
                    return matches[0]
                }
                else -> throw AmbiguityException(keys.toStrings(matches))
            }
        }
        return null
    }
}
