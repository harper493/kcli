interface JsonObject {
    fun asString(): String
    fun asInt(): Int
    fun asBoolean(): Boolean
    fun asFloat(): Double
    fun asArray(): List<JsonObject>
    fun asDict(): Map<String, JsonObject>
    fun isString(): Boolean
    fun isOnlyString(): Boolean
    fun isInt(): Boolean
    fun isNull(): Boolean
    fun isBoolean(): Boolean
    fun isFloat(): Boolean
    fun isArray(): Boolean
    fun isDict(): Boolean
    fun parse(string: String): JsonObject
    fun toMap(): Map<String, String>
    fun toString(indent:Int = 2, prefix:String = ""): String
    operator fun get(key: String): JsonObject?
    operator fun get(index: Int): JsonObject?
    fun toSimpleString(): String

    companion object {
        fun load(value: String) = JsonObjectImpl().parse(value)
        fun make() = JsonObjectImpl()
    }
}

class JsonException(text: String) : Exception(text)

class JsonObjectImpl : JsonObject {
    class Reader(
        val string : String
    ) {
        private var index: Int = 0
        fun good() : Boolean {
            return index < string.length
        }
        fun skipWs() : Char? {
            while (good() && (string[index] in " \t\n")) {
                ++index
            }
            return if (good()) string[index] else null
        }

        private fun getChar(): Char? {
            ++index
            return skipWs()
        }
        fun skipChar(ch: Char, doSkipWs: Boolean = true) : Boolean {
            val thisCh = if (doSkipWs) skipWs() else string[index]
            return if (thisCh==ch) {
                getChar()
                true
            } else {
                false
            }
        }
        fun getStr() : String? {
            var result = ""
            if (!skipChar('"')) {
                return null
            }
            var escape = false
            while (good()) {
                if (!escape && skipChar('\"', doSkipWs=false)) {
                    return result
                } else if (!escape && skipChar('\\', doSkipWs=false)) {
                    escape = true
                } else if (escape) {
                    if (string[index]=='u') {
                        var code = ""
                        for (i in 0..3) {
                            ++index
                            if (good()) {
                                code = "$code${string[index]}"
                            } else break
                        }
                        result += code.toInt(16).toChar()
                    } else {
                        val ch = "" + string[index]
                        result += escapes[ch] ?: ch
                    }
                    ++index
                    escape = false
                } else {
                    result += string[index]
                    ++index
                    escape = false
                }
            }
            return result
        }
        fun getToken() : String {
            skipWs()
            var result = ""
            while (good()) {
                val ch = string[index]
                if (ch.isLetterOrDigit() || ch in "+-.") {
                    result += ch
                    ++index
                } else {
                    break
                }
            }
            return result
        }
        fun throwJson(msg: String) {
            val snippet = string.subSequence(maxOf(0, index-7), minOf(string.length, index+7))
            throw JsonException("$msg near position $index \"${snippet}\"")
        }
    }
    private var stringVal : String? = null
    private var intVal : Int? = null
    private var boolVal : Boolean? = null
    private var floatVal : Double? = null
    private var arrayVal : MutableList<JsonObject>? = null
    private var dictVal : MutableMap<String, JsonObject>? = null
    override fun asString() = stringVal ?: ""
    override fun asInt() = intVal ?: stringVal?.toIntOrNull() ?: 0
    override fun asBoolean() = boolVal ?: false
    override fun asFloat() = floatVal ?: 0.0
    override fun asArray() = arrayVal ?: listOf()
    override fun asDict() = dictVal ?: mapOf()
    override fun isString() = (stringVal != null)
    override fun isOnlyString() = (stringVal != null && intVal == null && floatVal == null && boolVal==null)
    override fun isInt() = (intVal != null)
    override fun isBoolean() = (boolVal != null)
    override fun isFloat() = (floatVal != null)
    override fun isArray() = (arrayVal != null)
    override fun isDict() = (dictVal != null)
    override fun isNull() = (stringVal==null && arrayVal==null && dictVal==null)
    override fun get(key: String): JsonObject? = if (isDict()) dictVal!![key] else null
    override fun get(index: Int): JsonObject? = if (isArray()) arrayVal!![index] else null
    override fun toMap(): Map<String, String> =
        if (isDict()) {
            dictVal!!.map{Pair(it.key, it.value.toSimpleString())}.toMap()
        } else {
            mapOf()
        }
    override fun toSimpleString() =
        when {
            isFloat() -> floatVal!!.toString()
            isInt() -> intVal!!.toString()
            isBoolean() -> boolVal!!.toString()
            isString() -> stringVal!!
            else -> ""
        }
    override fun toString() = toString(2, "")
    override fun toString(indent: Int, prefix: String): String {
        fun nl() = if (indent>0) "\n" else ""
        val myPrefix = prefix + " ".repeat(indent)
        fun toStr(v: JsonObject) = v.toString(indent, myPrefix)
        fun eToStr(s: String) = "\"${s}\":${" ".repeat(minOf(1,indent))}${dictVal!![s]!!.toString(indent, myPrefix)}"
        return when {
            isFloat() -> floatVal!!.toString()
            isInt() -> intVal!!.toString()
            isBoolean() -> boolVal!!.toString()
            isString() -> "\"${stringVal!!
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")}\""
            isArray() -> {
                val content = arrayVal!!.joinToString(",${nl()}${myPrefix}") { toStr(it) }
                "[${nl()}${myPrefix}${content}${nl()}${prefix}]"
            }
            isDict() -> {
                val content = dictVal!!.keys.joinToString(",${nl()}${myPrefix}") { eToStr(it) }
                "{${nl()}${myPrefix}${content}${nl()}${prefix}}"
            }
            else -> "null"
        }
    }
    override fun parse(string: String): JsonObjectImpl {
        val reader = Reader(string)
        parseOne(reader)
        if (reader.good()) {
            reader.throwJson("unexpected trailing characters")
        }
        return this
    }
    private fun parseOne(reader : Reader) : JsonObjectImpl {
        while (reader.good()) {
            val ch = reader.skipWs()
            if (reader.skipChar('{')) {
                dictVal = mutableMapOf()
                while (reader.good()) {
                    if (reader.skipChar('}')) {
                        break
                    }
                    val key = reader.getStr()
                    if (key != null) {
                        if (!reader.skipChar(':')) {
                            reader.throwJson("expected ':'")
                        }
                        val v = JsonObjectImpl().parseOne(reader)
                        dictVal?.put(key, v)
                    } else {
                        reader.throwJson("key expected")
                    }
                    if (reader.skipChar('}')) {
                        break
                    } else if (!reader.skipChar(',')) {
                        reader.throwJson("expected ',' or '}'")
                    }
                }
                break
            } else if (reader.skipChar('[')) {
                arrayVal = mutableListOf()
                while (reader.good()) {
                    if (reader.skipChar(']')) {
                        break
                    }
                    val v = JsonObjectImpl().parseOne(reader)
                    arrayVal?.add(v)
                    if (reader.skipChar(']')) {
                       break
                   } else if (!reader.skipChar(',')) {
                       reader.throwJson("expected ',' or ']'")
                   }
                }
                break
            } else if (ch == '"') {
                stringVal = reader.getStr()
                break
            } else if (ch == null) {
                reader.throwJson("string terminates unexpectedly")
            } else {
                val v = reader.getToken()
                if (v.isNotEmpty()) {
                    if (v!="null") {
                        stringVal = v
                        when (v) {
                            "true" -> boolVal = true
                            "false" -> boolVal = false
                            else -> {
                                try {
                                    intVal = v.toInt()
                                } catch (exc: NumberFormatException) {
                                    try {
                                        floatVal = v.toDouble()
                                    } catch (exc: NumberFormatException) {
                                        reader.throwJson("invalid unquoted value")
                                    }
                                }
                            }
                        }
                    }
                    break
                } else {
                    reader.throwJson("illegal character")
                }
            }
        }
        return this
    }
    companion object {
        val escapes = mapOf(
            "n" to "\n",
            "t" to "\t",
            "r" to "\r",)
    }
}

fun Map<String,String>.toJson() =
    "{" + map{ (key, value) -> "\"$key\":\"$value\""}.joinToString(",") + "}"
