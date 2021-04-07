
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default

class Args (
    private val cmdargs: Array<String>
    ) {
    private val parser = ArgParser(cmdargs)
    val trace by parser.flagging("-t", "--trace", help = "enable Test tracing")
    private val serverOpt by parser.storing(
        "-s", "--server",
        help = "specify server as username:password@address"
    ).default("")
    val output by parser.storing("-o", "--output", help = "specify output file")
        .default("")
    val color by parser.flagging("-c", "--color", help = "force color in output")
    val noColor by parser.flagging("-C", "--nocolor", help = "disable color in output")
    private val commandOpt by parser.positionalList(help = "command to execute", 0..Int.MAX_VALUE)
    var command: String; private set
    var server: String; private set

    init {
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
    }
}
