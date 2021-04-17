import java.time.LocalDateTime
import java.time.Month
import java.time.DayOfWeek

/*
* This file contains miscellaneous classes, functions etc relating to time
 */

/*
Convert a date/time to midnight of the same day
 */

fun LocalDateTime.atFirstOfMonth() =
    atMidnight().let{ it.minusDays(it.dayOfMonth.toLong() - 1)}

fun LocalDateTime.atMidnight() =
    withHour(0).atHour()

fun LocalDateTime.atHour() =
    withMinute(0).withSecond(0).withNano(1)

fun KeywordList.Companion.months(fn: (Month)->Unit) =
    KeywordList(*Month.values().map {
        KeywordFn(it.name.toLowerCase(), { fn(it) })
    }.toTypedArray())

fun KeywordList.Companion.days(fn: (DayOfWeek)->Unit) =
    KeywordList(*DayOfWeek.values().map {
        KeywordFn(it.name.toLowerCase(), { fn(it) })
    }.toTypedArray())

enum class IntervalType {
    hour,
    day,
    week,
    month
}

class DateInterval(
    val howMany: Int,
    val what: IntervalType
) {

    fun subtractFrom(dt: LocalDateTime) =
        when (what) {
            IntervalType.hour -> dt.atHour().minusHours(howMany.toLong())
            IntervalType.day -> dt.atMidnight().minusDays(howMany.toLong())
            IntervalType.week -> dt.atMidnight().minusDays(howMany.toLong() * 7)
            IntervalType.month -> dt.atMidnight().minusMonths(howMany.toLong())
        }
    fun addTo(dt: LocalDateTime) =
        when (what) {
            IntervalType.hour -> dt.atHour().plusHours(howMany.toLong())
            IntervalType.day -> dt.atMidnight().plusDays(howMany.toLong())
            IntervalType.week -> dt.atMidnight().plusDays(howMany.toLong() * 7)
            IntervalType.month -> dt.atMidnight().plusMonths(howMany.toLong())
        }
    override fun toString() =
        "$howMany ${what.toString().makePlural(howMany)}"

    companion object {
        fun read(parser: Parser, n: Int?=null, endOk: Boolean=false): DateInterval? {
            var nn = n ?: 1
            lateinit var interval: IntervalType
            val keys = KeywordList(*IntervalType.values().map {
                KeywordFn(it.name.toLowerCase().makePlural(), { interval = it })
            }.toTypedArray())
            println(n)
            with (parser.findKeyword(keys, missOk=(n==null), endOk=endOk)) {
                if (this==null) {
                    nn = parser.nextToken(tokenType=Parser.TokenType.ttInt, endOk=endOk)
                        ?.toInt() ?: return null
                    parser.findKeyword(keys)!!()
                } else {
                    this()
                }
                return DateInterval(nn, interval)
            }
        }
    }
}

/*
Parse a date and time
 */

fun String.parseDateTime(value: String, pastOnly: Boolean = false): LocalDateTime {
    val now = LocalDateTime.now()
    val rx = Regex("""(?:(?:(\d+)-)?(\d+)-(\d+)T?)?(?:(\d+):(\d+))?""")
    with(rx.matchEntire(value)?.groupValues) {
        this?.let {
            val year = it[1].toIntOrNull() ?: now.year
            val month = it[2].toIntOrNull() ?: now.month.value
            val day = it[3].toIntOrNull() ?: now.dayOfMonth
            val hour = it[4].toIntOrNull() ?: 0
            val minute = it[5].toIntOrNull() ?: 0
            val t1 = LocalDateTime.of(year, month, day, hour, minute)
            if (t1 > now && pastOnly) {
                if (it[1].isEmpty()) {
                    if (it[2].isEmpty()) {
                        return t1.minusDays(1)
                    } else {
                        return t1.minusYears(1)
                    }
                } else {
                    throw CliException("time $value must be in the past")
                }
            }
            return t1
        }
    }
    throw CliException("invalid time '$value' should be in form yyyy-mm-dd[Thh:mm]")
}

fun getHistoryTime(parser: Parser) =
    run {
        val now = LocalDateTime.now()
        val midnight = now.atMidnight()
        var month = -1
        var day = -1
        with (parser.findKeyword(KeywordList.days({ day = it.value })
            .add(KeywordList.months{ month = it.value })
            .addKeys("yesterday", "last"), missOk=true)) {
            if (this?.isFunction() ?: false) {
                this!!()
            }
            when {
                this==null -> {                    // try for a number or date
                    val t = parser.nextToken(tokenType = Parser.TokenType.ttNonBlank)!!
                    val n = t.toIntOrNull()
                    if (n != null) {
                        DateInterval.read(parser, n)!!.subtractFrom(now)
                    } else {
                        t.parseDateTime(t, pastOnly = true)
                    }
                }
                day >= 0 -> {
                    val today = now.dayOfWeek.value
                    val dayOffset = (today - day) + (if (day > today) 7 else 0)
                    midnight.minusDays(dayOffset.toLong())
                }
                month >= 0 -> {
                    val thisMonth = midnight.month.value
                    val monthOffset = (thisMonth - month) + (if (month > thisMonth) 12 else 0)
                    midnight.atFirstOfMonth().minusMonths(monthOffset.toLong())
                }
                this.asString()=="yesterday" ->
                    midnight.minusDays(1)
                this.asString()=="last" ->
                    DateInterval.read(parser, 1)!!.subtractFrom(now)
                else ->
                    midnight       // impossible
            }
        }
    }





