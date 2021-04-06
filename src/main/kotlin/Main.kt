import sun.misc.Signal
import java.io.PrintWriter
import java.time.Duration
import java.time.LocalTime
import kotlin.system.exitProcess

object Cli {
    val homeDir: String = System.getProperty("user.home")
    val kcliDir: String = "$homeDir/.kcli"
    private val targets = Properties()
    private lateinit var outFile: PrintWriter
    private lateinit var args: Args
    private lateinit var privilege: String
    private lateinit var target: ServerInfo
    private lateinit var systemName: String

    fun run(cmdargs: Array<String>) {
        args = Args(cmdargs)
        java.io.File(kcliDir).mkdirs()
        establishSignals()
        Datatype.load()
        target = findTarget(args.server)
        Rest.connect(server = target.toString(), trace=args.trace)
        Properties
            .load(ResourceCache.get("objects.properties")
                { Rest.getRaw("files/props/objects.properties") })
            .load(ResourceCache.get("attributes.properties")
                { Rest.getRaw("files/props/attributes.properties") })
            .load(ResourceCache.get("cli.properties", { defaultProperties }))
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
                HttpStatus.forbidden(exc.status) ->
                    outputError("incorrect username or password")
                else ->
                    outputError("error connecting to STM: ${exc.text}")
            }
            return
        }
        privilege = Rest.getAttribute("administrators/${target.username}", "privilege") ?: ""
        systemName = Rest.getAttribute("configurations/running", "system_name") ?: "stm"
        if (args.output.isBlank() && args.command.isEmpty()) {
            outputln(StyledText("User '${target.username}' logged on to '$systemName'" +
                    " (${target.server}) with privilege level $privilege",
                color=Properties.get("parameter", "login_color")).render())
            output(StyledText().render())
        }
        CommandReader.setPrompt("$systemName# ")
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
                error = if (HttpStatus.forbidden(exc.status)) "incorrect username or password"
                        else  exc.message ?: ""
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
        val kcliFile = "$kcliDir/targets"
        var result: String?
        ignoreException{ targets.loadFile(kcliFile) }
        if ((tname).containsAnyOf(":.@")) {
            result = tname
            targets.addValue(tname, listOf("target", "_last"))
            targets.write(kcliFile)
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
        val isSuperuser get() = privilege=="superuser"
        val isConfig get() = privilege=="config"
        val username get() = target.username
        fun getPassword(): String {
            val password = CommandReader.readPassword("New Password? ")
            val repeatPassword = CommandReader.readPassword("Repeat Password? ")
            CliException.throwIf("passwords do not match") { password != repeatPassword }
            return password
        }
        private var lastInterrupt: LocalTime? = null
        var interrupted = false; private set
        private fun establishSignals() {
            Signal.handle(Signal("INT")) { doInterrupt() }
        }
        private fun doInterrupt() {
            if (lastInterrupt != null
                && Duration.between(LocalTime.now(), lastInterrupt).toSeconds() < 1.0
            ) {
                exitProcess(0)
            } else {
                interrupted = true
                lastInterrupt = LocalTime.now()
            }
        }
        fun clearInterrupted() { interrupted = false }
    }



fun main(args: Array<String>) {
    Cli.run(args)
}

fun test2() {
    val words = listOf(
        "entry",
        "entry",
        "soliloquy",
        "wife",
        "hovercraft",
        "gentleman",
        "hoof",
        "bus",
        "witch",
        "cat",
        "fungus",
        "wish",
        "catch",
        "box",
    )
    words.forEach { println("$it => ${it.makePlural()}") }
    println(pluralCache)
    words.map{ it.makePlural() }.forEach { println("$it => ${it.makeSingular()}") }
    println(singularCache)
    val words2 = listOf(
        "cat",
        "elephant",
        "uniform",
        "ewe",
        "utility",
        "hour",
        "honorable",
        "unusual",
        "elephant",
    )
    words2.forEach{ println("${it.indefiniteArticle()} $it")}
    println(articleCache)
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