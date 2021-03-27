import java.io.PrintWriter
import java.net.InetAddress

class Cli(private val cmdargs: Array<String>) {
    private val homeDir: String = System.getProperty("user.home")
    private val targets = Properties("${homeDir}/.kcli")
    private lateinit var outFile: PrintWriter
    private lateinit var args: Args; private set

    init {
        theCli = this
    }

    fun run() {
        Datatype.load()
        Properties.load("/etc/kcli/objects.properties")
            .load("/etc/kcli/cli.properties")
        args = Args(cmdargs)
        val target = findTarget(args.server)
        Rest.connect(server = target.toString(), trace=args.trace)
        if (args.output.isBlank()) {
            StyledText.setRenderer("ISO6429")
        } else {
            StyledText.setRenderer("plain")
            outFile = PrintWriter(args.output)
        }
        try {
            Metadata.load()
        } catch (exc: RestException) {
            when {
                HttpStatus.timeout(exc.status) ->
                    outputError("failed to connect to STM - connection timeout")
                HttpStatus.unauthorized(exc.status) ->
                    outputError("incorrect username or password")
                else ->
                    outputError("error connecting to STM: ${exc.text}")
            }
            return
        }
        CommandReader.setPrompt("${Rest.getSystemName() ?: "stm"}# ")
        while (true) {
            var error = ""
            try {
                if (args.command.isNotEmpty()) {
                    CliCommand(args.command)
                } else {
                    CliCommand(CommandReader.read())
                }
            } catch (exc: CliException) {
                if (exc.message ?: "" != "") {
                    error = exc.message ?: ""
                } else {
                    break
                }
            } catch (exc: RestException) {
                error = exc.message ?: ""
            }
            if (error.isNotEmpty()) {
                outputError(error)
            }
            output(StyledText().render())
            if (args.command.isNotEmpty()) break
        }
        if (args.output.isNotBlank()) {
            outFile.close()
        }
    }

    private fun findTarget(tname: String): ServerInfo {
        var result: String?
        targets.load()
        if ((tname).containsAnyOf(":.@")) {
            result = tname
            targets.addValue(tname, listOf("target", "_last"))
            targets.write()
        } else {
            result = targets.get("target", tname)
            if (result == null) {
                result = targets.get("target", "_last")
            }
        }
        val si = ServerInfo(result?:"")
        if (si.server.isEmpty()) {
            si.server = getUserInput("Server name or address? ")
        }
        if (si.username.isEmpty()) {
            si.username = getUserInput("Username? ")
        }
        if (si.password.isEmpty()) {
            si.password = getUserInput("Password? ")
        }
        return si
    }

    fun outputln(text: String) {
        output(text + "\n")
    }

    fun output(text: String) {
        if (args.output.isBlank()) {
            print(text)
        } else {
            outFile.append(text)
        }
    }

    private fun getSystemName(): String? =
        try {
            InetAddress.getLocalHost().hostName
        } catch (E: Exception) {
            null
        }

    companion object {
        lateinit var theCli: Cli
        fun outputln(text: String) = theCli.outputln(text)
        fun output(text: String) = theCli.output(text)
        fun outputError(text: String) {
            outputln(
                StyledText(
                    text.uppercaseFirst(),
                    color = Properties.get("color", "error")
                )
                    .render()
            )
            output(StyledText("").render())
        }
    }
}


fun main(args: Array<String>) {
    Cli(args).run()
}


fun test() {
/*
    val x4 = listOf(1,2,3,10,3,2,1).runningReduceLimit(3)
    val x2 = listOf(1,2,3,10,3,2,1).runningReduceLimit(17)
    val x0 = listOf(1,2,3,10,3,2,1).runningReduceLimit(8)
    val x1 = listOf(1,1,2,3,10,3,2,1).runningReduceLimit(2)
    val w0 = listOf(1,3,5,7,9).windowed(2)
    val a0 = listOf(1,3,5,7,9).makeAscending()
    val a1 = listOf(1,3,3,5,0,9).makeAscending()
    val a2 = listOf<Int>().makeAscending()
    val a3 = listOf(1).makeAscending()
    val a4 = listOf(1,1).makeAscending()
    val a5 = listOf(10,6,8).makeAscending()
    val s1 = "abcdefghijklmnopqrstuvwyxz"
    val p1 = s1.splitAt(listOf(3,6,10,26,10))
    val p2 = s1.splitAt(listOf())
    val p3 = s1.splitAt(listOf(1))
    val p4 = s1.splitAt(listOf(0,3,3))
    val q0 = wrap("abcdefg-hijkl", 8, true)
*/
}