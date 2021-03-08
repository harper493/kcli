import java.io.File
// Date and time
// Timers functionality
// Tmux functionality

// jline-builtins
// jline-keymap
// jline-reader
import org.jline.reader.*
import org.jline.reader.impl.DefaultParser
// jline-terminal
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
// jline-utils

class CommandCompleter : Completer {
    override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
        try {
            CliCommand("${line.line()}${Parser.completerCh}")
        } catch (exc: CompletionException) {
            exc.completions.map{ candidates.add(Candidate(it))}
        }
    }
}

class CompletionException(val completions: List<String>): Exception("")

class CommandReader (val prompt: String) {
    val width = "tput cols".runCommand(File(".")) ?: "0".toInt()
    val foo = 0.also { println("width = $width") }
    val builder: TerminalBuilder = TerminalBuilder.builder()
    val terminal: Terminal = builder.build()
    //val completer = StringsCompleter("abc", "def", "deg", "ghi")
    val completer = CommandCompleter()

    val reader: LineReader = LineReaderBuilder.builder()
        .terminal(terminal)
        .completer(completer)
        .parser(DefaultParser())
        .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%M%P > ")
        .build()

    init{
        println("${terminal.getName()}: ${terminal.getType()}")
        println("\nhelp: list available commands")
    }

    fun read(): String {
        return reader.readLine(prompt)
    }
}





