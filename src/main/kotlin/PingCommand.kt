fun CliCommand.doPing() {
    val target = parser.nextToken(validator=Datatype["ipv4_address"].validator)!!
    val values = readAttributes(Metadata.getClass("ping_request")!!, false)
    values["destination_host"] = target
    val pingName = ObjectName("ping_requests/")
    val result = Rest.post(pingName.url, values)
    val replyName = pingName.copy()
        .addLeafName(result["name"]?.asString()!!)
        .append(Metadata.getAttribute("ping_request", "replies")!!, "")
}

/*
# do_ping - implement ping command
#
    def do_ping(self, reader) :
        commands = { 'interface' : 'interface',
                     'next_hop' : 'next_hop',
                     'source_address' : 'source_address',
                     'count' : 'count'
                 }
        target = interface = next_hop = source_address = count = None
        while not reader.is_finished() :
            k = reader.next_keyword(commands, help_context='ping_request', endOK=True, missOK=True)
            if k is None :
                target = reader.next_token()
            elif k=='interface' :
                interface = reader.next_token(help_text='interface to send ping request on')
            elif k=='next_hop' :
                next_hop = reader.next_token(help_text='next hop IP address to use')
            elif k=='source_address' :
                source_address = reader.next_token(help_text='source IP address to use')
            elif k=='count' :
                count = int(reader.next_token(help_text='number of requests to send, zero means send forever'))
                if count==0 :
                    count = 999999
            else :              # should never happen
                pass
        if target is None :
            raise SyntaxException, "target [interface:]host must be specified"
        target_interface = None
        if '#' in target :
            target_interface, host = target.split('#', 1)
        elif ':' in target :
            target_interface, host = target.split(':', 1)
        else :
            host = target
        if interface is None and target_interface is not None :
            interface = target_interface
        args = { 'destination_host' : host }
        if interface is not None :
            args['interface'] = interface
        if next_hop is not None :
            args['next_hop'] = next_hop
        if source_address is not None :
            args['source_address'] = source_address
        if count is not None :
            args['count'] = count
        ping_name = object_name('ping_requests')
        j = self.api.rest_post(ping_name, args)
        ping_name.add_leaf_name(j['name'])
        reply_name = ping_name.copy().append('replies')
        count = int(self.api.get_attribute_value(ping_name, 'count'))
        dest_address = self.api.get_attribute_value(ping_name, 'destination_address')
        reply_count = 0
        last_seq = 0
        done = False
        while True :
            try :
                req_timeout = timedelta(0, 1)
                n_retries = 0
                start_time = datetime.now()
                while count==0 or reply_count < count :
                    time.sleep(0.1)
                    replies = self.api.get_collection(reply_name,
                                                      with_='sequence_number>%d,state!=request_transmitted' %
                                                      (last_seq,),
                                                      level='detail')
                    for n,reply in replies :
                        seq = int(reply['sequence_number'])
                        if reply.convert_value('state')=='reply_received' :
                            text = "From %s (%s): icmp_seq %d rtt %.2f mS" % (host, dest_address, seq,
                                                                              float(reply['round_trip_time']))
                        else :
                            text = "From %s (%s): reply not received" % (host, dest_address)
                        output(text)
                        reply_count += 1
                        last_seq = max(last_seq, seq)
                    if len(replies) == 0 :
                        now = datetime.now()
                        if now - start_time > req_timeout :
                            error_output('Destination Host Unreachable')
                            n_retries += 1
                            if n_retries > 5 :
                                self.api.rest_delete(ping_name)
                                return
                            else :
                                start_time = now
                    else :
                        n_retries = 0
                        start_time = datetime.now()
                done = True
            except KeyboardInterrupt :
                done = True
            if done :
                break
        ping_obj = self.api.get_object(ping_name, 'packet_stats', level='full')
        req = ping_obj.convert_value('requests_sent')
        rep = ping_obj.convert_value('replies_received')
        if req != 0 :
            output(('%d packets transmitted, %d received, %d%% packet loss\n' + \
                   'rtt min/max/avg/mdev = %.3f / %.3f / %.3f / %.3f ms') % \
                (req,
                 rep,
                 100 * (1 - (float(rep)/float(req))),
                 1000*self.duration_to_s(ping_obj['rtt_min']),
                 1000*self.duration_to_s(ping_obj['rtt_max']),
                 1000*self.duration_to_s(ping_obj['rtt_average']),
                 1000*self.duration_to_s(ping_obj['rtt_stddev'])))
        self.api.rest_delete(ping_name)
#

 */