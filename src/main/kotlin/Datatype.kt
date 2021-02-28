import kotlin.math.pow

// import com.sun.org.apache.xpath.internal.operations.Bool
//import kotlin.reflect.full.cast

abstract class Datatype (
    val name: String,
    val description: String,
    val formatter: (value: Any) -> String,
) {
    abstract fun convert(s: String): GenericVariable
    open fun validate(value: String): Boolean { return true }
    open fun isNumeric(): Boolean = false
    open fun isClassType(): Boolean = false
    open fun hasNull(): Boolean = false
    open fun getClass(): ClassMetadata? = null
    fun validateCheck(value: String) {
        if (!validate(value)) {
            throw CliException("invalid value '$value' for type '$name'")
        }
    }
    companion object {
        private var types = mutableMapOf<String, Datatype>()
        operator fun get(index: String): Datatype {
            return types[index] ?: types["string"]!!
        }
        fun makeType(name: String): Datatype {
            lateinit var result: Datatype
            if (name in types) {
                result = types[name]!!
            } else {
                val ss = name.split(":")
                if (ss.size > 1) {
                    when (ss[0]) {
                        "opt" -> result = OptDatatype(name, makeType(ss[1]))
                        "set" -> result = SetDatatype(name, makeType(ss[1]))
                        "list" -> result = ListDatatype(name, makeType(ss[1]))
                        else -> result = StringDatatype(name)
                    }
                } else {
                    result = if (Metadata.getClass(name) == null) {
                        StringDatatype(name)
                    } else {
                        ClassDatatype(name)
                    }
                }
                types[name] = result
            }
            return result
        }

        fun addType(t: Datatype) {
            types[t.name] = t
        }

        fun regexValidator(value: String, rx: String): Boolean {
            return Regex(rx).matchEntire(value) != null
        }

        fun toInt(value: String): Int {
            return if (value.startsWith("0x", ignoreCase=true)) {
                    value.drop(2).toInt(16)
                } else {
                    value.toInt()
                }
        }

        fun toFloat(value: String): Double {
            val multipliers = mapOf('k' to 3, 'm' to 6, 'g' to 9, 't' to 12)
            var mult = 0
            var v = value
            if (value.last() in "kKmMgGtT") {
                v = v.dropLast(1)
                mult = multipliers[value.last().toLowerCase()] ?: 0
            }
            return v.toDouble() * 10.0.pow(mult.toDouble())
        }

        fun load() {
            addType(IntDatatype("int"))
            addType(FloatDatatype("float"))
            addType(IntDatatype("counter", properties="counter"))
            addType(IntDatatype("byte_counter", properties="counter"))
            addType(StringDatatype("string"))
            addType(StringDatatype("text", description="human-readable text"))
            addType(FloatDatatype("rate", description="traffic rate in Kbit/sec",
                            converter={ toFloat(it) * if ((it.lastOrNull()?:' ').isLetter()) 1.0 else 0.001 }))
        }
    }
}

fun conversionValidator(value: String, dt: Datatype) : Boolean {
    return try {
        dt.convert(value)
        true
    } catch (exc: Exception) {
        false
    }
}

open class TypedDatatype<T: Comparable<T>>(
    name: String,
    description: String,
    formatter: (value: Any) -> String = { it.toString() },
    val validator: ((value: String) -> Boolean)? = null,
    val properties: String = "",
    val converter: (String)->T,
    val gvFactory: (String)->GenericVariable,
    val wrapper: (String, Int)->List<String> = { value, width -> value.chunked(width) },
) : Datatype (name, description, formatter)  {
    override fun convert(s: String): GenericVariable {
        return gvFactory(s)
    }
    override fun validate(value: String) = if (validator==null) true else validator!!(value)
}

open class StringDatatype(
    name: String,
    description: String = name,
    formatter: (value: Any) -> String = { it as String },
    validator: ((value: String) -> Boolean)? = null,
    properties: String = "",
    wrapper: (String, Int)->List<String> = { value, width -> value.chunked(width) },
) : TypedDatatype<String>(name,
    description,
    formatter,
    validator,
    properties,
    { it },
    { TypedGenericVariable(it) },
    wrapper,
) {
    override fun hasNull(): Boolean = true
}

open class IntDatatype(
    name: String,
    description: String = name,
    formatter: (value: Any) -> String = { it.toString() },
    validator: ((value: String) -> Boolean)? = null,
    properties: String = "",
    converter: (String)->Int = { toInt(it) },
    wrapper: (String, Int)->List<String> = { value, width -> value.chunked(width) },
) : TypedDatatype<Int>(name,
    description,
    formatter,
    validator,
    "arithmetic $properties",
    converter,
    { NumericGenericVariable( toInt(it), toInt(it).toDouble()) },
    wrapper,
) {
    override fun validate(value: String) =
        if (validator==null) conversionValidator(value, this)
        else validator!!(value)

    override fun isNumeric(): Boolean = true
}

open class FloatDatatype(
    name: String,
    description: String = name,
    formatter: (value: Any) -> String = { it.toString() },
    validator: ((value: String) -> Boolean)? = null,
    properties: String = "",
    converter: (String)->Double = { toFloat(it) },
    wrapper: (String, Int)->List<String> = { value, width -> value.chunked(width) },
) : TypedDatatype<Double>(name,
    description,
    formatter,
    validator,
    "arithmetic $properties",
    converter,
    { NumericGenericVariable( toFloat(it), toFloat(it)) },
    wrapper,
) {
    override fun validate(value: String) =
        if (validator==null) conversionValidator(value, this)
        else validator!!(value)

    override fun isNumeric(): Boolean = true
}

class ClassDatatype(
    name: String,
): StringDatatype(name) {
    override fun isClassType(): Boolean = true
    override fun getClass(): ClassMetadata? = Metadata.getClass(name)
}

open class CompoundDatatype(
    name: String,
    val baseType: Datatype
): StringDatatype(name)

class OptDatatype(
    name: String,
    baseType: Datatype
): CompoundDatatype(name, baseType) {
    override fun validate(value: String) = value.isEmpty() || baseType.validate(value)
}

class SetDatatype(
    name: String,
    baseType: Datatype
): CompoundDatatype(name, baseType) {
    override fun validate(value: String) =
        value.isEmpty()
                || (value.first() in listOf("+", "-") && baseType.validate(value.drop(1)))
                || (value.split(",").fold(true){acc, v ->acc && baseType.validate(v) })
}

class ListDatatype(
    name: String,
    baseType: Datatype
): CompoundDatatype(name, baseType) {
    override fun validate(value: String) =
        value.isEmpty()
                || (value.split(",").fold(true){acc, v -> acc && baseType.validate(v) })
}

