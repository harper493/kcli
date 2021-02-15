interface JsonObject {
    fun getString(): String?
    fun getInt(): Int?
    fun getBoolean(): Boolean?
    fun getFloat(): Double?
    fun getArray(): MutableList<JsonObject>?
    fun getDict(): Map<String, JsonObject>?
    fun isString(): Boolean
    fun isOnlyString(): Boolean
    fun isInt(): Boolean
    fun isNull(): Boolean
    fun isBoolean(): Boolean
    fun isFloat(): Boolean
    fun isArray(): Boolean
    fun isDict(): Boolean
    fun parse(string: String): JsonObject
    fun toString(indent:Int = 2, prefix:String = ""): String
    operator fun get(key: String): JsonObject?
    operator fun get(index: Int): JsonObject?
    companion object {
        fun load(value: String): JsonObject {
            return JsonObject_().parse(value)
        }
    }
}

class JsonException(text: String) : Exception(text)

class JsonObject_(): JsonObject {
    class Reader(
        val string : String
    ) {
        var index: Int = 0
        fun good() : Boolean {
            return index < string.length
        }
        fun skipWs() : Char? {
            while (good() && (string[index] in " \t\n")) {
                ++index
            }
            return if (good()) string[index] else null
        }
        fun backup() {
            if (index>0) {
                --index
            }
        }
        fun getChar() : Char? {
            ++index
            val result = skipWs()
            return result
        }
        fun skipChar(ch: Char) : Boolean {
            if (skipWs()==ch) {
                getChar()
                return true
            } else {
                return false
            }
        }
        fun getStr() : String? {
            var result = ""
            if (!skipChar('"')) {
                return null
            }
            var escape = false
            while (good()) {
                if (!escape && skipChar('\"')) {
                    return result
                } else if (!escape && skipChar('\\')) {
                    escape = true
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
            throw JsonException("${msg} near position ${index} \"${snippet}\"")
        }
    }
    var stringVal : String? = null
    var intVal : Int? = null
    var boolVal : Boolean? = null
    var floatVal : Double? = null
    var arrayVal : MutableList<JsonObject>? = null
    var dictVal : MutableMap<String, JsonObject>? = null
    override fun getString() = stringVal
    override fun getInt() = intVal
    override fun getBoolean() = boolVal
    override fun getFloat() = floatVal
    override fun getArray() = arrayVal
    override fun getDict() = dictVal
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
    override fun toString() = toString(2, "")
    override fun toString(indent: Int, prefix: String): String {
        fun nl() = if (indent>0) "\n" else ""
        var myPrefix = prefix + " ".repeat(indent)
        fun toStr(v: JsonObject) = v.toString(indent, myPrefix)
        fun eToStr(s: String) = "\"${s}\":${" ".repeat(minOf(1,indent))}${dictVal!![s]!!.toString(indent, myPrefix)}"
        if (isFloat()) {
            return floatVal!!.toString()
        } else if (isInt()) {
            return intVal!!.toString()
        } else if (isBoolean()) {
            return boolVal!!.toString()
        } else if (isString()) {
            return "\"${stringVal!!.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        } else if (isArray()) {
            val content = arrayVal!!.joinToString(",${nl()}${myPrefix}") { it -> toStr(it) }
            return "[${nl()}${myPrefix}${content}${nl()}${prefix}]"
        } else if (isDict()) {
            val content = dictVal!!.keys.joinToString(",${nl()}${myPrefix}") { it -> eToStr(it) }
            return "{${nl()}${myPrefix}${content}${nl()}${prefix}}"
        } else {
            return "null"
        }
    }
    override fun parse(value: String): JsonObject_ {
        var reader = Reader(value)
        parseOne(reader)
        if (reader.good()) {
            reader.throwJson("unexpected trailing characters")
        }
        return this
    }
    private fun parseOne(reader : Reader) : JsonObject_ {
        while (reader.good()) {
            var ch = reader.skipWs()
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
                        val v = JsonObject_().parseOne(reader)
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
                    val v = JsonObject_().parseOne(reader)
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
                        if (v=="true") {
                            boolVal = true
                        } else if (v=="false") {
                            boolVal = false
                        } else {
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
                    break
                } else {
                    reader.throwJson("illegal character")
                }
            }
        }
        return this
    }
}