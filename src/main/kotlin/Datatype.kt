// import com.sun.org.apache.xpath.internal.operations.Bool
//import kotlin.reflect.full.cast

interface Datatype {
    val name: String
    val description: String
    val formatter: (value: Any) -> String
    fun convert(s: String): GenericVariable
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

        fun load() {
            Datatype.addType(
                TypedDatatype<Int>("int",
                    "integer",
                    { it.toString() },
                    { Datatype.regexValidator(it, "[-+]?\\d+|0x[0-9a-fA-F]") },
                    "arithmetic",
                    { it.toInt() },
                    { NumericGenericVariable<Int>(it.toInt(), it.toDouble()) }
                )
            )
            Datatype.addType(
                TypedDatatype<Int>("counter",
                    "counter",
                    { it.toString() },
                    { true },
                    "arithmetic counter",
                    { it.toInt() },
                    { NumericGenericVariable<Int>(it.toInt(), it.toDouble()) }
                )
            )
            Datatype.addType(
                TypedDatatype<Int>("byte_counter",
                    "counter",
                    { it.toString() },
                    { true },
                    "arithmetic counter",
                    { it.toInt() },
                    { NumericGenericVariable<Int>(it.toInt(), it.toDouble()) }
                )
            )
            Datatype.addType(
                TypedDatatype<String>("string",
                    "string",
                    { it as String },
                    { true },
                    "",
                    { it },
                    { TypedGenericVariable<String>(it) }
                )
            )
            Datatype.addType(
                TypedDatatype<Double>("rate",
                    "traffic rate",
                    { it.toString() },
                    { Datatype.regexValidator(it, "\\d+(\\.\\d+)?(m|M|g|G|k|K|t|T)?") },
                    "arithmetic suppress_range",
                    { it.toDouble() },
                    { NumericGenericVariable<Double>(it.toDouble(), it.toDouble()) }
                )
            )
        }
    }
}

class TypedDatatype<T: Comparable<T>>(
    val name_: String,
    val description_: String,
    val formatter_: (value: Any) -> String,
    val validator: (value: String) -> Boolean,
    val properties: String,
    val converter_: (String)->T,
    val gvFactory: (String)->GenericVariable
): Datatype {
    override val name: String get() = name_
    override val description: String get() = description_
    override val formatter: (value: Any) -> String get() = formatter_
    override fun convert(s: String): GenericVariable {
        return gvFactory(s)
    }
}

