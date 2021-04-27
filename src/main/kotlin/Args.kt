
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default

class Args() {
    lateinit var command: String; private set
    lateinit var server: String; private set

    val trace by parser.flagging("-t", "--trace", help = "enable Test tracing")
    private val serverOpt by parser.storing(
        "-s", "--server",
        help = "specify server as username:password@address"
    ).default("")
    val output: String by parser.storing("-o", "--output", help = "specify output file")
        .default("")
    val color by parser.flagging("-c", "--color", help = "force color in output")
    val noColor by parser.flagging("-C", "--nocolor", help = "disable color in output")
    val remote by parser.flagging("-r", "--remote", help = "run remote with SSH")
    private val commandOpt by parser.positionalList(help = "command to execute", 0..Int.MAX_VALUE)

    private fun parse (): Args {
        parser.force()
        if (serverOpt.isEmpty()
            && commandOpt.isNotEmpty()
            && commandOpt.first().containsAnyOf(":.@")) {
            server = commandOpt.first()
            command = commandOpt.drop(1).joinToString(" ")
        } else {
            server = serverOpt
            command = commandOpt.joinToString(" ")
        }
        return this
    }
    companion object {
        lateinit var parser: ArgParser
        lateinit var theArgs: Args
        val remote get() = theArgs.remote
        fun parse(cmdargs: Array<String>): Args {
            parser = ArgParser(cmdargs)
            theArgs = Args()
            return theArgs.parse()
        }
    }
}
