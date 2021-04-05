fun CliCommand.doTraceroute() {
    val target = parser.nextToken(tokenType=Parser.TokenType.ttNonBlank)!!
    val values = readAttributes(Metadata.getClass("traceroute")!!,
        false)
    values["destination_host"] = target
    val tracerouteUrl = ObjectName("traceroutes/")
    val result = Rest.post(tracerouteUrl.url, values)
    val tracerouteName = tracerouteUrl.copy()
        .addLeafName(result["name"]?.asString()!!)
    val replyName = tracerouteName.copy().append(
        Metadata.getAttribute("traceroute", "replies")!!
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


/*
        while True :
            try :
                hop1_timeout = timedelta(0, 3)
                start_time = datetime.now()
                while (not done) and (hop_count < max_hops) :
                    time.sleep(0.1)
                    replies = self.api.get_collection(reply_name,
                                                      with_='hop_count>=%d,state!=probes_transmitted' %
                                                      (hop_count,),
                                                      level='detail')
                    for n,reply in replies :
                        while hop_count < int(reply['hop_count']) :
                            output("%2d * * *" % (hop_count))
                            hop_count += 1
                        if reply.convert_value('state')=='timed_out' :
                            text = "%2d * * *" % (hop_count)
                        else :
                            text = "%2d " % (hop_count)
                            sa = reply['sender_address_1']
                            if sa=="::" :
                                text += "* "
                            else :
                                text += "%s (%s) %.3f ms " % (reply['sender_name_1'], sa,
                                                             1000*self.duration_to_s(reply['round_trip_time_1']))
                            prev = sa
                            sa = reply['sender_address_2']
                            if sa=="::" :
                                text += "* "
                            elif sa==prev :
                                text += "%.3f ms " % (1000*self.duration_to_s(reply['round_trip_time_2']))

                            else :
                                text += "%s (%s) %.3f ms " % (reply['sender_name_2'], sa,
                                                             1000*self.duration_to_s(reply['round_trip_time_2']))
                            prev = sa
                            sa = reply['sender_address_3']
                            if sa=="::" :
                                text += "* "
                            elif sa==prev :
                                text += "%.3f ms" % (1000*self.duration_to_s(reply['round_trip_time_3']))
                            else :
                                text += "%s (%s) %.3f ms" % (reply['sender_name_3'], sa,
                                                             1000*self.duration_to_s(reply['round_trip_time_2']))
                        output(text)
                        if reply.convert_value('state')=='destination_unreach_received' :
                            done = True
                        hop_count += 1
                    if (hop_count == 1) and (len(replies) == 0) :
                        if (datetime.now() - start_time) > hop1_timeout :
                            error_output("%s: Destination Host Unreachable" % args['destination_host'])
                            done = True
                done = True
            except KeyboardInterrupt :
                done = True
            if done :
                break
        self.api.rest_delete(traceroute_name)

 */