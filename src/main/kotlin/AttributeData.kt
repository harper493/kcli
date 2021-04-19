

class AttributeData(val attributeMd: AttributeMetadata,
                    val value: String) {
    val name = attributeMd.name
    val displayName = attributeMd.displayName
    val displayValue = attributeMd.reformat(value)
    var history: List<HistoryValue>? = null; private set

    override fun toString() = value
    fun toInt(): Int = attributeMd.type.convert(value).toInt()
    fun toFloat(): Double = attributeMd.type.convert(value).toFloat()
    fun historyReader() = HistoryReader(this)
    fun loadHistory(source: JsonObject) {
        history = source.asArray()
            .map{ HistoryValue.read(it) }
            .sortedBy{ it.time }
    }

    class HistoryValue(
        val time: Long,
        val value: Double
    ) {
        companion object {
            fun read(source: JsonObject) =
                with(source.asArray()) {
                    HistoryValue(this[0].asString().toLongOrNull() ?: 0,
                        this[1].asString().toDoubleOrNull() ?: 0.0)
                }
        }
    }

    class HistoryReader(val data: AttributeData) {
        private var lastTime: Long = 0
        private var startTime: Long = 0
        private var accumulator: Double = 0.0
        private val iter = data.history?.iterator()

        fun getAt(time: Long): Double? {
            while (iter?.hasNext() ?: false) {
                val hv = iter!!.next()
                val value = hv.value
                val interval = (hv.time - lastTime).toDouble()
                if (hv.time > time ||
                    (lastTime > 0
                            && interval / (time - lastTime).toDouble() > 0.9)) {
                    lastTime = hv.time
                    return when {
                        data.attributeMd.type.isCounter()
                                || data.attributeMd.total == "none"
                                || startTime == 0L -> {
                            startTime = hv.time
                            value
                        }
                        else -> {
                            ((value * interval + accumulator) / (hv.time - startTime)).apply  {
                                startTime = hv.time
                                accumulator = 0.0
                            }
                        }
                    }
                } else {
                    lastTime = hv.time
                    accumulator += hv.value * interval
                }
            }
            return null
        }
    }
}

