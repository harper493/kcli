fun CliCommand.doTraceroute() {
    val target = parser.nextToken(tokenType=Parser.TokenType.ttNonBlank)!!
    val values = readAttributes(CliMetadata.getClass("traceroute")!!,
        false)
    values["destination_host"] = target
    val tracerouteUrl = ObjectName("traceroutes/")
    val result = Rest.post(tracerouteUrl.url, values)
    val tracerouteName = tracerouteUrl.copy()
        .addLeafName(result["name"]?.asString()!!)
    val replyName = tracerouteName.copy().append(
        CliMetadata.getAttribute("traceroute", "replies")!!
        , "")
    val tracerouteObj = Rest.getObject(tracerouteName.url,
        mapOf("select" to "destination_address,max_hops"))
    CliException.throwIf("failed to create traceroute_request object"){ tracerouteObj==null }
    val destAdr = tracerouteObj?.getValue("destination_address")!!
    val maxHops = tracerouteObj?.getInt("max_hops")!!
    Cli.outputln("traceroute to $target ($destAdr)")
    var hopCount = 0
    var done = false
    Cli.clearInterrupted()
    while (!Cli.interrupted && !done && hopCount < maxHops) {
        Thread.sleep(100)
        val replies = Rest.getCollection(replyName, mapOf(
            "with" to "hop_count>$hopCount,state!=probes_transmitted",
            "level" to "detail"
        ))
        replies.objects.values.forEach{ obj ->
            val hops = obj.getInt("hop_count")!!
            val state = obj.getValue("state")!!
            hopCount += 1
            while (hopCount < hops) {
                Cli.outputln("%2d   * * *".format(hopCount))
                hopCount += 1
            }
            var text = "%2d  ".format(hopCount)
            when {
                hops < hopCount ->
                    text += " * * *"
                state == "timed_out" ->
                    text += " * * *"
                state == "destination_unreach_received" -> {
                    text += " * * *"
                    done = true
                }
                else -> {
                    var prevSenderAdr = ""
                    (1..3).forEach { tryNo ->
                        val senderAdr = obj.getValue("sender_address_$tryNo")!!
                        val senderName = obj.getValue("sender_name_$tryNo")!!
                        val rtt = "%.0f".format((obj.getFloat("round_trip_time_$tryNo")?:0.0)*1000.0)
                        done = done || (senderAdr == destAdr)
                        when {
                            senderAdr == "::" -> text += " * "
                            senderAdr == prevSenderAdr -> text += " $rtt ms "
                            senderAdr == senderName -> {
                                prevSenderAdr = senderAdr
                                text += "$senderName  $rtt ms "
                            }
                            else -> {
                                prevSenderAdr = senderAdr
                                text += "$senderName ($senderAdr)  $rtt ms "
                            }
                        }
                    }
                }
            }
            Cli.outputln(text)
        }
    }
    Cli.clearInterrupted()
    (1..hopCount).forEach{ ignoreException { Rest.delete(replyName.copy().addLeafName("$it").url) } }
    ignoreException{ Rest.delete(tracerouteName.url) }
}
