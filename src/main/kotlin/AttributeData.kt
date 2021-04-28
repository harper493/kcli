class AttributeData(val attributeMd: AttributeMetadata,
                    val value: String) {
    val name = attributeMd.name
    val displayName = attributeMd.displayName
    val displayValue = attributeMd.reformat(value)
    var history: List<HistoryValue>? = null; private set
    val hasHistory get() = history!=null

    override fun toString() = value
    fun toInt(): Int = attributeMd.type.convert(value).toInt()
    fun toFloat(): Double = attributeMd.type.convert(value).toFloat()
    fun historyReader() = HistoryReader(this)
    fun loadHistory(source: JsonObject) {
        history = source.asArray()
            .map { HistoryValue.read(attributeMd, it) }
            .sortedBy { it.time }
    }

    class HistoryValue(
        val attributeMd: AttributeMetadata,
        val time: Long,
        val value: Double
    ) {
        companion object {
            fun read(attributeMd: AttributeMetadata, source: JsonObject) =
                with(source.asArray()) {
                    HistoryValue(
                        attributeMd,
                        this[0].asString().toLongOrNull() ?: 0,
                        this[1].asString().toDoubleOrNull() ?: 0.0
                    )
                }
        }
    }

    class HistoryReader(val data: AttributeData) {
        val attributeMd = data.attributeMd
        private var startTime: Long = 0
        private var lastTime: Long = 0
        private var accumulator: Double = 0.0
        private var lastReturnedValue: Double? = null
        private var accumulated: Boolean = false
        private val iter = data.history?.iterator()
        private var nextValue: HistoryValue? = if (iter?.hasNext() == true) iter.next() else null
        private var prevValue: HistoryValue? = null

        private fun accumulate() =
            also {
                if (!accumulated) {
                    val interval = (nextValue!!.time - prevValue!!.time).toDouble()
                    accumulator += nextValue!!.value * interval
                    accumulated = true
                }
            }

        private fun getValue() =
            (if (data.attributeMd.type.isCounter())
                with (nextValue!!.value) {
                    this - (lastReturnedValue ?: this)
                        .also { lastReturnedValue = this }
                }
            else if (data.attributeMd.total == "none"
                || prevValue == null)
                nextValue!!.value
            else accumulator / ((nextValue?.time ?: prevValue!!.time) - startTime).toDouble()
                .let { if (it==0.0) 1.0 else it})
                .also {
                    start()
                }

        private fun reload() = also {
            prevValue = nextValue
            nextValue = if (iter?.hasNext() == true) iter.next() else null
            accumulated = false
        }

        private fun start() = also {
            accumulator = 0.0
            startTime = nextValue?.time ?: 0
        }

        fun getNext(): Pair<Long, Double>? =
            nextValue?.let { Pair(nextValue!!.time, nextValue!!.value) }.also { reload() }

        fun exhausted() = nextValue==null

        fun nextTime() = nextValue?.time

        private fun makeResult(requestTime: Long, timeSource: HistoryValue) =
            run {
                lastTime = requestTime
                HistoryValue(attributeMd, timeSource.time, getValue())
            }

        fun getAt(time: Long): HistoryValue? {
            val myTime = if (time>0) time else nextValue?.time ?: prevValue?.time ?: 0
            while (true) {
                when {
                    nextValue!=null && prevValue!=null ->
                        when {
                            myTime <= prevValue!!.time ->
                                return null
                            lastTime >= prevValue!!.time && myTime < nextValue!!.time ->
                                return null
                            myTime == nextValue!!.time -> {
                                accumulate()
                                return makeResult(myTime, nextValue!!).also {
                                    start()
                                    reload() }
                            }
                            myTime < nextValue!!.time ->
                                return makeResult(myTime, prevValue!!).also {
                                    start()
                                    accumulate()
                                    reload()
                                }
                            else ->
                                accumulate().reload()
                        }
                    nextValue!=null && prevValue==null && (myTime >= nextValue!!.time) -> {
                        return makeResult(myTime, nextValue!!).also {
                            start()
                            reload()
                        }
                    }
                    nextValue!=null && prevValue==null && (myTime < nextValue!!.time) ->
                        reload()
                    nextValue==null && (prevValue==null || (myTime < prevValue!!.time)) ->
                        return null
                    nextValue==null && prevValue!=null && (myTime >= prevValue!!.time) ->
                        return makeResult(myTime, prevValue!!)
                            .also{ reload() }
                }
            }
        }

    }
}

