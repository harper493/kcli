class SetCommand(val cli: CliCommand) {
    val commands = KeywordList(
        KeywordFn("parameters", { setParameters() }),
        KeywordFn("logging", { setLogging() }),
        KeywordFn("password", { setPassword() }),
        KeywordFn("configuration", { setConfiguration() }),
        KeywordFn("trace", { setTrace() }),
    )
    private val parser get() = cli.parser

    fun doSet() = parser.findKeyword(commands)?.invoke()

    private fun setParameters() {
        val param = parser.findKeyword(
            KeywordList(
                Metadata.getClass("parameter_info")!!.attributes))!!.attribute!!
        val value = parser.nextToken(attribute=param)!!
        parser.checkFinished()
        Rest.put("parameters", mapOf(param.name to value).toJson())
    }

    private fun setLogging() {
        val loggers = Rest.getCollection("loggers/", options=mapOf("link" to "name"))
        val logger = parser.findKeyword(KeywordList(*loggers.values.map{it["name"].value}
            .toTypedArray()))
        val level = parser.findKeyword(KeywordList("fatal", "error", "warn",
            "info", "debug", "trace"))!!.toString()
        parser.checkFinished()
        Rest.put("loggers/$logger", mapOf("level" to level).toJson())
    }

    private fun setPassword() {

    }

    private fun setConfiguration() {
        val config = parser.nextToken(completer=ObjectCompleter(ObjectName("configurations/")))!!
        Rest.setConfig(config)
    }

    private fun setTrace() {
        val type = Datatype["boolean"]
        Rest.setTrace(Datatype.toBoolean(parser.nextToken(type=type) ?: "F"))
    }
}


/*

#
# do
# do_set_password - change own password or (if superuser) another user's
#
    def do_set_password(self, reader) :
        username = None
        if self.is_superuser() :
            k = self.reader.next_token(completer=collection_completer(self.get_administrators_name()), endOK=True)
            if k :
                password = get_password()
                username = k
                args = { 'password' : password }
        if username is None :
            old_password = getpass("Enter old password: ")
            password = get_password()
            args = { 'password' : password, 'old_password' : old_password }
            username = self.user
        self.api.rest_put(self.get_administrators_name(username), args)

#
# do_set_configuration - change the configuration being edited, and create
# a new one if the named config doesn't exist
#
    def do_set_configuration(self, reader) :
        commands = {}
        name = self._get_config_name(reader)
        conf_md = self.api.get_attribute_metadata('policy_manager', 'configurations')
        my_object = object_name(conf_md, name)
        conf = self.api.rest_get_object(my_object)
        if conf is None :    # if this named config does not exist
            commands['copy'] = 'copy'
        attrs = {}
        while not reader.is_finished() :
            k = reader.next_keyword(commands, help_context='set_configuration')
            if k=='copy' :
                attrs['copy'] = self._get_config_name(reader, help_context='set_configuration')
        if conf is None :
            if yesno_p("configuration '%s' does not exist, create" % (name,)) :
                self.api.rest_put(my_object, attrs)
                conf = self.api.rest_get_object(my_object)
        if conf :
            self.config_name = name
            self.mode.set_config(name)
            self.api.rest.set_config(name)
            if name=='running' :
                self.mode.set_prompt(self.system_name + BASE_PROMPT)
            else :
                self.mode.set_prompt(self.system_name + '-(configuration-' + name + ')' + BASE_PROMPT)
#
# do_set_trace - hidden command to turn Rest tracing on and off
#
    def do_set_trace(self, reader) :
        onoff = { p:p for p in [ 'on', 'off' ]}
        if reader.is_finished() :
            state = True
        else :
            state = reader.next_keyword(onoff) == 'on'
        reader.check_finished()
        self.api.rest.set_trace(state)



 */