class Parser (
    private var line: String
    ) {
    private var tokens: MutableList<String?> = mutableListOf()
    private var tokenStarts: MutableList<Int> = mutableListOf()
    private var lineIndex = 0
    private var finished = false
    enum class TokenType { ttName, ttNumber, ttInt, ttAny, ttExplicit, ttGeneral, ttAll, ttNonBlank }
    var curToken: String? = null

    private val escChars = mapOf( 'n' to '\n', 'r' to '\r', 't' to '\t' )
    private val quotes = listOf( '\"', '\'' )
    private val whitespace = listOf( ' ', '\t' )
    private val nullCh = 0.toChar()

    fun nextToken(help: String="",
                  tokenType: TokenType=TokenType.ttNonBlank,
                  validator: Validator=Validator(),
                  completer: CliCompleter=CliCompleter(),
                  type: Datatype?=null,
                  attribute: AttributeMetadata?=null,
                  helpContext: HelpContext? = null,
                  endOk: Boolean=false): String? {
        val myDatatype = attribute?.type ?: type
        var escape = false
        var quote = nullCh
        var token: String? = null
        var ch = nullCh
        val myValidator =
            myDatatype?.validator
                ?: when (if (validator.isNull) tokenType else TokenType.ttExplicit) {
                    TokenType.ttName -> Validator("""\*?[a-zA-Z0-9-_$\.]*\*?""")
                    TokenType.ttNumber -> Validator("""[+-]?\d+(\.\d*)?[a-zA-z]*""")
                    TokenType.ttInt -> Validator("""[+-]?\d+""")
                    TokenType.ttGeneral -> Validator("""\w+|[=<>!]+|\d[\w\.]*""")
                    TokenType.ttAll, TokenType.ttAny -> Validator(".*")
                    TokenType.ttExplicit -> validator
                    TokenType.ttNonBlank -> Validator("""\S+""")
                }
        fun good() = lineIndex < line.length

        val startIndex = lineIndex
        if (!good()) {
            finished = true
        } else {
            while (good()) {
                ch = line[lineIndex]
                if (escape) {
                    ch = escChars[ch] ?: ch
                } else if (ch == '\\') {
                    escape = true
                    ch = nullCh
                } else if (ch == quote) {
                    quote = nullCh
                    ch = nullCh
                } else if (ch in quotes && (token ?: "").isEmpty()) {
                    quote = ch
                    ch = nullCh
                } else if ((token ?: "").isNotEmpty()
                    && ch!=completerCh
                    && quote==nullCh) {
                    if (!myValidator.validatePfx(token + ch)) {
                        break
                    }
                }
                if (ch!=nullCh) {
                    token = (token ?: "") + ch
                    if (ch == completerCh) {
                        break
                    }
                }
                ++lineIndex
            }
        }
        tokens.add(token)
        curToken = token
        tokenStarts.add(startIndex)
        while (good() && line[lineIndex] in whitespace) {
            ++lineIndex
        }
        if (ch==completerCh){
            throw CompletionException(
                if (token!!.length<=1) {
                    completer.help(helpContext, line.dropLast(1))
                } else {
                    completer.complete(
                        line.dropLast(1),
                        token!!.dropLast(1)
                    )
                }
            )
        }
        CliException.throwIf("unexpected end of line"){ !endOk && curToken==null }
        return curToken
    }


    fun getInt(): Int {
        val result: Int
        try {
            result = (nextToken(tokenType=TokenType.ttInt)?:"").toInt()
        } catch (exc: Exception) {
            throw CliException("expected integer not '$curToken'")
        }
        return result
    }

    fun isFinished() = lineIndex >= line.length

    fun checkFinished() = if (nextToken(endOk=true)!=null)
        throw CliException("unexpected text at end of command '$curToken'")
        else null

    fun backup() {
        if (tokens.size>0) {
            lineIndex = tokenStarts[tokenStarts.size - 1]
            tokens.removeLast()
            tokenStarts.removeLast()
        }
    }

    fun peek() = line.drop(lineIndex).take(1)

    fun peekAnyOf(tokens: Iterable<String>) = tokens.fold(false){ b,s -> b || s.take(1)==peek() }

    fun peekRx(rx: String) = Regex("^$rx.*").matches(peek())

    fun skipToken(token: String) : Boolean =
        (peek()==token.take(1)).also{ if (it) nextToken() }

    fun getObjectName(initialExtras: KeywordList=KeywordList(),
                      finalExtras: KeywordList=KeywordList(),
                      helpContext: HelpContext?=null,
                      wildOk: Boolean=true,
                      missOk: Boolean=false,
                      initialPred: (AttributeMetadata)->Boolean={ true },
                      keywordAdder: (ClassMetadata, KeywordList)->Unit={ _, _ -> }): Pair<ObjectName, Keyword?> {
        val result = ObjectName()
        var terminator: Keyword? = null
        var curMd = CliMetadata.getConfigMd()
        while (true) {
            val classKeys = KeywordList(curMd.collections.filter{initialPred(it)})
            if (result.isEmpty) {
                classKeys.add(initialExtras)
                classKeys.addAttributes(CliMetadata.getPolicyManagerMd().getAttribute("configurations")!!)
            } else {
                classKeys.add(finalExtras)
            }
            classKeys.addAbbreviations()
            keywordAdder(curMd, classKeys)
            val classKey = findKeyword(classKeys, helpContext=helpContext, missOk=true)
                ?: if (missOk) break
                else throw if (curToken==null) CliException("end of line found where object name or keyword expected")
                           else CliException("unknown collection or keyword '$curToken'")
            val attrMd = classKey.attribute
            if (attrMd != null && attrMd.isCollection) {
                val name = nextToken(endOk=wildOk,
                    helpContext=helpContext,
                    tokenType=TokenType.ttName,
                    completer=ObjectCompleter(result.copy().append(attrMd, ""),
                        (if (wildOk) finalExtras else KeywordList())))
                if (wildOk) {
                    val key = finalExtras.exactMatch(name ?: "")
                    result.append(attrMd, if (key == null) name ?: "" else "")
                    if (key != null) {
                        terminator = key
                        break
                    }
                } else {
                    CliException.throwIf("wildcard name '$name' not permitted here")
                        { "*" in name!! }
                    result.append(attrMd, name!!)
                }
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
                    helpContext: HelpContext?=null,
                    missOk: Boolean=false,
                    endOk: Boolean=false,
                    errFn: ((String)->Unit)?=null): Keyword? {
        var result: Keyword? = null
        val token = nextToken(completer=KeywordCompleter(keys), helpContext=helpContext, endOk=missOk || endOk)
        if ((token?:"").isNotEmpty()) {
            val exact = keys.exactMatch(token!!)
            if (exact!=null) {
                result = exact
            } else {
                val matches = keys.match(token).filterNotNull()
                    .removeDuplicates{ a,b -> a.sameReferent(b)
                                       && a.key.length < b.key.length }
                when (matches.size) {
                    0 -> {
                        backup()
                        when {
                            missOk -> return null
                            errFn != null -> errFn(token)
                            else -> throw CliException("unknown keyword '${token}'")
                        }
                    }
                    1 -> {
                        result = matches[0]
                    }
                    else -> {
                        result = keys.matchShorter(token)
                        if (result==null) {
                            throw CliException(
                                "keyword '$token' matches ${if (matches.size == 2) "both" else "all"} of: ${
                                    matches.map{ it.key }
                                        .removePrefixes()
                                        .joinToString(", ")}")
                        }
                    }
                }
            }
        }
        return result
    }
    companion object {
        const val completerCh = '~'
    }
}


