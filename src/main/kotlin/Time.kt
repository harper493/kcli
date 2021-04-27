import java.time.*
import java.time.temporal.ChronoUnit

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
    withMinute(0).withSecond(0).withNano(0)

fun LocalDateTime.toNiceString() =
    toString().replace('T', ' ')

fun LocalDateTime.toUnix(): Long =
    run {
        val utcZone = ZoneId.of("UTC+0")
        val utcTime = ZonedDateTime.ofLocal(this,
            utcZone,
            ZoneOffset.ofHours(0))
        val base = ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, utcZone)
        val delta = base.until(utcTime, ChronoUnit.SECONDS)
        delta * 1000L
    }

fun LocalDateTime.setTime(time: LocalTime) =
    withHour(time.hour).withMinute(time.minute)

fun unixToLocalDateTime(unixTime: Long) =
    LocalDateTime.ofEpochSecond(unixTime/1000,
        (unixTime % 1000L).toInt(),
        ZoneId.systemDefault().getRules().getOffset(Instant.now()))

fun KeywordList.Companion.months(fn: (Month)->Unit) =
    KeywordList(*Month.values().map {
        KeywordFn(it.name.toLowerCase(), { fn(it) })
    }.toTypedArray())

fun KeywordList.Companion.days(fn: (DayOfWeek)->Unit) =
    KeywordList(*DayOfWeek.values().map {
        KeywordFn(it.name.toLowerCase(), { fn(it) })
    }.toTypedArray())

enum class IntervalType {
    minute,
    hour,
    day,
    week,
    month
}

class DateInterval(
    val howMany: Int,
    val what: IntervalType
) {

    fun addTo(dt: LocalDateTime) =
        with (howMany.toLong()) {
            when (what) {
                IntervalType.minute -> dt.plusMinutes(this)
                IntervalType.hour -> dt.atHour().plusHours(this)
                IntervalType.day -> dt.atMidnight().plusDays(this)
                IntervalType.week -> dt.atMidnight().plusDays(this * 7)
                IntervalType.month -> dt.atMidnight().plusMonths(this)
            }
        }
    fun subtractFrom(dt: LocalDateTime) =
        with (howMany.toLong()) {
            when (what) {
                IntervalType.minute -> dt.minusMinutes(this)
                IntervalType.hour -> dt.atHour().minusHours(this)
                IntervalType.day -> dt.atMidnight().minusDays(this)
                IntervalType.week -> dt.atMidnight().minusDays(this * 7)
                IntervalType.month -> dt.atMidnight().minusMonths(this)
            }
        }
    fun addTo(time: Long) =
        with (howMany.toLong()) {
            when (what) {
                IntervalType.minute -> time + this * 60000
                IntervalType.hour -> time + this * 60 * 60000
                IntervalType.day -> time + this * 24 * 60 * 60000
                IntervalType.week -> time + this * 7 * 24 * 60 * 60000
                IntervalType.month -> time + this * 31 *24 * 60 * 60000
            }
        }
    fun subtractFrom(time: Long) =
        with (howMany.toLong()) {
            when (what) {
                IntervalType.minute -> time - this * 60000
                IntervalType.hour -> time - this * 60 * 60000
                IntervalType.day -> time - this * 24 * 60 * 60000
                IntervalType.week -> time - this * 7 * 24 * 60 * 60000
                IntervalType.month -> time - this * 31 *24 * 60 * 60000
            }
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

fun String.parseDateTime(pastOnly: Boolean = false): LocalDateTime {
    val now = LocalDateTime.now()
    val rx = Regex("""(?:(?:(\d+)-)?(\d+)-(\d+)T?)?(?:(\d+):(\d+))?""")
    with (rx.matchEntire(this)?.groupValues) {
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
                    throw CliException("time $this must be in the past")
                }
            }
            return t1
        }
    }
    throw CliException("invalid time '$this' should be in form yyyy-mm-dd[Thh:mm]")
}

fun String.parseTime(): LocalTime {
    val rx = Regex("""(\d+):(\d+)""")
    with (rx.matchEntire(this)?.groupValues) {
        this?.let {
            return LocalTime.of(it[1].toInt(), it[2].toInt())
        }
    }
    throw CliException("invalid time '$this' should be in form hh:mm")
}

fun getHistoryTime(parser: Parser) =
    run {
        val now = LocalDateTime.now()
        val midnight = now.atMidnight()
        var month = -1
        var day = -1
        with (parser.findKeyword(KeywordList.days({ day = it.value })
            .add(KeywordList.months{ month = it.value })
            .addKeys("yesterday", "today", "last"), missOk=true)) {
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
                        val result = t.parseDateTime(pastOnly = true)
                        val t2 = parser.nextToken(tokenType = Parser.TokenType.ttNonBlank, endOk=true)
                        try {
                            t2?.let{
                                result.setTime(it.parseTime())
                            } ?: result
                        } catch (exc: Exception) {
                            parser.backup()
                            result
                        }
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
                this.asString()=="today" ->
                    midnight
                this.asString()=="last" ->
                    DateInterval.read(parser, 1)!!.subtractFrom(now)
                else ->
                    midnight       // impossible
            }
        }
    }





