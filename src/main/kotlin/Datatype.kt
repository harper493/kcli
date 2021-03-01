import kotlin.math.pow

// import com.sun.org.apache.xpath.internal.operations.Bool
//import kotlin.reflect.full.cast

class Validator(val regex: String?=null,
                val prefixRx: String?=null,
                val fn: ((String,Boolean)->Boolean)?=null) {
    fun validate(value: String): Boolean =
        when {
            fn!=null -> fn.invoke(value, true)
            regex!=null -> Regex(regex).matches(value)
            else -> true
        }
    fun validatePfx(value: String): Boolean =
        when {
            prefixRx!=null -> Regex(prefixRx).matches(value)
            fn!=null -> fn.invoke(value, false)
            regex!=null -> Regex(regex).matches(value)
            else -> true
        }
    val isNull: Boolean = fn==null && regex==null
}

abstract class Datatype (
    val name: String,
    val description: String,
    val formatter: (value: Any) -> String,
    val validator: Validator,
    val unit: String,
) {
    abstract fun convert(s: String): GenericVariable
    open fun validate(value: String): Boolean = validator.validate(value)
    open fun validatePfx(value: String): Boolean = validator.validatePfx(value)
    open fun isClassType(): Boolean = false
    open fun hasNull(): Boolean = false
    open fun getClass(): ClassMetadata? = null
    open fun isNumeric(): Boolean = false
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
                result = if (ss.size > 1) {
                    when (ss[0]) {
                        "opt" -> OptDatatype(name, makeType(ss[1]))
                        "set" -> SetDatatype(name, makeType(ss[1]))
                        "list" -> ListDatatype(name, makeType(ss[1]))
                        else -> StringDatatype(name)
                    }
                } else {
                    if (Metadata.getClass(name) == null) {
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
        
        fun addTypes(vararg tt: Datatype) {
            for (t in tt) {
                addType(t)
            }
        }

        fun regexValidator(value: String, rx: String): Boolean {
            return Regex(rx).matchEntire(value) != null
        }

        fun toInt(value: String) =
            if (value.startsWith("0x", ignoreCase=true)) {
                value.drop(2).toInt(16)
            } else {
                value.toInt()
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
        
        fun toBoolean(value: String): Boolean = 
            when {
                "true".startsWith(value, ignoreCase = true) -> true
                "false".startsWith(value, ignoreCase = true) -> false
                value.toIntOrNull() ?: 1 == 0 -> false
                value.toIntOrNull() ?: 0 != 0 -> true
                else -> throw SyntaxException("invalid value for boolean '$value'")
            }

        fun validateIpV4Address(value: String): Boolean =
            value.split(".").run{
                size==4
                        && fold(true){sofar, it -> sofar && it.toIntOrNull()?:256 in (0..255)}
            }

        fun validateMacAddress(value: String): Boolean =
            value.split(":").run{
                size==6
                        && fold(true){sofar, it -> sofar
                        && it.length==2
                        && it.toIntOrNull(16)?:0x100 in (0..0xff)}
            }

        fun validateIpAddress(value: String): Boolean =
            if (":" in value) {
                value.split(":").run {
                    size <= 8
                    && filter { it.isEmpty() }.size == (if (size < 8) 1 else 0)
                    && filter { it.isNotEmpty() }
                        .fold(true) { sofar, it -> sofar && it.toIntOrNull(16) ?: 0x10000 in (0..0xffff) }
                }
            }
            else validateIpV4Address(value)

        fun validateIpSubnet(value: String): Boolean {
            val s = value.split("/")
            val mask = s.getOrNull(1)?.toIntOrNull()?:0
            val maxMask = if (":" in s[0]) 128 else 32
            return validateIpAddress(s[0]) && mask < maxMask
        }

        fun load() = addTypes(
            IntDatatype("int"),
            FloatDatatype("float"),
            IntDatatype("counter", properties="counter"),
            IntDatatype("byte_counter", properties="counter"),
            StringDatatype("string"),
            StringDatatype("text", description="human-readable text"),
            FloatDatatype("percentage", unit="%"),
            IntDatatype("uid"),
            FloatDatatype("rate", description="traffic rate in Kbit/sec",
                converter={ toFloat(it) * if ((it.lastOrNull()?:' ').isLetter()) 1.0 else 0.001 }),
            StringDatatype("mac_address", description="MAC address",
                validator = Validator(fn={ value, _ -> validateMacAddress(value) },
                    prefixRx="""[\w:]+""")),
            TypedDatatype<Boolean>("bool",
                converter={ toBoolean(it) },
                gvFactory = { TypedGenericVariable(toBoolean(it))}),
            StringDatatype("ip_address", description="IP address",
                validator = Validator(fn={ value, _ -> validateIpAddress(value) },
                                      prefixRx="""[\w:]+|[0-9\.]+""")),
            StringDatatype("ip_subnet", description="IP address",
                validator = Validator(fn={ value, _ -> validateIpSubnet(value) },
                    prefixRx="""[\w:/]+|[0-9\./]+""")),
            StringDatatype("ipv4_address", description="IPV4 address",
                validator = Validator(fn={ value, _ -> validateIpV4Address(value) },
                    prefixRx="""[\d\.]+""")),
        )
    }
}
/*
        self._t('enum', enum=True, precision=None)
        self._t('time', descriptive='absolute time in format yyyy-mm-ddThh[:mm[:ss]] or +/- a valid duration for delta to present time', sortable=True, \
                validation=r'\d{4}-\d{1,2}-\d{1,2}(T\d{1,2}(:\d{1,2}(:\d{1,2}(\.\d{1,6})?)?)?)?|[+-](\d+:)?(\d+:)?(\d+)(\.\d+)?(d|D|h|H|m|M|s|S|u|U|[mM][sS])?',
                extra_chars=':.', precision=None)
        self._t('duration', sortable=True, arithmetic=True, converter=float, descriptive= \
                'time duration, as number of seconds, or followed by h/m/s/ms/u for ' + \
                'hours, minutes, seconds, milliseconds, microseconds, or in form hh:mm:ss.sss',
                validation=r'(\d+:)?(\d+:)?(\d+)(\.\d+)?(d|D|h|H|m|M|s|S|u|U|[mM][sS])?',
                extra_chars=':.', precision=None)
        self._t('duration_s', sortable=True, arithmetic=True, converter=float, descriptive= \
                'time in seconds',
                validation=r'(\d+:)?(\d+:)?(\d+)(\.\d+)?(d|D|h|H|m|M|s|S|u|U|[mM][sS])?',
                extra_chars=':.', precision=3)
        self._t('duration_ms', sortable=True, arithmetic=True, converter=float, descriptive= \
                'time in milliseconds',
                validation=r'(\d+:)?(\d+:)?(\d+)(\.\d+)?(d|D|h|H|m|M|s|S|u|U|[mM][sS])?', formatter='%.3f',
                extra_chars=':.', precision=3)
        self._t('duration_us', sortable=True, arithmetic=True, converter=float, descriptive= \
                'time in microseconds',
                validation=r'(\d+:)?(\d+:)?(\d+)(\.\d+)?(d|D|h|H|m|M|s|S|u|U|[mM][sS])?',
                extra_chars=':.', precision=0)
        self._t('ternary', descriptive='true, false or x for either', \
                validation='(t|T|f|F|x|x).*', precision=None)
        self._t('ipv4_address', validation=r'\d+\.\d+\.\d+\.\d+', sortable=True, blank_ok=True,
                extra_chars='.', precision=None)
        self._t('ipv4_subnet',
                descriptive='ipv4 subnet, an address possibly followed by mask length, e.g. 1.2.3.0/24', \
                validation=r'\d+\.\d+\.\d+\.\d+(/\d+)?', blank_ok=True,
                extra_chars='/.', precision=None)
        self._t('ip_address', validation=r'[0-9a-fA-F:\.]+', sortable=True, blank_ok=True,
                extra_chars=':.', precision=None)
        self._t('ip_subnet',
                descriptive='ip subnet, an IPv4 or IPv6 address possibly followed by mask length, e.g. 1.2.3.0/24', \
                validation=r'[0-9a-fA-F:\.]+(?:/\d+)?|[a-zA-Z]+', blank_ok=True, sortable=True,
                extra_chars=':./', precision=None)
        self._t('address_range', descriptive='IP address or subnet, or two IP addresses separated by -',
                validation=r'[0-9a-fA-F:\.]+(?:(?:/\d+)?|(?:-[0-9a-fA-F:\.]+)?)',
                extra_chars=':.-/', precision=None)
        self._t('l4_port_number', descriptive='TCP or UDP port number', sortable=True, precision=None)
        self._t('port_range', descriptive='tcp/udp port or range, e.g. 53, 21-22', \
                validation=r'\d+(-\d+)?',
                extra_chars='-', precision=None)
        self._t('mac_address', validation= \
                "[0-9a-fA-F]{2}:[0-9a-fA-F]{2}:[0-9a-fA-F]{2}:" + \
                "[0-9a-fA-F]{2}:[0-9a-fA-F]{2}:[0-9a-fA-F]{2}",
                extra_chars=':', precision=None)
        self._t('rate', descriptive='rate in Kbit/sec or followed by M or G for Mbit/sec, Gbit/sec',
                validation=r'\d+(\.\d+)?(m|M|g|G|k|K|t|T)?', formatter='%.1f',
                sortable=True, arithmetic=True, suppress_range=True, converter=float, rate=True,
                accumulator=zero_accumulator, precision=3)
        self._t('item_rate', descriptive='event rate per second',
                validation=r'\d+(\.\d+)?(m|M|g|G|k|K|t|T)?', formatter='%.1f',
                sortable=True, arithmetic=True, suppress_range=True, converter=float, rate=True,
                accumulator=zero_accumulator, precision=3)
        self._t('time_of_day', descriptive='time of day in form hh, hh:mm or hh:mm:ss (24 hour)',
                validation=r'\d{1,2}(:\d{1,2}(:\d{1,2})?)?', sortable=True,
                extra_chars=':.', precision=None)
        self._t('weekday', descriptive='day of week', enum=True, sortable=True, precision=None)
        self._t('location', descriptive='geographic location expressed as (latitude,longitude)',
                validation=r'\(?[0-9.+-]+,[0-9.+-]+\)?')
        self._t('location_bound', descriptive='geographic rectangle',
                validation='\(\([0-9.+-]+,[0-9.+-]+\),\([0-9.+-]+,[0-9.+-]+\)\)')
        self._t('object_filter', blank_ok=True, precision=None)
        self._t('object_name', descriptive='name of object', sortable=True,
                extra_chars='^', precision=None)
        self._t('password', extra_chars='^', precision=None)
        self._t('unknown')

 */
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
    description: String = name,
    formatter: (value: Any) -> String = { it.toString() },
    validator: Validator=Validator(),
    unit: String = "",
    val properties: String = "",
    val converter: (String)->T,
    val gvFactory: (String)->GenericVariable,
    val wrapper: (String, Int)->List<String> = { value, width -> value.chunked(width) },
) : Datatype (name, description, formatter, validator, unit)  {
    override fun convert(s: String): GenericVariable {
        return gvFactory(s)
    }
}

open class StringDatatype(
    name: String,
    description: String = name,
    formatter: (value: Any) -> String = { it as String },
    validator: Validator=Validator(),
    unit: String = "",
    properties: String = "",
    wrapper: (String, Int)->List<String> = { value, width -> value.chunked(width) },
) : TypedDatatype<String>(name,
    description,
    formatter,
    validator,
    unit,
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
    validator: Validator=Validator(),
    unit: String = "",
    properties: String = "",
    converter: (String)->Int = { toInt(it) },
    wrapper: (String, Int)->List<String> = { value, width -> value.chunked(width) },
) : TypedDatatype<Int>(name,
    description,
    formatter,
    validator,
    unit,
    "arithmetic $properties",
    converter,
    { NumericGenericVariable( toInt(it), toInt(it).toDouble()) },
    wrapper,
) {
    override fun validate(value: String) =
        if (validator.isNull) conversionValidator(value, this)
        else validator.validate(value)

    override fun isNumeric(): Boolean = true
}

open class FloatDatatype(
    name: String,
    description: String = name,
    formatter: (value: Any) -> String = { it.toString() },
    validator: Validator=Validator(),
    unit: String = "",
    properties: String = "",
    converter: (String)->Double = { toFloat(it) },
    wrapper: (String, Int)->List<String> = { value, width -> value.chunked(width) },
) : TypedDatatype<Double>(name,
    description,
    formatter,
    validator,
    unit,
    "arithmetic $properties",
    converter,
    { NumericGenericVariable( toFloat(it), toFloat(it)) },
    wrapper,
) {
    override fun validate(value: String) =
        if (validator.isNull) conversionValidator(value, this)
        else validator.validate(value)

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

