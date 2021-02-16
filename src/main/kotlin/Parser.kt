class SyntaxException(val why: String) : Exception(why)

class AmbiguityException(val values: Iterable<String>) : Exception("")


class Parser (
    private var line: String
    ) {
    private var tokens: MutableList<String> = mutableListOf()
    var tokenStarts: MutableList<Int> = mutableListOf()
    var helpText: String = ""
    private val tokenStart: Int get() = if (tokenStarts.isEmpty()) 0 else tokenStarts.last()
    private var index = 0
    val curToken: String get() = tokens.lastOrNull() ?: ""

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

    fun getObjectName(extras: KeywordList=KeywordList(), missOK: Boolean=false) : Pair<ObjectName, Keyword?> {
        val result = ObjectName()
        var terminator: Keyword? = null
        var curMd = Metadata.getConfigMd()
        while (true) {
            val classKeys = KeywordList(curMd.getCollections())
            if (result.isEmpty) {
                classKeys.addAttributes(Metadata.getPolicyManagerMd().getAttribute("configurations")!!)
            }
            classKeys.add(extras)
            val classKey = findKeyword(classKeys, missOK=true)
            if (classKey==null) {
                if (missOK) {
                    backup()
                    break
                }
                else throw Exception("unknown collection '$curToken'")
            }
            val attrMd = classKey.attribute
            if (attrMd != null) {
                val oname = nextToken(endOK=true)
                if (oname!="") {
                    val extra = extras.exactMatch(oname)
                    if (extra != null) {
                        result.append(attrMd, "")
                        terminator = extra
                        break
                    }
                }
                result.append(attrMd, oname)
                nextToken(endOK=true)
                curMd = attrMd.myClass
            } else {
                terminator = classKey
                break
            }
            if (isFinished()) break
        }
        return Pair(result, terminator)
    }

    fun findKeyword(keys: KeywordList,
                     missOK: Boolean=false,
                     errFn: ((String)->Unit)?=null): Keyword? {
        val token = curToken
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
