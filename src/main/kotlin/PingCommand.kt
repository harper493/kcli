fun CliCommand.doPing() {
    val target = parser.nextToken(tokenType=Parser.TokenType.ttNonBlank)!!
    val values = readAttributes(CliMetadata.getClass("ping_request")!!,
        false)
    values["destination_host"] = target
    val pingUrl = ObjectName("ping_requests/")
    val result = Rest.post(pingUrl.url, values)
    val pingName = pingUrl.copy()
        .addLeafName(result["name"]?.asString()!!)
    val replyName = pingName.copy().append(
        CliMetadata.getAttribute("ping_request", "replies")!!
        , "")
    val pingObj = Rest.getObject(pingName.url,
        mapOf("select" to "destination_address,count"))
    CliException.throwIf("failed to create ping_request object"){ pingObj==null }
    val count = pingObj!!.getInt("count")!!
    val destAdr = pingObj["destination_address"]
    var replyCount = 0
    var nextSeq = 1
    val seen = mutableSetOf<Int>()
    Cli.clearInterrupted()
    while (!Cli.interrupted && (count==0 || replyCount < count)) {
        Thread.sleep(100)
        val replies = Rest.getCollection(replyName, mapOf(
            "with" to "sequence_number>=$nextSeq,state!=request_transmitted",
            "level" to "detail"
        ))
        replies.objects.values.forEach{ obj ->
            val seq = obj.getInt("sequence_number")!!
            if (seq !in seen) {
                seen.add(seq)
                val rtt = "%.2f".format(obj.getValue("round_trip_time")?.toFloat())
                val destInfo = "From ${values["destination_host"]} ($destAdr):"
                if (obj.getValue("state") == "reply_received") {
                    Cli.outputln("$destInfo sequence $seq RTT $rtt mS")
                } else {
                    Cli.outputln("$destInfo reply $seq not received")
                }
                replyCount += 1
                if (seq==nextSeq) {
                    nextSeq += 1
                }
            }
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
        "RTT ${
            listOf(
                "min",
                "max",
                "average"
            ).joinToString(", ") 
            { "$it %.0f".format(1000 * pingObj2.getFloat("rtt_$it")!!) }
        } mS"
    )
    Cli.clearInterrupted()
    seen.forEach {
        ignoreException{ Rest.delete(replyName.copy().addLeafName("$it").url) }
    }
    ignoreException { Rest.delete(pingName.url) }
}
