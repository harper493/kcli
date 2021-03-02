class SyntaxException(val why: String) : Exception(why)

class AmbiguityException(val values: Iterable<String>) : Exception("")


class Parser (
    private var line: String
    ) {
    private var tokens: MutableList<String?> = mutableListOf()
    var tokenStarts: MutableList<Int> = mutableListOf()
    var helpText: String = ""
    private val tokenStart: Int get() = if (tokenStarts.isEmpty()) 0 else tokenStarts.last()
    private var lineIndex = 0
    private var tokenIndex = -1
    private var finished = false
    enum class TokenType { ttName, ttNumber, ttAny, ttExplicit, ttGeneral, ttAll }
    val curToken: String? get() = tokens[tokenIndex]
    var lastKeyword: Keyword? = null; private set

    private val digraphs = listOf( ">=", "<=", "!=", ">>", "!>>", "<<", "!<<", )
    private val nameChars = listOf( '_' )
    private val completerChars = listOf( '~', '?' )
    private val numberChars = listOf( '+', '-', '.' )
    private val escChars = mapOf( 'n' to '\n', 'r' to '\r', 't' to '\t' )
    private val quotes = listOf( '\"', '\'' )
    private val whitespace = listOf( ' ', '\t' )
    private val nullCh = 0.toChar()

    fun nextToken(help: String="", endOk: Boolean=false, type: TokenType=TokenType.ttGeneral, validator: Validator=Validator()) : String? {
        if (tokenIndex >= 0 && tokens.size == tokenIndex + 1 && tokens[tokenIndex] == null) {
            if (endOk) {
                return null
            } else {
                throw SyntaxException("line ends unexpectedly")
            }
        } else {
            ++tokenIndex
            if (tokens.size < tokenIndex + 1) {
                readToken(type=type, validator=validator)
            }
            return tokens[tokenIndex]
        }
    }

    fun reparse(help: String="", endOk: Boolean=false, type: TokenType=TokenType.ttGeneral, validator: Validator=Validator()) : String? {
        backup()
        return nextToken(help=help, type=type, validator=validator)
    }

    private fun readToken(help: String="", type: TokenType=TokenType.ttGeneral, validator: Validator=Validator()) {
        var escape = false
        var quote = nullCh
        var token: String? = null
        var isName = false
        var isNumber = false
        val myValidator = when (if (validator.isNull) type else TokenType.ttExplicit) {
            TokenType.ttName -> Validator("""\*?[a-zA-Z][a-zA-Z0-9-_$]*\*?""")
            TokenType.ttNumber -> Validator("""[+-]?\d+(.\d+)?[a-zA-z]*""")
            TokenType.ttGeneral -> Validator("""\w+|[=<>!]+|\d[\w\.]*""")
            TokenType.ttAll -> Validator(".*")
            TokenType.ttExplicit -> validator
            else -> Validator("""\S+""")
        }
        fun good() = lineIndex < line.length

        val startIndex = lineIndex
        if (!good()) {
            finished = true
        } else {
            while (good()) {
                var ch = line[lineIndex]
                if (escape) {
                    ch = escChars[ch] ?: ch
                } else if (ch == '\\') {
                    escape = true
                    ch = nullCh
                } else if (ch == quote) {
                    ch = nullCh
                } else if (ch in quotes && (token?:"").length==0) {
                    quote = ch
                    ch = nullCh
                } else if ((token?:"").length > 0) {
                    if (!myValidator.validatePfx(token + ch)) {
                        break
                    }
                }
                if (ch!=nullCh) {
                    token = (token ?: "") + ch
                }
                ++lineIndex
            }
        }
        tokens.add(token)
        tokenStarts.add(startIndex)
        while (good() && line[lineIndex] in whitespace) {
            ++lineIndex
        }
    }

    fun backup() {
        if (tokenIndex >= 0) {
            --tokenIndex
            tokens = tokens.take(tokenIndex+1).toMutableList()
            lineIndex = tokenStarts[tokenIndex+1]
            tokenStarts = tokenStarts.take(tokenIndex+1).toMutableList()
            finished = false
        }
    }

    fun getNumber(): Int {
        var result: Int
        try {
            result = (curToken?:"").toInt()
            nextToken()
        } catch (exc: Exception) {
            throw CliException("expected integer not '$curToken'")
        }
        return result
    }

    fun isFinished() = finished

    fun peek() = if (!isFinished()) line[tokenStarts.last()] else nullCh

    fun skipToken(token: String) : Boolean =
        (curToken==token).also{ if (it) nextToken(); it }

    fun skipTokens(vararg tokens: String): String? {
        if (curToken in tokens) {
            val t = curToken
            nextToken()
            return t
        } else {
            return null
        }
    }

    fun getObjectName(extras: KeywordList=KeywordList(), missOk: Boolean=false) : Pair<ObjectName, Keyword?> {
        val result = ObjectName()
        var terminator: Keyword? = null
        var curMd = Metadata.getConfigMd()
        while (true) {
            val classKeys = KeywordList(curMd.collections)
            if (result.isEmpty) {
                classKeys.addAttributes(Metadata.getPolicyManagerMd().getAttribute("configurations")!!)
            }
            classKeys.add(extras)
            val classKey = findKeyword(classKeys, missOk=true)
                ?: if (missOk) {
                    backup()
                    break
                } else throw Exception("unknown collection '$curToken'")
            val attrMd = classKey.attribute
            if (attrMd != null) {
                if (curToken ?: "" !="") {
                    val extra = extras.exactMatch(curToken!!)
                    if (extra != null) {
                        result.append(attrMd, "")
                        terminator = extra
                        break
                    }
                }
                result.append(attrMd, curToken ?: "")
                nextToken(endOk=true)
                curMd = attrMd.containedClass!!
            } else {
                terminator = classKey
                break
            }
            if (isFinished()) break
        }
        return Pair(result, terminator)
    }

    fun findKeyword(keys: KeywordList,
                    missOk: Boolean=false,
                    errFn: ((String)->Unit)?=null): Keyword? {
        var result: Keyword? = null
        val token = curToken ?: ""
        if (token.isNotEmpty()) {
            val exact = keys.exactMatch(token)
            if (exact!=null) {
                nextToken(endOk=true)
                result = exact
            } else {
                val matches = keys.match(token)
                when (matches.size) {
                    0 -> {
                        //backup()
                        when {
                            missOk -> return null
                            errFn != null -> errFn(token)
                            else -> throw SyntaxException("unknown keyword '${token}'")
                        }
                    }
                    1 -> {
                        nextToken(endOk = true)
                        result = matches[0]
                    }
                    else -> throw AmbiguityException(keys.toStrings(matches))
                }
            }
        }
        lastKeyword = result
        return result
    }

    fun useKeyword() { lastKeyword = null }
}


