import java.time.LocalTime
import java.time.Duration

fun CliCommand.doPing() {
    val target = parser.nextToken(tokenType=Parser.TokenType.ttNonBlank)!!
    val values = readAttributes(Metadata.getClass("ping_request")!!,
        false)
    values["destination_host"] = target
    val pingUrl = ObjectName("ping_requests/")
    val result = Rest.post(pingUrl.url, values)
    val pingName = pingUrl.copy()
        .addLeafName(result["name"]?.asString()!!)
    val replyName = pingName.copy().append(
        Metadata.getAttribute("ping_request", "replies")!!
        , "")
    val pingObj = Rest.getObject(pingName.url,
        mapOf("select" to "destination_address,count"))
    CliException.throwIf("failed to create ping_request object"){ pingObj==null }
    val count = pingObj!!.getInt("count")!!
    val destAdr = pingObj["destination_address"]
    var replyCount = 0
    var lastSeq = 0
    val timeout = Properties.getInt("parameter", "ping_timeout")
    var startTime = LocalTime.now()
    while (!Cli.interrupted && (count==0 || replyCount < count)) {
        Thread.sleep(100)
        val replies = Rest.getCollection(replyName, mapOf(
            "with" to "sequence_number>$lastSeq,state!=request_transmitted",
            "level" to "detail"
        ))
        if (replies.isEmpty()) {
            if (Duration.between(LocalTime.now(), startTime).toSeconds() > timeout) {
                Cli.outputError("destination host unreachable")
                break
            }
        } else {
            replies.objects.values.forEach{ obj ->
                val seq = obj.getInt("sequence_number")!!
                val rtt = "%.2f".format(obj.getValue("round_trip_time")?.toFloat())
                val destInfo = "From ${values["destination_host"]} ($destAdr):"
                if (obj.getValue("state")=="reply_received") {
                    Cli.outputln("$destInfo icmp_seq $seq rtt $rtt")
                } else {
                    Cli.outputln("$destInfo reply not received")
                }
                replyCount += 1
                lastSeq = maxOf(seq, lastSeq)
            }
            startTime = LocalTime.now()
        }
    }
    val pingObj2 = Rest.getObject(pingName.url, mapOf("level" to "full"))
    CliException.throwIf("failed to retreive ping_request object"){ pingObj2==null }
    val sent = pingObj2!!.getInt("requests_sent") ?: 1
    val received = pingObj2.getInt("replies_received") ?: 0
    Cli.outputln(
        "%d packets transmitted, %d received, %.1f%% packet loss".format(
            sent,
            received,
            100 * (1.0 - received.toFloat() / sent.toFloat())
        )
    )
    Cli.outputln(
        "RTT ${listOf("min", "max", "average", "stddev")
            .map{ "$it %.0f".format(1000 * pingObj2.getFloat("rtt_$it")!!) }
            .joinToString(", ")} mS"
    )
}
