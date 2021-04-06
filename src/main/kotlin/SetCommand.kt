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
        Rest.put("parameters", mapOf(param.name to value))
    }

    private fun setLogging() {
        val loggers = Rest.getCollection("loggers/", options=mapOf("link" to "name"))
        val logger = parser.findKeyword(KeywordList(*loggers.values.map{it["name"].value}
            .toTypedArray()))
        val level = parser.findKeyword(KeywordList("fatal", "error", "warn",
            "info", "debug", "trace"))!!.toString()
        parser.checkFinished()
        Rest.put("loggers/$logger", mapOf("level" to level))
    }

    private fun setPassword() {
        var admin: String? = null
        var password: String? = null
        var oldPassword = ""
        if (Cli.isSuperuser) {
            admin = parser.nextToken(completer=ObjectCompleter(ObjectName("administrators/")), endOk=true)
            if (admin!=null) {
                password = parser.nextToken(tokenType = Parser.TokenType.ttNonBlank, endOk=true)
            }
        }
        parser.checkFinished()
        if (password==null) {
            if (admin==null) {
                oldPassword = CommandReader.readPassword("Old Password? ")
            }
            password = Cli.getPassword()
        }
        val body = mutableMapOf("password" to password)
        if (oldPassword.isNotEmpty()) {
            body["old_password"] = oldPassword
        }
        admin = admin ?: Cli.username
        Rest.put("administrators/${admin}", body)
        if (admin==Cli.username) {
            Rest.setPassword(password)
        }
    }

    private fun setConfiguration() {
        val config = parser.nextToken(completer=ObjectCompleter(ObjectName("configurations/")))!!
        parser.checkFinished()
        Rest.setConfig(config)
    }

    private fun setTrace() {
        val onOff = parser.nextToken(type=Datatype["boolean"]) ?: "F"
        parser.checkFinished()
        Rest.setTrace(Datatype.toBoolean(onOff))
    }
}

