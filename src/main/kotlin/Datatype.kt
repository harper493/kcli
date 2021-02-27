import kotlin.math.pow

// import com.sun.org.apache.xpath.internal.operations.Bool
//import kotlin.reflect.full.cast

interface Datatype {
    val name: String
    val description: String
    val formatter: (value: Any) -> String
    fun convert(s: String): GenericVariable
    fun validate(value: String): Boolean { return true }
    fun isNumeric(): Boolean = false
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
    override val name: String,
    override val description: String,
    override val formatter: (value: Any) -> String = { it.toString() },
    val validator: ((value: String) -> Boolean)? = null,
    val properties: String = "",
    val converter: (String)->T,
    val gvFactory: (String)->GenericVariable,
    val wrapper: (String, Int)->List<String> = { value, width -> value.chunked(width) },
) : Datatype {
    override fun convert(s: String): GenericVariable {
        return gvFactory(s)
    }
    override fun validate(value: String) = if (validator==null) true else validator!!(value)
}

class StringDatatype(
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
)

class IntDatatype(
    name: String,
    description: String = name,
    formatter: (value: Any) -> String = { it.toString() },
    validator: ((value: String) -> Boolean)? = null,
    properties: String = "",
    converter: (String)->Int = { Datatype.toInt(it) },
    wrapper: (String, Int)->List<String> = { value, width -> value.chunked(width) },
) : TypedDatatype<Int>(name,
    description,
    formatter,
    validator,
    "arithmetic $properties",
    converter,
    { NumericGenericVariable( Datatype.toInt(it), Datatype.toInt(it).toDouble()) },
    wrapper,
) {
    override fun validate(value: String) =
        if (validator==null) conversionValidator(value, this)
        else validator!!(value)

    override fun isNumeric(): Boolean = true
}

class FloatDatatype(
    name: String,
    description: String = name,
    formatter: (value: Any) -> String = { it.toString() },
    validator: ((value: String) -> Boolean)? = null,
    properties: String = "",
    converter: (String)->Double = { Datatype.toFloat(it) },
    wrapper: (String, Int)->List<String> = { value, width -> value.chunked(width) },
) : TypedDatatype<Double>(name,
    description,
    formatter,
    validator,
    "arithmetic $properties",
    converter,
    { NumericGenericVariable( Datatype.toFloat(it), Datatype.toFloat(it)) },
    wrapper,
) {
    override fun validate(value: String) =
        if (validator==null) conversionValidator(value, this)
        else validator!!(value)

    override fun isNumeric(): Boolean = true
}

